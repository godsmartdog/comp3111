// [Task 1 nice-to-have]
package Library.Service;

import Library.Model.Book;
import Library.Model.BorrowRecord;
import Library.Repository.BookRepository;
import Library.Repository.BorrowRepository;

import java.util.*;
import java.util.stream.Collectors;

public class RecommendationService {
    private final BookRepository bookRepository;
    private final BorrowRepository borrowRepository;

    public RecommendationService(BookRepository bookRepository, BorrowRepository borrowRepository) {
        this.bookRepository = bookRepository;
        this.borrowRepository = borrowRepository;
    }

    public List<Book> recommendTopPopular(int limit) {
        Map<String, Long> countByBook = borrowRepository.findAll().stream()
                .collect(Collectors.groupingBy(BorrowRecord::getBookId, Collectors.counting()));

        return bookRepository.findAll().stream()
                .filter(Book::isApproved)
                .sorted((a, b) -> Long.compare(
                        countByBook.getOrDefault(b.getId(), 0L),
                        countByBook.getOrDefault(a.getId(), 0L)))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
