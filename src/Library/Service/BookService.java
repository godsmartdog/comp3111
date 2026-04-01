package Library.Service;

import Library.Model.Book;
import Library.Repository.BookRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Service class to handle book-related operations such as listing approved books and searching for books by title or author.
public class BookService {
    private final BookRepository bookRepository;

    // Constructor to initialize the BookService with the required BookRepository, allowing for dependency injection and better separation of concerns.
    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    // Method to list all approved books along with their availability status, filtering the books to include only those that are approved and sorting them by title before returning the list.
    public List<Book> listApprovedBooksWithAvailability() {
        return bookRepository.findAll().stream()
                .filter(Book::isApproved)
                .sorted(Comparator.comparing(Book::getTitle))
                .collect(Collectors.toList());
    }

    // Method to search for approved books by title or author, filtering the search results to include only approved books and sorting them by title before returning the list.
    public List<Book> searchApprovedBooks(String keyword) {
        return bookRepository.searchByTitleOrAuthor(keyword).stream()
                .filter(Book::isApproved)
                .sorted(Comparator.comparing(Book::getTitle))
                .collect(Collectors.toList());
    }

    public List<Book> listApprovedBooksWithFilters(String keyword, Boolean availableFilter) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Book> base = normalizedKeyword.isEmpty()
                ? listApprovedBooksWithAvailability()
                : searchApprovedBooks(normalizedKeyword);

        if (availableFilter == null) {
            return base;
        }

        return base.stream()
                .filter(book -> book.isAvailable() == availableFilter)
                .collect(Collectors.toList());
    }

    public List<Book> listApprovedBooksByAuthorUsername(String authorUsername) {
        String normalized = authorUsername == null ? "" : authorUsername.trim();
        return bookRepository.findAll().stream()
                .filter(Book::isApproved)
                .filter(book -> book.getAuthorUsername().equals(normalized))
                .sorted(Comparator.comparing(Book::getTitle))
                .collect(Collectors.toList());
    }

    public List<Book> listApprovedBooksForLibrarian() {
        return listApprovedBooksWithAvailability();
    }

    public Optional<Book> findBookById(String bookId) {
        return bookRepository.findById(bookId);
    }
}
