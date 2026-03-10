//This file will be used in Library.service.BookService 

//Import the system library used for extension handling
package Library.Repository;
//import class of book
import Library.Model.Book;
import java.util.*;

public class MemoryBookRepository implements BookRepository {
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
// list all book stored currently
    @Override
    public List<Book> findAll() {
        return new ArrayList<>(books.values());
    }
}


