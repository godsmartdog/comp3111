// [Task 2 nice-to-have]
package Library.Service;

import Library.Exception.ValidationException;
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
        String normalizedAuthorUsername = validateAuthorUsername(authorUsername);
        String normalizedTitle = normalizeTitle(title);
        BookDraft2 draft = draftRepository.findByAuthorUsernameAndTitle(normalizedAuthorUsername, normalizedTitle)
            .orElse(new BookDraft2(normalizedAuthorUsername));
        draft.setTitle(normalizedTitle);
        draft.setGenres(genres == null ? List.of() : genres.stream()
                .map(genre -> genre == null ? "" : genre.trim())
                .filter(genre -> !genre.isBlank())
                .distinct()
                .toList());
        draft.setDescription(description == null ? "" : description.trim());
        draft.setFilePath(filePath == null ? "" : filePath.trim());
        draftRepository.save(draft);
        return draft;
    }

    public Optional<BookDraft2> loadDraft(String authorUsername, String title) {
        return draftRepository.findByAuthorUsernameAndTitle(validateAuthorUsername(authorUsername), normalizeTitle(title));
    }

    public List<BookDraft2> loadDrafts(String authorUsername) {
        return draftRepository.findAllByAuthorUsername(validateAuthorUsername(authorUsername));
    }

    public void clearDraft(String authorUsername, String title) {
        draftRepository.deleteByAuthorUsernameAndTitle(validateAuthorUsername(authorUsername), normalizeTitle(title));
    }

    private String validateAuthorUsername(String authorUsername) {
        if (authorUsername == null || authorUsername.isBlank()) {
            throw new ValidationException("Author username cannot be null or empty.");
        }
        return authorUsername.trim();
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("Draft title cannot be null or empty.");
        }
        return title.trim();
    }
}
