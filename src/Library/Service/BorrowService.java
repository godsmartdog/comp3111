package Library.Service;

import Library.Exception.BusinessException;
import Library.Exception.NotFoundException;
import Library.Model.Book;
import Library.Model.BorrowRecord;
import Library.Model.NotificationPriority;
import Library.Repository.BookRepository;
import Library.Repository.BorrowRepository;
import Library.Security.SecurityConfig;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ReadingProgressService readingProgressService;
    private NotificationService notificationService;

    // Constructor to initialize the BorrowService with the required BookRepository and BorrowRepository, allowing for dependency injection and better separation of concerns.
    public BorrowService(BookRepository bookRepository, BorrowRepository borrowRepository) {
        this(bookRepository, borrowRepository, null, null);
    }

    public BorrowService(BookRepository bookRepository,
                         BorrowRepository borrowRepository,
                         ReadingProgressService readingProgressService) {
        this(bookRepository, borrowRepository, readingProgressService, null);
    }

    public BorrowService(BookRepository bookRepository,
                         BorrowRepository borrowRepository,
                         ReadingProgressService readingProgressService,
                         NotificationService notificationService) {
        this.bookRepository = bookRepository;
        this.borrowRepository = borrowRepository;
        this.readingProgressService = readingProgressService;
        this.notificationService = notificationService;
    }

    // Optional post-construction wiring for the notification dependency. Used by
    // composition roots (LibraryApiHandlers, TestContext) where NotificationService
    // is created after BorrowService.
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Method to borrow a book for a user, with an optional parameter for the number of days to borrow, validating the book's availability and the user's borrowing limits before creating a borrow record and updating the book's availability status.
    public BorrowRecord borrowBook(String username, String bookId) {
        return borrowBook(username, bookId, DEFAULT_BORROW_DAYS);
    }

    // Overloaded method to borrow a book with a specified number of days, allowing for more flexible borrowing durations while still enforcing validation rules for book availability and user borrowing limits.
    public BorrowRecord borrowBook(String username, String bookId, int borrowDays) {
        autoReturnOverdueBooks(username);
        validateBorrowDuration(borrowDays);
        if (hasActiveBorrowForUserAndBook(username, bookId)) {
            throw new BusinessException("You have already borrowed this book and have not returned it yet.");
        }
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

        book.markBorrowedCopy();
        ensureReadingProgress(username, bookId);
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
            if (hasActiveBorrowForUserAndBook(username, bookId)) {
                throw new BusinessException("Cannot borrow the same book twice before returning it: " + bookId);
            }
            booksToBorrow.add(requireBorrowableBook(bookId));
        }

        LocalDate now = LocalDate.now();
        LocalDate due = now.plusDays(borrowDays);
        List<BorrowRecord> records = new ArrayList<>();
        for (Book book : booksToBorrow) {
            BorrowRecord record = new BorrowRecord(username, book.getId(), now, due);
            borrowRepository.save(record);
            book.markBorrowedCopy();
            ensureReadingProgress(username, book.getId());
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
        book.markReturnedCopy();
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
            bookRepository.findById(record.getBookId()).ifPresent(Book::markReturnedCopy);
            sendAutoReturnNotification(record, today);
        }
        return overdue.size();
    }

    private void sendAutoReturnNotification(BorrowRecord record, LocalDate today) {
        if (notificationService == null) {
            return;
        }
        if (notificationService.autoReturnNotificationExists(record.getUsername(), record.getId())) {
            return;
        }
        String bookTitle = bookRepository.findById(record.getBookId())
                .map(Book::getTitle)
                .orElse(record.getBookId());
        String title = "Book auto-returned";
        String message = "Your borrow of \"" + bookTitle + "\" was auto-returned on " + today
                + " because its due date (" + record.getDueDate() + ") has passed.";
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("type", "auto-return");
        metadata.put("borrowRecordId", record.getId());
        metadata.put("bookId", record.getBookId());
        metadata.put("dueDate", record.getDueDate().toString());
        metadata.put("returnedDate", today.toString());
        notificationService.addNotification(
                record.getUsername(),
                title,
                message,
                NotificationPriority.HIGH,
                null,
                metadata
        );
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

    private void ensureReadingProgress(String username, String bookId) {
        if (readingProgressService != null) {
            readingProgressService.getProgress(username, bookId);
        }
    }

    private boolean hasActiveBorrowForUserAndBook(String username, String bookId) {
        return borrowRepository.findByUsername(username).stream()
                .anyMatch(record -> !record.isReturned() && record.getBookId().equals(bookId));
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
