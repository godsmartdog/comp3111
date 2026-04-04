package Library.Repository;

import Library.Model.BookRequest2;
import Library.Model.BookRequestStatus;

import java.util.List;
import java.util.Optional;

public interface BookRequestRepository2 {
    void save(BookRequest2 request);
    Optional<BookRequest2> findById(String id);
    List<BookRequest2> findAll();
    List<BookRequest2> findByStatus(BookRequestStatus status);
    List<BookRequest2> findByRequesterUsername(String username);
}