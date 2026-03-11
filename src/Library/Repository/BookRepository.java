//This file will be used in Library.service.BookService 
//function( save(), findByUsername(), findAll()) defined in Memoryxxxx -> just go see this
//Import the system library used for extension handling
package Library.Repository;
//import class of Book
import Library.Model.Book;
// we use list to store collections of books
import java.util.List;
//safe return
import java.util.Optional;

public interface BookRepository {
    void save(Book book);
    Optional<Book> findById(String id);
    List<Book> findAll();
    List<Book> searchByTitleOrAuthor(String keyword);
}
