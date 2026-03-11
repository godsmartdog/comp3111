package Library.Security;

import Library.Model.User;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public final class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();

    private User currentUser;
    private LocalDateTime loginTime;
    private final Map<String, Object> sessionData = new HashMap<>();

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public void createSession(User user) {
        this.currentUser = user;
        this.loginTime = LocalDateTime.now();
        this.sessionData.clear();
    }

    public void destroySession() {
        this.currentUser = null;
        this.loginTime = null;
        this.sessionData.clear();
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isAuthenticated() {
        return currentUser != null;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setAttribute(String key, Object value) {
        sessionData.put(key, value);
    }

    public Object getAttribute(String key) {
        return sessionData.get(key);
    }
}
