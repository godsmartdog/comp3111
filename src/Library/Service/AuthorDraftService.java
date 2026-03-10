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
        BookDraft2 draft = draftRepository.findByAuthorUsername(authorUsername).orElse(new BookDraft2(authorUsername));
        draft.setTitle(title);
        draft.setGenres(genres);
        draft.setDescription(description);
        draft.setFilePath(filePath);
        draftRepository.save(draft);
        return draft;
    }

    public Optional<BookDraft2> loadDraft(String authorUsername) {
        return draftRepository.findByAuthorUsername(authorUsername);
    }

    public void clearDraft(String authorUsername) {
        draftRepository.deleteByAuthorUsername(authorUsername);
    }
}
