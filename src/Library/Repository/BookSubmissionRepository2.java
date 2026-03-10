//This file will be used in Library.service.LibrarianService3
//all function( save(), findById(), findall(), findBystatus(), findByAuthorUsername()) defined in Memoryxxxx -> just go see this
// Import the system library used for extension handling
package Library.Repository;
//for class of BookSubmission2
import Library.Model.BookSubmission2;
//data type Submission state
import Library.Model.SubmissionState;
//list to store collections
import java.util.List;
//safe return
import java.util.Optional;

public interface BookSubmissionRepository2 {
    void save(BookSubmission2 submission);
    Optional<BookSubmission2> findById(String id);
    List<BookSubmission2> findAll();
    List<BookSubmission2> findByStatus(SubmissionState status);
    List<BookSubmission2> findByAuthorUsername(String username);
}
