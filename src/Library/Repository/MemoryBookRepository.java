//This file will be used in Library.service.BookService 

//Import the system library used for extension handling
package Library.Repository;
//import class of book
import Library.Model.Book;
import java.io.Serializable;
import java.util.*;

public class MemoryBookRepository implements BookRepository, Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Book> books = new HashMap<>();
//we use id as key to store book in map
    @Override
    public void save(Book book) {
        books.put(book.getId(), book);
    }
// we use id as key to find book in map
    @Override
    public Optional<Book> findById(String id) {
        return Optional.ofNullable(books.get(id));
    }

    @Override
    public void deleteById(String id) {
        books.remove(id);
    }
// list all book stored currently
    @Override
    public List<Book> findAll() {
        return new ArrayList<>(books.values());
    }

    @Override
    public List<Book> searchByTitleOrAuthor(String keyword) {
        String normalized = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        return books.values().stream()
            .filter(b -> b.getTitle().toLowerCase(Locale.ROOT).contains(normalized)
                    || b.getAuthorFullName().toLowerCase(Locale.ROOT).contains(normalized))
            .toList();
    }
}


