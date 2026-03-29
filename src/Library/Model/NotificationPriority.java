package Library.Model;

public enum NotificationPriority {
    HIGH,
    NORMAL,
    LOW;

    public static NotificationPriority fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        try {
            return NotificationPriority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return NORMAL;
        }
    }
}
