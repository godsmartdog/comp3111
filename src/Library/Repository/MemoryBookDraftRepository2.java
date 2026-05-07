// [Task 2]
//This file will be used in Library.service.AuthorDraftService

// Import the system library used for extension handling
package Library.Repository;
//import class
import Library.Model.BookDraft2;
import java.io.Serializable;
//import map for storing
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
//safe return
import java.util.Optional;

public class MemoryBookDraftRepository2 implements BookDraftRepository2, Serializable {
    private static final long serialVersionUID = 1L;

    //create map to store
    private final Map<String, Map<String, BookDraft2>> draftsByAuthor = new HashMap<>();

    //we take AuthorUsername + title as key for draft
    @Override
    public void save(BookDraft2 draft) {
        draftsByAuthor.computeIfAbsent(draft.getAuthorUsername(), key -> new LinkedHashMap<>())
                .put(normalizeTitle(draft.getTitle()), draft);
    }

    //we use authorUsername + title as key to find a specific draft
    @Override
    public Optional<BookDraft2> findByAuthorUsernameAndTitle(String authorUsername, String title) {
        Map<String, BookDraft2> drafts = draftsByAuthor.get(authorUsername);
        if (drafts == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(drafts.get(normalizeTitle(title)));
    }

    @Override
    public List<BookDraft2> findAllByAuthorUsername(String authorUsername) {
        Map<String, BookDraft2> drafts = draftsByAuthor.get(authorUsername);
        if (drafts == null) {
            return List.of();
        }
        return new ArrayList<>(drafts.values());
    }

    //we use authorUsername + title as key to delete a specific draft
    @Override
    public void deleteByAuthorUsernameAndTitle(String authorUsername, String title) {
        Map<String, BookDraft2> drafts = draftsByAuthor.get(authorUsername);
        if (drafts == null) {
            return;
        }
        drafts.remove(normalizeTitle(title));
        if (drafts.isEmpty()) {
            draftsByAuthor.remove(authorUsername);
        }
    }

    private String normalizeTitle(String title) {
        return Objects.requireNonNull(title, "Draft title cannot be null.").trim();
    }
}
