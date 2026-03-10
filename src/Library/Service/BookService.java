package Library.Service;

import Library.Model.Book;
import Library.Repository.BookRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BookService {
    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> listApprovedBooksWithAvailability() {
        return bookRepository.findAll().stream()
                .filter(Book::isApproved)
                .sorted(Comparator.comparing(Book::getTitle))
                .collect(Collectors.toList());
    }
}
