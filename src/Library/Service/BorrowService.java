package Library.Service;

import Library.Exception.BusinessException;
import Library.Exception.NotFoundException;
import Library.Model.Book;
import Library.Model.BorrowRecord;
import Library.Repository.BookRepository;
import Library.Repository.BorrowRepository;
import Library.Security.SecurityConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

// Service class to handle borrowing-related operations such as borrowing and returning books, as well as listing active borrows for a user.
public class BorrowService {
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
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found."));

        if (!book.isApproved()) {
            throw new BusinessException("Book is not approved yet.");
        }
        if (!book.isAvailable()) {
            throw new BusinessException("Book is currently unavailable.");
        }

        // Check the number of active borrows for the user to enforce the maximum borrow limit, counting only those borrow records that have not been marked as returned.
        long activeBorrows = borrowRepository.findByUsername(username).stream()
                .filter(r -> !r.isReturned())
                .count();

        if (activeBorrows >= MAX_BORROW_LIMIT) {
            throw new BusinessException("Borrow limit reached. Max allowed is " + MAX_BORROW_LIMIT + ".");
        }

        if (borrowDays <= 0 || borrowDays > MAX_BORROW_DAYS) {
            throw new BusinessException("Borrow duration must be between 1 and " + MAX_BORROW_DAYS + " days.");
        }

        // Create a new borrow record with the current date as the borrow date and calculate the due date based on the specified number of borrow days, then save the record and update the book's availability status to false.
        LocalDate now = LocalDate.now();
        LocalDate due = now.plusDays(borrowDays);
        BorrowRecord record = new BorrowRecord(username, bookId, now, due);
        borrowRepository.save(record);

        book.setAvailable(false);
        return record;
    }

    // Method to return a borrowed book for a user, validating the existence of an active borrow record for the specified user and book, marking the record as returned, and updating the book's availability status to true.
    public BorrowRecord returnBook(String username, String bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found."));

        BorrowRecord record = borrowRepository.findActiveByUsernameAndBookId(username, bookId)
                .orElseThrow(() -> new BusinessException("No active borrow record found for this user and book."));

        record.markReturned();
        book.setAvailable(true);
        return record;
    }

    // Method to list all active borrows for a specific user, filtering the borrow records to include only those that have not been marked as returned and returning the list of active borrow records.
    public List<BorrowRecord> listActiveBorrowsByUser(String username) {
        return borrowRepository.findByUsername(username).stream()
                .filter(r -> !r.isReturned())
                .collect(Collectors.toList());
    }
}
