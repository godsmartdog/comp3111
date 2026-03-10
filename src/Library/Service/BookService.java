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
    
    public String previewBook(String title, List<String> genres, String description) {
        return "=== Preview ===\nTitle: " + title + "\nGenres: " + genres + "\nDescription: " + description;
    }

    public List<String> getSupportedGenres() {
        return List.of("Fiction", "Non-Fiction", "Education", "Science", "Technology", "History", "Fantasy");
    }
}
