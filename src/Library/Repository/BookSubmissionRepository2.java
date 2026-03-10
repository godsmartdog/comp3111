package Library.Repository;

import Library.Model.BookSubmission2;
import Library.Model.SubmissionState;

import java.util.List;
import java.util.Optional;

public interface BookSubmissionRepository2 {
    void save(BookSubmission2 submission);
    Optional<BookSubmission2> findById(String id);
    List<BookSubmission2> findAll();
    List<BookSubmission2> findByStatus(SubmissionState status);
    List<BookSubmission2> findByAuthorUsername(String username);
}
