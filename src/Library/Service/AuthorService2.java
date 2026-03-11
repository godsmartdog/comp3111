package Library.Service;

import Library.Exception.AuthenticationException;
import Library.Exception.ValidationException;
import Library.Model.AuthorProfile2;
import Library.Model.BookSubmission2;
import Library.Model.Role;
import Library.Model.User;
import Library.Repository.AuthorProfileRepository2;
import Library.Repository.BookSubmissionRepository2;
import Library.Repository.UserRepository;
import Library.Security.PasswordHasher;
import Library.Security.PasswordPolicy;
import Library.Security.SessionManager;
import java.util.List;
import java.util.Set;

// Service class to handle author-related operations such as registration, login, and book submission.
public class AuthorService2 {
    // Supported genres for book submissions, defined as a constant set for validation purposes.
    private static final Set<String> SUPPORTED_GENRES = Set.of(
            "Fiction", "Non-Fiction", "Science", "Technology", "History",
            "Biography", "Fantasy", "Mystery", "Romance", "Education"
    );

    // Repositories for user management, author profiles, and book submissions, injected via constructor for better testability and separation of concerns.
    private final UserRepository userRepository;
    private final AuthorProfileRepository2 authorProfileRepository;
    private final BookSubmissionRepository2 submissionRepository;

    // Constructor to initialize the service with required repositories, allowing for dependency injection and easier testing.
    public AuthorService2(UserRepository userRepository,
                         AuthorProfileRepository2 authorProfileRepository,
                         BookSubmissionRepository2 submissionRepository) {
        this.userRepository = userRepository;
        this.authorProfileRepository = authorProfileRepository;
        this.submissionRepository = submissionRepository;
    }

    // Method to register a new author, validating input and ensuring unique usernames, while also creating an associated author profile.
    public User registerAuthor(String username, String fullName, String password, String bio) {
        validateBasic(username, fullName);
        PasswordPolicy.validate(password);

        if (userRepository.existsByUsername(username)) {
            throw new ValidationException("Username already exists.");
        }

        // Create and save the new user with the AUTHOR role, and also create an associated author profile with the provided bio.
        User user = new User(username, fullName, PasswordHasher.hashPassword(password), Role.AUTHOR);
        userRepository.save(user);
        authorProfileRepository.save(new AuthorProfile2(username, bio == null ? "" : bio));
        return user;
    }

    // Method to authenticate an author, verifying credentials and ensuring the user has the AUTHOR role before creating a session.
    public User loginAuthor(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));
        if (user.getRole() != Role.AUTHOR) {
            throw new AuthenticationException("This username does not belong to AUTHOR account.");
        }
        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }
        SessionManager.getInstance().createSession(user);
        return user;
    }

    // Method to submit a new book for publication, validating input and ensuring the submitting user is an authenticated author before saving the submission.
    public BookSubmission2 publishBook(String authorUsername, String title, List<String> genres,
                                      String description, String fileName) {
        if (title == null || title.isBlank()) throw new ValidationException("Title cannot be empty.");
        if (genres == null || genres.isEmpty()) throw new ValidationException("At least one genre is required.");
        if (description == null || description.isBlank()) throw new ValidationException("Description cannot be empty.");
        if (fileName == null || fileName.isBlank()) throw new ValidationException("Book file is required.");

        // Validate genres and file format before proceeding with submission creation.
        validateGenres(genres);
        validateFileFormat(fileName);

        // Ensure the author exists and has the AUTHOR role before allowing book submission, throwing an exception if validation fails.
        User author = userRepository.findByUsername(authorUsername)
                .orElseThrow(() -> new ValidationException("Author user not found."));
        if (author.getRole() != Role.AUTHOR) throw new ValidationException("User is not an author.");

        // Create and save the book submission, associating it with the author's username and full name for future reference and tracking.
        BookSubmission2 submission = new BookSubmission2(
                title, author.getUsername(), author.getFullName(), genres, description, fileName
        );
        submissionRepository.save(submission);
        return submission;
    }

    // Method to retrieve the list of supported genres, returning them in a sorted order for better user experience when displaying options.
    public List<String> getSupportedGenres() {
        return SUPPORTED_GENRES.stream().sorted().toList();
    }

    // Method to generate a preview of the book submission, validating input and providing a formatted string that includes the title, genres, and a truncated description for display purposes.
    public String previewBook(String title, List<String> genres, String description) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("Title cannot be empty for preview.");
        }
        if (genres == null || genres.isEmpty()) {
            throw new ValidationException("At least one genre is required for preview.");
        }
        if (description == null || description.isBlank()) {
            throw new ValidationException("Description cannot be empty for preview.");
        }

        // Validate genres before generating the preview, ensuring that only supported genres are included in the output.
        String normalizedDescription = description.trim();
        if (normalizedDescription.length() > 300) {
            normalizedDescription = normalizedDescription.substring(0, 300) + "...";
        }

        return "=== Book Preview ===\n"
                + "Title: " + title.trim() + "\n"
                + "Genres: " + genres + "\n"
                + "Description: " + normalizedDescription + "\n";
    }

    // Private helper method to validate that the provided genres are all supported, throwing a ValidationException if any unsupported genres are found.
    private void validateGenres(List<String> genres) {
        List<String> invalid = genres.stream()
                .filter(g -> !SUPPORTED_GENRES.contains(g))
                .toList();
        if (!invalid.isEmpty()) {
            throw new ValidationException("Unsupported genres: " + invalid + ". Supported: " + getSupportedGenres());
        }
    }

    // Private helper method to validate the file format of the submitted book, ensuring that only allowed formats are accepted and throwing a ValidationException if the format is unsupported.
    private void validateFileFormat(String fileName) {
        String lower = fileName.toLowerCase();
        if (!(lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".doc") || lower.endsWith(".docx"))) {
            throw new ValidationException("Unsupported file format. Allowed: pdf, txt, doc, docx.");
        }
    }

    // Private helper method to validate basic input fields such as username and full name, ensuring they are not null or blank before proceeding with registration or other operations.
    private void validateBasic(String username, String fullName) {
        if (username == null || username.isBlank()) throw new ValidationException("Username cannot be empty.");
        if (fullName == null || fullName.isBlank()) throw new ValidationException("Full Name cannot be empty.");
    }
}
