// [Task 2]
//This file will be used in Library.service.AuthorDraftService
//function defined in Memoryxxxx -> just go see this
// Import the system library used for extension handling
package Library.Repository;
//import the class
import Library.Model.BookDraft2;
//safe return
import java.util.Optional;

//declare function
public interface BookDraftRepository2 {
    void save(BookDraft2 draft);
    Optional<BookDraft2> findByAuthorUsername(String authorUsername);
    void deleteByAuthorUsername(String authorUsername);
}
