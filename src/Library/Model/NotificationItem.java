package Library.Model;

import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationItem {
    private final String id;
    private final String username;
    private final String title;
    private final String message;
    private final LocalDateTime createdAt;
    private boolean read;

    public NotificationItem(String username, String title, String message, LocalDateTime createdAt) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.read = false;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }

    public void markRead() {
        this.read = true;
    }
}
