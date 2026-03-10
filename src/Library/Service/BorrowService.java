package Library.Service;

import Library.Exception.BusinessException;
import Library.Exception.NotFoundException;
import Library.Model.Book;
import Library.Model.BorrowRecord;
import Library.Repository.BookRepository;
import Library.Repository.BorrowRepository;

import java.time.LocalDate;

public class BorrowService {
    private static final int MAX_BORROW_LIMIT = 5;
    private static final int DEFAULT_BORROW_DAYS = 14;

    private final BookRepository bookRepository;
    private final BorrowRepository borrowRepository;

    public BorrowService(BookRepository bookRepository, BorrowRepository borrowRepository) {
        this.bookRepository = bookRepository;
        this.borrowRepository = borrowRepository;
    }

    public BorrowRecord borrowBook(String username, String bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found."));

        if (!book.isApproved()) {
            throw new BusinessException("Book is not approved yet.");
        }
        if (!book.isAvailable()) {
            throw new BusinessException("Book is currently unavailable.");
        }

        long activeBorrows = borrowRepository.findByUsername(username).stream()
                .filter(r -> !r.isReturned())
                .count();

        if (activeBorrows >= MAX_BORROW_LIMIT) {
            throw new BusinessException("Borrow limit reached. Max allowed is " + MAX_BORROW_LIMIT + ".");
        }

        LocalDate now = LocalDate.now();
        LocalDate due = now.plusDays(DEFAULT_BORROW_DAYS);
        BorrowRecord record = new BorrowRecord(username, bookId, now, due);
        borrowRepository.save(record);

        book.setAvailable(false);
        return record;
    }
}
