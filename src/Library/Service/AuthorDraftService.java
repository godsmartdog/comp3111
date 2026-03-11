// [Task 2 nice-to-have]
package Library.Service;

import Library.Model.BookDraft2;
import Library.Repository.BookDraftRepository2;

import java.util.List;
import java.util.Optional;

public class AuthorDraftService {
    private final BookDraftRepository2 draftRepository;

    public AuthorDraftService(BookDraftRepository2 draftRepository) {
        this.draftRepository = draftRepository;
    }

    public BookDraft2 autoSave(String authorUsername, String title, List<String> genres, String description, String filePath) {
        BookDraft2 draft = draftRepository.findByAuthorUsernameAndTitle(authorUsername, title)
                .orElse(new BookDraft2(authorUsername));
        draft.setTitle(title);
        draft.setGenres(genres);
        draft.setDescription(description);
        draft.setFilePath(filePath);
        draftRepository.save(draft);
        return draft;
    }

    public Optional<BookDraft2> loadDraft(String authorUsername, String title) {
        return draftRepository.findByAuthorUsernameAndTitle(authorUsername, title);
    }

    public List<BookDraft2> loadDrafts(String authorUsername) {
        return draftRepository.findAllByAuthorUsername(authorUsername);
    }

    public void clearDraft(String authorUsername, String title) {
        draftRepository.deleteByAuthorUsernameAndTitle(authorUsername, title);
    }
}
