package Library.Model;

import java.time.LocalDateTime;

public class SessionSnapshot {
    private final String sessionId;
    private final String username;
    private final Role role;
    private final String portalKey;
    private final String lastViewKey;
    private final String lastAction;
    private final LocalDateTime capturedAt;
    private final String statePayload;

    public SessionSnapshot(String sessionId,
                           String username,
                           Role role,
                           String portalKey,
                           String lastViewKey,
                           String lastAction,
                           LocalDateTime capturedAt,
                           String statePayload) {
        this.sessionId = safe(sessionId);
        this.username = safe(username);
        this.role = role;
        this.portalKey = safe(portalKey);
        this.lastViewKey = safe(lastViewKey);
        this.lastAction = safe(lastAction);
        this.capturedAt = capturedAt == null ? LocalDateTime.now() : capturedAt;
        this.statePayload = safe(statePayload);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public String getPortalKey() {
        return portalKey;
    }

    public String getLastViewKey() {
        return lastViewKey;
    }

    public String getLastAction() {
        return lastAction;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public String getStatePayload() {
        return statePayload;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}