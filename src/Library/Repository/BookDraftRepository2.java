package Library.Repository;

import Library.Model.BookDraft2;
import java.util.List;
import java.util.Optional;

// Repository interface for managing author book drafts.
public interface BookDraftRepository2 {
    void save(BookDraft2 draft);
    Optional<BookDraft2> findByAuthorUsernameAndTitle(String authorUsername, String title);
    List<BookDraft2> findAllByAuthorUsername(String authorUsername);
    void deleteByAuthorUsernameAndTitle(String authorUsername, String title);
}
