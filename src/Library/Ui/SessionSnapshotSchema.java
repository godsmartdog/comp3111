package Library.Ui;

import Library.Model.User;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SessionSnapshotSchema {
    private static final int SCHEMA_VERSION = 1;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final int schemaVersion;
    private final LocalDateTime capturedAt;
    private final List<SessionEntry> sessions;

    private SessionSnapshotSchema(int schemaVersion, LocalDateTime capturedAt, List<SessionEntry> sessions) {
        this.schemaVersion = schemaVersion;
        this.capturedAt = capturedAt;
        this.sessions = List.copyOf(sessions);
    }

    public static SessionSnapshotSchema empty() {
        return new SessionSnapshotSchema(SCHEMA_VERSION, LocalDateTime.now(), List.of());
    }

    public static SessionSnapshotSchema capture(Map<String, User> activeSessions) {
        List<SessionEntry> entries = new ArrayList<>();
        for (Map.Entry<String, User> session : activeSessions.entrySet()) {
            User user = session.getValue();
            entries.add(new SessionEntry(
                    session.getKey(),
                    user.getUsername(),
                    user.getFullName(),
                    user.getRole().name()
            ));
        }
        return new SessionSnapshotSchema(SCHEMA_VERSION, LocalDateTime.now(), entries);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public LocalDateTime capturedAt() {
        return capturedAt;
    }

    public List<SessionEntry> sessions() {
        return sessions;
    }

    public String toJson() {
        List<String> values = new ArrayList<>();
        for (SessionEntry entry : sessions) {
            values.add("{" +
                    "\"sessionId\":\"" + JsonUtil.escape(entry.sessionId()) + "\"," +
                    "\"username\":\"" + JsonUtil.escape(entry.username()) + "\"," +
                    "\"fullName\":\"" + JsonUtil.escape(entry.fullName()) + "\"," +
                    "\"role\":\"" + JsonUtil.escape(entry.role()) + "\"" +
                    "}");
        }

        return "{" +
                "\"schemaVersion\":" + schemaVersion + "," +
                "\"capturedAt\":\"" + DATE_TIME_FORMATTER.format(capturedAt) + "\"," +
                "\"sessions\":[" + String.join(",", values) + "]" +
                "}";
    }

    public record SessionEntry(String sessionId, String username, String fullName, String role) {
    }
}