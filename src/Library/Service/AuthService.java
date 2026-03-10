package Library.Service;
import Library.Exception.AuthenticationException;
import Library.Exception.ValidationException;
import Library.Model.Role;
import Library.Model.User;
import Library.Repository.UserRepository;
import Library.Security.PasswordHasher;
import Library.Security.PasswordPolicy;

public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User registerStudentOrStaff(String username, String fullName, String password, Role role) {
        if (role != Role.STUDENT && role != Role.STAFF) {
            throw new ValidationException("Only STUDENT or STAFF role is allowed here.");
        }

        validateBasicFields(username, fullName);
        PasswordPolicy.validate(password);

        if (userRepository.existsByUsername(username)) {
            throw new ValidationException("Username already exists.");
        }

        String hash = PasswordHasher.sha256(password);
        User user = new User(username, fullName, hash, role);
        userRepository.save(user);
        return user;
    }

    public User loginStudentOrStaff(String username, String password, Role expectedRole) {
        if (expectedRole != Role.STUDENT && expectedRole != Role.STAFF) {
            throw new ValidationException("Expected role must be STUDENT or STAFF.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));

        if (user.getRole() != expectedRole) {
            throw new AuthenticationException("This username does not belong to " + expectedRole + " account.");
        }

        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }

        return user;
    }

    private void validateBasicFields(String username, String fullName) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Username cannot be empty.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("Full Name cannot be empty.");
        }
    }
}
