package Library.Model;

import java.time.LocalDateTime;
import java.util.UUID;

public class BookReview {
    private final String id;
    private final String username;
    private final String bookId;
    private int rating;
    private String reviewText;
    private String replyText;
    private boolean flagged;
    private String flagReason;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime repliedAt;
    private LocalDateTime flaggedAt;

    public BookReview(String username, String bookId, int rating, String reviewText) {
        this.id = UUID.randomUUID().toString();
        this.username = username == null ? "" : username.trim();
        this.bookId = bookId == null ? "" : bookId.trim();
        this.rating = rating;
        this.reviewText = reviewText == null ? "" : reviewText.trim();
        this.replyText = "";
        this.flagged = false;
        this.flagReason = "";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.repliedAt = null;
        this.flaggedAt = null;
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

    public String getReplyText() {
        return replyText;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public String getFlagReason() {
        return flagReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getRepliedAt() {
        return repliedAt;
    }

    public LocalDateTime getFlaggedAt() {
        return flaggedAt;
    }

    public void update(int rating, String reviewText) {
        this.rating = rating;
        this.reviewText = reviewText == null ? "" : reviewText.trim();
        this.updatedAt = LocalDateTime.now();
    }

    public void reply(String replyText) {
        this.replyText = replyText == null ? "" : replyText.trim();
        this.repliedAt = LocalDateTime.now();
        this.updatedAt = this.repliedAt;
    }

    public void flag(String reason) {
        this.flagged = true;
        this.flagReason = reason == null ? "" : reason.trim();
        this.flaggedAt = LocalDateTime.now();
        this.updatedAt = this.flaggedAt;
    }
}