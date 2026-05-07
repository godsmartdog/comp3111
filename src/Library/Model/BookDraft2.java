// [Task 2]
// imported in BookDraftRepository2 for interface

package Library.Model; // imported in BookRepository for interface

import java.io.Serializable;
import java.time.LocalDateTime; // local system date and time for reference (both the day such as YYYYMMDD and the time HHMMSS)
import java.util.ArrayList; // generic class (similar to C++ template class "array")
import java.util.List; // generic class (similar to C++ template class "list")

// Class for BookDraft - meant for work in progress books (author is still typing content)
public class BookDraft2 implements Serializable {
    private static final long serialVersionUID = 1L;

    // Member variables
    private final String authorUsername;
    private String title;
    private List<String> genres = new ArrayList<>(); // Liskov polymorphic substution principle - init RHS ArrayList<>() prevent null pointer exception
    private String description;
    private String filePath;
    private LocalDateTime lastSavedAt;

    // Constructor for book - the remaining member variables will be set up by mutators
    public BookDraft2(String authorUsername) {
        this.authorUsername = authorUsername;
        this.lastSavedAt = LocalDateTime.now();
    }

    // Accessor
    public String getAuthorUsername() { return authorUsername; }
    public String getTitle() { return title; }
    public List<String> getGenres() { return genres; }
    public String getDescription() { return description; }
    public String getFilePath() { return filePath; }
    public LocalDateTime getLastSavedAt() { return lastSavedAt; }

    // Mutator
    public void setTitle(String title) { this.title = title; touch(); }
    public void setGenres(List<String> genres) { this.genres = new ArrayList<>(genres); touch(); }
    public void setDescription(String description) { this.description = description; touch(); }
    public void setFilePath(String filePath) { this.filePath = filePath; touch(); }

    // Custom command to save latest time of saved file (similar to UNIX touch())
    private void touch() { this.lastSavedAt = LocalDateTime.now(); }
}
