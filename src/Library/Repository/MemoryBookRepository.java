package Library.Repository;

import Library.Model.Book;
import java.util.*;

public class MemoryBookRepository implements BookRepository {
    private final Map<String, Book> books = new HashMap<>();

    @Override
    public void save(Book book) {
        books.put(book.getId(), book);
    }

    @Override
    public Optional<Book> findById(String id) {
        return Optional.ofNullable(books.get(id));
    }

    @Override
    public List<Book> findAll() {
        return new ArrayList<>(books.values());
    }
}


