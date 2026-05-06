package Library.Model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReadingProgress implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String bookId;
    private int bookmarkPage;
    private List<String> highlights;
    private int totalReadingSeconds;
    private LocalDateTime updatedAt;

    public ReadingProgress(String username, String bookId) {
        this.username = username;
        this.bookId = bookId;
        this.bookmarkPage = 1;
        this.highlights = new ArrayList<>();
        this.totalReadingSeconds = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public String getUsername() { return username; }
    public String getBookId() { return bookId; }
    public int getBookmarkPage() { return bookmarkPage; }
    public List<String> getHighlights() { return new ArrayList<>(highlights); }
    public int getTotalReadingSeconds() { return totalReadingSeconds; }
    public int getTotalReadingMinutes() { return (int) Math.round(totalReadingSeconds / 60.0); }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void update(int bookmarkPage, List<String> highlights) {
        this.bookmarkPage = Math.max(1, bookmarkPage);
        this.highlights = highlights == null ? new ArrayList<>() : new ArrayList<>(highlights);
        this.updatedAt = LocalDateTime.now();
    }

    public void addReadingSeconds(int seconds) {
        if (seconds <= 0) {
            return;
        }
        this.totalReadingSeconds = Math.max(0, this.totalReadingSeconds + seconds);
        this.updatedAt = LocalDateTime.now();
    }

    public void addReadingMinutes(int minutes) {
        if (minutes <= 0) {
            return;
        }
        addReadingSeconds(minutes * 60);
    }
}
