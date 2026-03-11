package Library.Security;

import Library.Model.User;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public final class SessionManager {
    // Singleton instance to manage user sessions across the application.
    private static final SessionManager INSTANCE = new SessionManager();

    private User currentUser;
    private LocalDateTime loginTime;
    private final Map<String, Object> sessionData = new HashMap<>();

    // Private constructor to prevent instantiation from outside the class.
    private SessionManager() {}

    // Public method to access the singleton instance.
    public static SessionManager getInstance() {
        return INSTANCE;
    }

    // Creates a new session for the given user, storing the login time and initializing session data.
    public void createSession(User user) {
        this.currentUser = user;
        this.loginTime = LocalDateTime.now();
        this.sessionData.clear();
    }

    // Destroys the current session, clearing all user data and session attributes.
    public void destroySession() {
        this.currentUser = null;
        this.loginTime = null;
        this.sessionData.clear();
    }

    // Accessor methods to retrieve current user, check authentication status, and manage session attributes.
    public User getCurrentUser() {
        return currentUser;
    }

    // Checks if there is an active session with an authenticated user.
    public boolean isAuthenticated() {
        return currentUser != null;
    }

    // Returns the login time of the current session, or null if no session is active.
    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    // Sets a session attribute with the given key and value, allowing storage of arbitrary data for the session.
    public void setAttribute(String key, Object value) {
        sessionData.put(key, value);
    }

    // Retrieves a session attribute by key, returning null if the key does not exist in the session data.
    public Object getAttribute(String key) {
        return sessionData.get(key);
    }
}
