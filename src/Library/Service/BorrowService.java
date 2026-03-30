package Library.Service;

import Library.Exception.BusinessException;
import Library.Exception.NotFoundException;
import Library.Model.Book;
import Library.Model.BorrowRecord;
import Library.Repository.BookRepository;
import Library.Repository.BorrowRepository;
import Library.Security.SecurityConfig;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Service class to handle borrowing-related operations such as borrowing and returning books, as well as listing active borrows for a user.
public class BorrowService {
    public enum BorrowReminderLevel {
        DUE_SOON,
        OVERDUE
    }

    public record BorrowReminderCandidate(String borrowRecordId,
                                          String bookId,
                                          String bookTitle,
                                          LocalDate dueDate,
                                          BorrowReminderLevel level,
                                          int daysUntilDue) {
    }

    private static final int MAX_BORROW_LIMIT = SecurityConfig.MAX_BORROW_LIMIT;
    private static final int DEFAULT_BORROW_DAYS = SecurityConfig.DEFAULT_BORROW_DAYS;
    private static final int MAX_BORROW_DAYS = SecurityConfig.MAX_BORROW_DAYS;

    private final BookRepository bookRepository;
    private final BorrowRepository borrowRepository;

    // Constructor to initialize the BorrowService with the required BookRepository and BorrowRepository, allowing for dependency injection and better separation of concerns.
    public BorrowService(BookRepository bookRepository, BorrowRepository borrowRepository) {
        this.bookRepository = bookRepository;
        this.borrowRepository = borrowRepository;
    }

    // Method to borrow a book for a user, with an optional parameter for the number of days to borrow, validating the book's availability and the user's borrowing limits before creating a borrow record and updating the book's availability status.
    public BorrowRecord borrowBook(String username, String bookId) {
        return borrowBook(username, bookId, DEFAULT_BORROW_DAYS);
    }

    // Overloaded method to borrow a book with a specified number of days, allowing for more flexible borrowing durations while still enforcing validation rules for book availability and user borrowing limits.
    public BorrowRecord borrowBook(String username, String bookId, int borrowDays) {
        autoReturnOverdueBooks(username);
        validateBorrowDuration(borrowDays);
        long activeBorrows = countActiveBorrows(username);
        if (activeBorrows + 1 > MAX_BORROW_LIMIT) {
            throw new BusinessException("Borrow limit reached. Max allowed is " + MAX_BORROW_LIMIT + ".");
        }
        Book book = requireBorrowableBook(bookId);

        // Create a new borrow record with the current date as the borrow date and calculate the due date based on the specified number of borrow days, then save the record and update the book's availability status to false.
        LocalDate now = LocalDate.now();
        LocalDate due = now.plusDays(borrowDays);
        BorrowRecord record = new BorrowRecord(username, bookId, now, due);
        borrowRepository.save(record);

        book.setAvailable(false);
        return record;
    }

    public List<BorrowRecord> borrowBooks(String username, List<String> bookIds, int borrowDays) {
        autoReturnOverdueBooks(username);
        validateBorrowDuration(borrowDays);

        List<String> normalizedBookIds = normalizeBookIds(bookIds);
        if (normalizedBookIds.isEmpty()) {
            throw new BusinessException("At least one book must be selected for bulk borrow.");
        }

        long activeBorrows = countActiveBorrows(username);
        if (activeBorrows + normalizedBookIds.size() > MAX_BORROW_LIMIT) {
            throw new BusinessException("Borrow limit reached. Max allowed is " + MAX_BORROW_LIMIT + ".");
        }

        // Validate the full selection first so bulk borrow is all-or-nothing.
        List<Book> booksToBorrow = new ArrayList<>();
        Set<String> uniqueBookIds = new HashSet<>();
        for (String bookId : normalizedBookIds) {
            if (!uniqueBookIds.add(bookId)) {
                throw new BusinessException("Duplicate book selection is not allowed.");
            }
            booksToBorrow.add(requireBorrowableBook(bookId));
        }

        LocalDate now = LocalDate.now();
        LocalDate due = now.plusDays(borrowDays);
        List<BorrowRecord> records = new ArrayList<>();
        for (Book book : booksToBorrow) {
            BorrowRecord record = new BorrowRecord(username, book.getId(), now, due);
            borrowRepository.save(record);
            book.setAvailable(false);
            records.add(record);
        }
        return records;
    }

