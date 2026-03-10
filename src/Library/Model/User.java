package Library.Model;

import javax.management.relation.Role;
import java.time.LocalDateTime;
import java.util.Objects;

public class User {
    private final String username;
    private final String fullName;
    private final String passwordHash;
    private final Role role;
    private final LocalDateTime createdAt;

    public User(String username, String fullName, String passwordHash, Role role) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = LocalDateTime.now();
    }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }
}
