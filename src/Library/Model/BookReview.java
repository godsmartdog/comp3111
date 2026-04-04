package Library.Model;

import java.time.LocalDateTime;
import java.util.UUID;

public class BookReview {
    private final String id;
    private final String username;
    private final String bookId;
    private int rating;
    private String reviewText;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BookReview(String username, String bookId, int rating, String reviewText) {
        this.id = UUID.randomUUID().toString();
        this.username = username == null ? "" : username.trim();
        this.bookId = bookId == null ? "" : bookId.trim();
        this.rating = rating;
        this.reviewText = reviewText == null ? "" : reviewText.trim();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getBookId() {
        return bookId;
    }

    public int getRating() {
        return rating;
    }

    public String getReviewText() {
        return reviewText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(int rating, String reviewText) {
        this.rating = rating;
        this.reviewText = reviewText == null ? "" : reviewText.trim();
        this.updatedAt = LocalDateTime.now();
    }
}