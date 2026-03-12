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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Service class to handle author-related operations such as registration, login, and book submission.
public class AuthorService2 {
    // Supported genres for book submissions, defined as a constant set for validation purposes.
    private static final Set<String> SUPPORTED_GENRES = Set.of(
            "Fiction", "Non-Fiction", "Science", "Technology", "History",
            "Biography", "Fantasy", "Mystery", "Romance", "Education"
    );
        private static final Map<String, String> GENRE_LOOKUP = SUPPORTED_GENRES.stream()
            .collect(Collectors.toMap(
                genre -> genre.toLowerCase(Locale.ROOT),
                genre -> genre
            ));

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
        String normalizedUsername = normalizeRequired(username, "Username cannot be empty.");
        String normalizedFullName = normalizeRequired(fullName, "Full Name cannot be empty.");
        PasswordPolicy.validate(password);

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ValidationException("Username already exists.");
        }

        // Create and save the new user with the AUTHOR role, and also create an associated author profile with the provided bio.
        User user = new User(normalizedUsername, normalizedFullName, PasswordHasher.hashPassword(password), Role.AUTHOR);
        userRepository.save(user);
        authorProfileRepository.save(new AuthorProfile2(normalizedUsername, bio == null ? "" : bio.trim()));
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
        String normalizedAuthorUsername = normalizeRequired(authorUsername, "Author username cannot be empty.");
        String normalizedTitle = normalizeRequired(title, "Title cannot be empty.");
        String normalizedDescription = normalizeRequired(description, "Description cannot be empty.");
        String normalizedFileName = normalizeRequired(fileName, "Book file is required.");

        // Validate genres and file format before proceeding with submission creation.
        List<String> normalizedGenres = normalizeGenres(genres, "At least one genre is required.");
        validateFileFormat(normalizedFileName);

        // Ensure the author exists and has the AUTHOR role before allowing book submission, throwing an exception if validation fails.
        User author = userRepository.findByUsername(normalizedAuthorUsername)
                .orElseThrow(() -> new ValidationException("Author user not found."));
        if (author.getRole() != Role.AUTHOR) throw new ValidationException("User is not an author.");

        // Create and save the book submission, associating it with the author's username and full name for future reference and tracking.
        BookSubmission2 submission = new BookSubmission2(
            normalizedTitle, author.getUsername(), author.getFullName(), normalizedGenres, normalizedDescription, normalizedFileName
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
        String normalizedTitle = normalizeRequired(title, "Title cannot be empty for preview.");
        String normalizedDescription = normalizeRequired(description, "Description cannot be empty for preview.");

        // Validate genres before generating the preview, ensuring that only supported genres are included in the output.
        List<String> normalizedGenres = normalizeGenres(genres, "At least one genre is required for preview.");
        if (normalizedDescription.length() > 300) {
            normalizedDescription = normalizedDescription.substring(0, 300) + "...";
        }

        return "=== Book Preview ===\n"
                + "Title: " + normalizedTitle + "\n"
                + "Genres: " + normalizedGenres + "\n"
                + "Description: " + normalizedDescription + "\n";
    }

    // Private helper method to validate that the provided genres are all supported, throwing a ValidationException if any unsupported genres are found.
    private List<String> normalizeGenres(List<String> genres, String emptyMessage) {
        if (genres == null) {
            throw new ValidationException(emptyMessage);
        }

        List<String> invalid = new ArrayList<>();
        List<String> normalized = new ArrayList<>();
        for (String genre : genres) {
            if (genre == null || genre.isBlank()) {
                continue;
            }

            String canonicalGenre = GENRE_LOOKUP.get(genre.trim().toLowerCase(Locale.ROOT));
            if (canonicalGenre == null) {
                invalid.add(genre.trim());
                continue;
            }
            if (!normalized.contains(canonicalGenre)) {
                normalized.add(canonicalGenre);
            }
        }

        if (normalized.isEmpty()) {
            throw new ValidationException(emptyMessage);
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException("Unsupported genres: " + invalid + ". Supported: " + getSupportedGenres());
        }
        return normalized;
    }

    // Private helper method to validate the file format of the submitted book, ensuring that only allowed formats are accepted and throwing a ValidationException if the format is unsupported.
    private void validateFileFormat(String fileName) {
        String lower = fileName.toLowerCase();
        if (!(lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".doc") || lower.endsWith(".docx"))) {
            throw new ValidationException("Unsupported file format. Allowed: pdf, txt, doc, docx.");
        }
    }

    // Private helper method to validate basic input fields such as username and full name, ensuring they are not null or blank before proceeding with registration or other operations.
    private String normalizeRequired(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(errorMessage);
        }
        return value.trim();
    }
}
