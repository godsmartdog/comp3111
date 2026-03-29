package Library.Model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReadingProgress {
    private final String username;
    private final String bookId;
    private int bookmarkPage;
    private List<String> highlights;
    private LocalDateTime updatedAt;

    public ReadingProgress(String username, String bookId) {
        this.username = username;
        this.bookId = bookId;
        this.bookmarkPage = 1;
        this.highlights = new ArrayList<>();
        this.updatedAt = LocalDateTime.now();
    }

    public String getUsername() { return username; }
    public String getBookId() { return bookId; }
    public int getBookmarkPage() { return bookmarkPage; }
    public List<String> getHighlights() { return new ArrayList<>(highlights); }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void update(int bookmarkPage, List<String> highlights) {
        this.bookmarkPage = Math.max(1, bookmarkPage);
        this.highlights = highlights == null ? new ArrayList<>() : new ArrayList<>(highlights);
        this.updatedAt = LocalDateTime.now();
    }
}
