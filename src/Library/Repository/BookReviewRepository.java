package Library.Repository;

import Library.Model.BookReview;
import java.util.List;
import java.util.Optional;

public interface BookReviewRepository {
    void save(BookReview review);
    Optional<BookReview> findById(String id);
    Optional<BookReview> findByUsernameAndBookId(String username, String bookId);
    List<BookReview> findByBookId(String bookId);
    List<BookReview> findByUsername(String username);
    List<BookReview> findAll();
}