    // Method to return a borrowed book for a user, validating the existence of an active borrow record for the specified user and book, marking the record as returned, and updating the book's availability status to true.
    public BorrowRecord returnBook(String username, String bookId) {
        autoReturnOverdueBooks(username);

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found."));

        BorrowRecord record = requireActiveBorrow(username, bookId);

        record.markReturned();
        book.setAvailable(true);
        return record;
    }

    // Method to list all active borrows for a specific user, filtering the borrow records to include only those that have not been marked as returned and returning the list of active borrow records.
    public List<BorrowRecord> listActiveBorrowsByUser(String username) {
        autoReturnOverdueBooks(username);
        return borrowRepository.findByUsername(username).stream()
                .filter(r -> !r.isReturned())
                .collect(Collectors.toList());
    }

        public List<BorrowRecord> listBorrowsByUser(String username) {
        autoReturnOverdueBooks(username);
        return borrowRepository.findByUsername(username).stream()
            .sorted(
                Comparator.comparing(BorrowRecord::getBorrowDate, Comparator.reverseOrder())
                    .thenComparing(BorrowRecord::getId)
            )
            .collect(Collectors.toList());
        }

    public int autoReturnOverdueBooks(String username) {
        LocalDate today = LocalDate.now();
        List<BorrowRecord> candidates = borrowRepository.findByUsername(username);
        List<BorrowRecord> overdue = new ArrayList<>();
        for (BorrowRecord record : candidates) {
            if (record.isOverdue(today)) {
                overdue.add(record);
            }
        }

        for (BorrowRecord record : overdue) {
            record.markReturned(today, true);
            bookRepository.findById(record.getBookId()).ifPresent(book -> book.setAvailable(true));
        }
        return overdue.size();
    }

    public BorrowRecord requireActiveBorrow(String username, String bookId) {
        autoReturnOverdueBooks(username);
        return borrowRepository.findActiveByUsernameAndBookId(username, bookId)
                .orElseThrow(() -> new BusinessException("Book is not currently borrowed by this user."));
    }

    public List<BorrowRecord> listAllBorrowRecords() {
        // This reporting method is intentionally side-effect-free because it powers
        // read/list screens (for example, librarian borrowed-records view).
        // Overdue auto-return must only run in explicit workflow operations
        // (borrow/return/active-borrow checks), not in list/read APIs.
        return borrowRepository.findAll().stream()
                .sorted(
                        Comparator.comparing(BorrowRecord::getBorrowDate, Comparator.reverseOrder())
                                .thenComparing(BorrowRecord::getId)
                )
                .collect(Collectors.toList());
    }

