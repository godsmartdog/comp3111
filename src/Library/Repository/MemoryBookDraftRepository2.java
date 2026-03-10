// [Task 2]
//This file will be used in Library.service.AuthorDraftService

// Import the system library used for extension handling
package Library.Repository;
//import class
import Library.Model.BookDraft2;
//import map for storing
import java.util.HashMap;
import java.util.Map;
//safe return
import java.util.Optional;

public class MemoryBookDraftRepository2 implements BookDraftRepository2 {
    //create map to ctore
    private final Map<String, BookDraft2> map = new HashMap<>();

    //we take AuthorUsername as key for draft 
    @Override
    public void save(BookDraft2 draft) {
        map.put(draft.getAuthorUsername(), draft);
    }
    //we use authorUsername as key to find draft
    @Override
    public Optional<BookDraft2> findByAuthorUsername(String authorUsername) {
        return Optional.ofNullable(map.get(authorUsername));
    }
    //we use authorUsername as key to delete draft
    @Override
    public void deleteByAuthorUsername(String authorUsername) {
        map.remove(authorUsername);
    }
}
