// [Task 2]
// imported in BookDraftRepository2 for interface

package Library.Model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BookDraft2 {
    private final String authorUsername;
    private String title;
    private List<String> genres = new ArrayList<>();
    private String description;
    private String filePath;
    private LocalDateTime lastSavedAt;

    public BookDraft2(String authorUsername) {
        this.authorUsername = authorUsername;
        this.lastSavedAt = LocalDateTime.now();
    }

    public String getAuthorUsername() { return authorUsername; }
    public String getTitle() { return title; }
    public List<String> getGenres() { return genres; }
    public String getDescription() { return description; }
    public String getFilePath() { return filePath; }
    public LocalDateTime getLastSavedAt() { return lastSavedAt; }

    public void setTitle(String title) { this.title = title; touch(); }
    public void setGenres(List<String> genres) { this.genres = new ArrayList<>(genres); touch(); }
    public void setDescription(String description) { this.description = description; touch(); }
    public void setFilePath(String filePath) { this.filePath = filePath; touch(); }

    private void touch() { this.lastSavedAt = LocalDateTime.now(); }
}
