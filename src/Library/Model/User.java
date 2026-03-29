package Library.Model;

import java.time.LocalDateTime; // local system date and time for reference (both the day such as YYYYMMDD and the time HHMMSS)
import java.util.Objects; // the Java Object class is here for convenient bulit-in function

// This is a general User class, for future specialization classes (such as Student/Staff, Author, Librarian)
public class User {

    // Member variables
    private final String username;
    private String fullName;
    private String passwordHash;
    private final Role role;
    private final LocalDateTime createdAt;

    // Constructor
    public User(String username, String fullName, String passwordHash, Role role) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = LocalDateTime.now(); // retrieve current system time
    }

    // Accessor
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void updateFullName(String fullName) {
        this.fullName = fullName;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    // Foundation for future implementation - customizing/ overloading operators
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof User user)) return false;
        return Objects.equals(username, user.username);
    }

    // Foundation for future implementation - customizing/ overloading operators
    @Override
    public int hashCode() {
        return Objects.hash(username);
    }
}
