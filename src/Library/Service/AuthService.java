package Library.Service;
import Library.Exception.AuthenticationException;
import Library.Exception.ValidationException;
import Library.Model.Role;
import Library.Model.User;
import Library.Repository.UserRepository;
import Library.Security.PasswordHasher;
import Library.Security.PasswordPolicy;
import Library.Security.SessionManager;

// Service class to handle authentication-related operations such as user registration, login, and session management for students and staff.
public class AuthService {
    private final UserRepository userRepository;
    private final SessionManager sessionManager;

    // Constructor to initialize the AuthService with the required UserRepository and SessionManager, allowing for dependency injection and better separation of concerns.
    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.sessionManager = SessionManager.getInstance();
    }

    // Method to register a new student or staff user, validating input and ensuring unique usernames, while also enforcing password policies for security.
    public User registerStudentOrStaff(String username, String fullName, String password, Role role) {
        if (role != Role.STUDENT && role != Role.STAFF) {
            throw new ValidationException("Only STUDENT or STAFF role is allowed here.");
        }

        // Validate basic input fields and enforce password policies before creating a new user account, ensuring that the username is unique and the password meets security requirements.
        validateBasicFields(username, fullName);
        PasswordPolicy.validate(password);

        // Check if the username already exists in the repository, throwing a ValidationException if it does to prevent duplicate accounts.
        if (userRepository.existsByUsername(username)) {
            throw new ValidationException("Username already exists.");
        }

        // Create and save the new user with the specified role, hashing the password for secure storage, and return the created user object.
        String hash = PasswordHasher.hashPassword(password);
        User user = new User(username, fullName, hash, role);
        userRepository.save(user);
        return user;
    }

    // Method to authenticate a student or staff user, verifying credentials and ensuring the user has the correct role before creating a session for the authenticated user.
    public User loginStudentOrStaff(String username, String password, Role expectedRole) {
        if (expectedRole != Role.STUDENT && expectedRole != Role.STAFF) {
            throw new ValidationException("Expected role must be STUDENT or STAFF.");
        }

        // Retrieve the user by username and validate the password, ensuring that the user exists and has the expected role before creating a session for the authenticated user.
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));

        if (user.getRole() != expectedRole) {
            throw new AuthenticationException("This username does not belong to " + expectedRole + " account.");
        }

        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }

        sessionManager.createSession(user);

        return user;
    }

    // Method to log out the current user by destroying the session, effectively clearing all session data and marking the user as unauthenticated.
    public void logout() {
        sessionManager.destroySession();
    }

    // Method to check if there is an active session with an authenticated user, returning true if a user is currently logged in and false otherwise.
    public boolean isLoggedIn() {
        return sessionManager.isAuthenticated();
    }

    // Method to retrieve the currently authenticated user from the session, returning the User object if a session is active or null if no user is logged in.
    public User getCurrentUser() {
        return sessionManager.getCurrentUser();
    }

    public User updateStudentOrStaffProfile(String username, String fullName, String newPassword) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Username cannot be empty.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("Full Name cannot be empty.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ValidationException("User not found."));
        if (user.getRole() != Role.STUDENT && user.getRole() != Role.STAFF) {
            throw new ValidationException("Only STUDENT or STAFF profile can be updated here.");
        }

        user.updateFullName(fullName.trim());
        if (newPassword != null && !newPassword.isBlank()) {
            PasswordPolicy.validate(newPassword);
            user.updatePasswordHash(PasswordHasher.hashPassword(newPassword));
        }

        userRepository.save(user);
        return user;
    }

    // Private helper method to validate basic input fields such as username and full name, ensuring they are not null or blank before proceeding with registration or other operations.
    private void validateBasicFields(String username, String fullName) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Username cannot be empty.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("Full Name cannot be empty.");
        }
    }
}