    public List<BorrowRecord> listBorrowRecordsByUser(String username,
                                                      String status,
                                                      LocalDate borrowDateFrom,
                                                      LocalDate borrowDateTo,
                                                      LocalDate dueDateFrom,
                                                      LocalDate dueDateTo,
                                                      String sortBy,
                                                      String sortDir) {
        LocalDate today = LocalDate.now();
        String normalizedStatus = status == null ? "active" : status.trim().toLowerCase();
        String normalizedSortBy = sortBy == null ? "" : sortBy.trim().toLowerCase();
        String normalizedSortDir = sortDir == null ? "asc" : sortDir.trim().toLowerCase();

        List<BorrowRecord> filtered = borrowRepository.findByUsername(username).stream()
                .filter(record -> matchesStatus(record, normalizedStatus, today))
                .filter(record -> borrowDateFrom == null || !record.getBorrowDate().isBefore(borrowDateFrom))
                .filter(record -> borrowDateTo == null || !record.getBorrowDate().isAfter(borrowDateTo))
                .filter(record -> dueDateFrom == null || !record.getDueDate().isBefore(dueDateFrom))
                .filter(record -> dueDateTo == null || !record.getDueDate().isAfter(dueDateTo))
                .collect(Collectors.toList());

        Comparator<BorrowRecord> comparator = null;
        if ("borrowdate".equals(normalizedSortBy)) {
            comparator = Comparator.comparing(BorrowRecord::getBorrowDate).thenComparing(BorrowRecord::getId);
        } else if ("duedate".equals(normalizedSortBy)) {
            comparator = Comparator.comparing(BorrowRecord::getDueDate).thenComparing(BorrowRecord::getId);
        }

        if (comparator == null) {
            return filtered;
        }

        if ("desc".equals(normalizedSortDir)) {
            comparator = comparator.reversed();
        }

        return filtered.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    public List<BorrowReminderCandidate> findReturnReminderCandidates(String username) {
        return findReturnReminderCandidates(
                username,
                LocalDate.now(),
                SecurityConfig.returnReminderDueSoonDays()
        );
    }

    public List<BorrowReminderCandidate> findReturnReminderCandidates(String username,
                                                                      LocalDate today,
                                                                      int dueSoonThresholdDays) {
        int effectiveThreshold = Math.max(0, dueSoonThresholdDays);
        List<BorrowReminderCandidate> reminders = new ArrayList<>();
        for (BorrowRecord record : borrowRepository.findByUsername(username)) {
            if (record.isReturned()) {
                continue;
            }

            BorrowReminderLevel level = determineReminderLevel(record, today, effectiveThreshold);
            if (level == null) {
                continue;
            }

            String bookTitle = bookRepository.findById(record.getBookId())
                    .map(Book::getTitle)
                    .orElse(record.getBookId());
            int daysUntilDue = (int) (record.getDueDate().toEpochDay() - today.toEpochDay());
            reminders.add(new BorrowReminderCandidate(
                    record.getId(),
                    record.getBookId(),
                    bookTitle,
                    record.getDueDate(),
                    level,
                    daysUntilDue
            ));
        }

        reminders.sort(
                Comparator.comparing(BorrowReminderCandidate::dueDate)
                        .thenComparing(BorrowReminderCandidate::borrowRecordId)
        );
        return reminders;
    }

    private Book requireBorrowableBook(String bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found."));

        if (!book.isApproved()) {
            throw new BusinessException("Book is not approved yet.");
        }
        if (!book.isAvailable()) {
            throw new BusinessException("Book is currently unavailable.");
        }
        return book;
    }

    private static void validateBorrowDuration(int borrowDays) {
        if (borrowDays <= 0 || borrowDays > MAX_BORROW_DAYS) {
            throw new BusinessException("Borrow duration must be between 1 and " + MAX_BORROW_DAYS + " days.");
        }
    }

    private long countActiveBorrows(String username) {
        return borrowRepository.findByUsername(username).stream()
                .filter(r -> !r.isReturned())
                .count();
    }

    private static List<String> normalizeBookIds(List<String> bookIds) {
        List<String> normalized = new ArrayList<>();
        if (bookIds == null) {
            return normalized;
        }

        for (String bookId : bookIds) {
            if (bookId == null) {
                continue;
            }
            String trimmed = bookId.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private static boolean matchesStatus(BorrowRecord record, String status, LocalDate today) {
        return switch (status) {
            case "all" -> true;
            case "returned" -> record.isReturned();
            case "overdue" -> record.isOverdue(today);
            case "active", "" -> !record.isReturned();
            default -> false;
        };
    }

    private static BorrowReminderLevel determineReminderLevel(BorrowRecord record,
                                                              LocalDate today,
                                                              int dueSoonThresholdDays) {
        if (record.isOverdue(today)) {
            return BorrowReminderLevel.OVERDUE;
        }

        if (record.getDueDate().isBefore(today)) {
            return null;
        }

        LocalDate thresholdDate = today.plusDays(dueSoonThresholdDays);
        if (!record.getDueDate().isAfter(thresholdDate)) {
            return BorrowReminderLevel.DUE_SOON;
        }

        return null;
    }
}
