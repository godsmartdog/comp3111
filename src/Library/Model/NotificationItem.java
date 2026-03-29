package Library.Model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class NotificationItem {
    private final String id;
    private final String username;
    private final String title;
    private final String message;
    private final LocalDateTime createdAt;
    private final NotificationAction action;
    private final Map<String, String> metadata;
    private boolean read;

    public NotificationItem(String username, String title, String message, LocalDateTime createdAt) {
        this(username, title, message, createdAt, null, Map.of());
    }

    public NotificationItem(String username,
                            String title,
                            String message,
                            LocalDateTime createdAt,
                            NotificationAction action,
                            Map<String, String> metadata) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.action = action;
        this.metadata = Collections.unmodifiableMap(copyMetadata(metadata));
        this.read = false;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public NotificationAction getAction() { return action; }
    public Map<String, String> getMetadata() { return metadata; }
    public boolean isRead() { return read; }

    public void markRead() {
        this.read = true;
    }

    private static Map<String, String> copyMetadata(Map<String, String> source) {
        Map<String, String> values = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return values;
        }

        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            values.put(entry.getKey().trim(), entry.getValue().trim());
        }
        return values;
    }
}
