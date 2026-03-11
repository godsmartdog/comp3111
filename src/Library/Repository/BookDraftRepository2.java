package Library.Repository;

import Library.Model.BookDraft2;
import java.util.Optional;

// Repository interface for managing author book drafts.
public interface BookDraftRepository2 {
    void save(BookDraft2 draft);
    Optional<BookDraft2> findByAuthorUsername(String authorUsername);
    void deleteByAuthorUsername(String authorUsername);
}
