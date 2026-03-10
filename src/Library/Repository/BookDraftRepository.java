// [Task 2]
package Library.Repository;

import Library.Model.BookDraft2;
import java.util.Optional;

public interface BookDraftRepository2 {
    void save(BookDraft2 draft);
    Optional<BookDraft2> findByAuthorUsername(String authorUsername);
    void deleteByAuthorUsername(String authorUsername);
}
