// [Task 2]
package Library.Repository;

import Library.Model.BookDraft2;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MemoryBookDraftRepository2 implements BookDraftRepository2 {
    private final Map<String, BookDraft2> map = new HashMap<>();

    @Override
    public void save(BookDraft2 draft) {
        map.put(draft.getAuthorUsername(), draft);
    }

    @Override
    public Optional<BookDraft2> findByAuthorUsername(String authorUsername) {
        return Optional.ofNullable(map.get(authorUsername));
    }

    @Override
    public void deleteByAuthorUsername(String authorUsername) {
        map.remove(authorUsername);
    }
}
