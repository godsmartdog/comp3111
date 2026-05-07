package Library.Service;

import Library.Exception.AuthenticationException;
import Library.Exception.BusinessException;
import Library.Exception.NotFoundException;
import Library.Exception.ValidationException;
import Library.Model.Book;
import Library.Model.AuthorProfile2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.Role;
import Library.Model.SubmissionState;
import Library.Model.User;
import Library.Repository.AuthorProfileRepository2;
import Library.Repository.BookRepository;
import Library.Repository.BookSubmissionRepository2;
import Library.Repository.BorrowRepository;
import Library.Repository.UserRepository;
import Library.Security.NamePolicy;
import Library.Security.PasswordHasher;
import Library.Security.PasswordPolicy;
import Library.Security.SessionManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
            "Biography", "Fantasy", "Mystery", "Romance", "Education",
            "Science Fiction", "Thriller", "Business", "Art", "Philosophy",
            "Self-Help", "Children", "Young Adult"
    );
        private static final Map<String, String> GENRE_LOOKUP = SUPPORTED_GENRES.stream()
            .collect(Collectors.toMap(
                AuthorService2::normalizeGenreLookupKey,
                genre -> genre
            ));

    // Repositories for user management, author profiles, and book submissions, injected via constructor for better testability and separation of concerns.
    private final UserRepository userRepository;
    private final AuthorProfileRepository2 authorProfileRepository;
    private final BookSubmissionRepository2 submissionRepository;
    private final BookRepository bookRepository;
    private final BorrowRepository borrowRepository;
    private final FileService fileService;

    // Constructor to initialize the service with required repositories, allowing for dependency injection and easier testing.
    public AuthorService2(UserRepository userRepository,
                         AuthorProfileRepository2 authorProfileRepository,
                         BookSubmissionRepository2 submissionRepository,
                         BookRepository bookRepository,
                         BorrowRepository borrowRepository,
                         FileService fileService) {
        this.userRepository = userRepository;
        this.authorProfileRepository = authorProfileRepository;
        this.submissionRepository = submissionRepository;
        this.bookRepository = bookRepository;
        this.borrowRepository = borrowRepository;
        this.fileService = fileService;
    }

    // Method to register a new author, validating input and ensuring unique usernames, while also creating an associated author profile.
    public User registerAuthor(String username, String fullName, String password, String bio) {
        String normalizedUsername = NamePolicy.validateUsername(username);
        String normalizedFullName = NamePolicy.validateFullName(fullName);
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
        if (!user.isActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact a librarian.");
        }
        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }
        user.markLoginNow();
        userRepository.save(user);
        SessionManager.getInstance().createSession(user);
        return user;
    }

    // Method to submit a new book for publication, validating input and ensuring the submitting user is an authenticated author before saving the submission.
    public BookSubmission2 publishBook(String authorUsername, String title, List<String> genres,
                                      String description, String fileName) {
        return publishBook(authorUsername, title, genres, description, fileName, "");
    }

    public BookSubmission2 publishBook(String authorUsername, String title, List<String> genres,
                                      String description, String fileName, String coverImagePath) {
        String normalizedAuthorUsername = normalizeRequired(authorUsername, "Author username cannot be empty.");
        String normalizedTitle = normalizeRequired(title, "Title cannot be empty.");
        String normalizedDescription = normalizeRequired(description, "Description cannot be empty.");
        String normalizedFileName = normalizeRequired(fileName, "Book file is required.");
        String normalizedCoverImagePath = coverImagePath == null ? "" : coverImagePath.trim();

        // Validate genres and file format before proceeding with submission creation.
        List<String> normalizedGenres = normalizeGenres(genres, "At least one genre is required.");
        validateFileFormat(normalizedFileName);

        // Ensure the author exists and has the AUTHOR role before allowing book submission, throwing an exception if validation fails.
        User author = userRepository.findByUsername(normalizedAuthorUsername)
                .orElseThrow(() -> new ValidationException("Author user not found."));
        if (author.getRole() != Role.AUTHOR) throw new ValidationException("User is not an author.");

        if (hasDuplicateOwnedTitle(normalizedAuthorUsername, normalizedTitle)) {
            throw new ValidationException("You already have a book or submission with this title.");
        }

        // Create and save the book submission, associating it with the author's username and full name for future reference and tracking.
        BookSubmission2 submission = new BookSubmission2(
            normalizedTitle,
            author.getUsername(),
            author.getFullName(),
            normalizedGenres,
            normalizedDescription,
            normalizedFileName,
            normalizedCoverImagePath
        );
        submissionRepository.save(submission);
        return submission;
    }

    private boolean hasDuplicateOwnedTitle(String authorUsername, String title) {
        String normalizedTitle = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        if (normalizedTitle.isEmpty()) {
            return false;
        }

        boolean duplicateSubmission = submissionRepository.findByAuthorUsername(authorUsername).stream()
                .anyMatch(submission -> submission.getTitle() != null
                        && submission.getTitle().trim().toLowerCase(Locale.ROOT).equals(normalizedTitle));
        if (duplicateSubmission) {
            return true;
        }

        return bookRepository.findAll().stream()
                .filter(Book::isApproved)
                .filter(book -> authorUsername.equals(book.getAuthorUsername()))
                .anyMatch(book -> book.getTitle() != null
                        && book.getTitle().trim().toLowerCase(Locale.ROOT).equals(normalizedTitle));
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

    public AuthorProfileSnapshot getAuthorProfile(String username) {
        String normalizedUsername = normalizeRequired(username, "Username cannot be empty.");
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new ValidationException("Author user not found."));
        if (user.getRole() != Role.AUTHOR) {
            throw new ValidationException("User is not an author.");
        }

        AuthorProfile2 profile = authorProfileRepository.findByUsername(normalizedUsername)
                .orElseGet(() -> {
                    AuthorProfile2 created = new AuthorProfile2(normalizedUsername, "");
                    authorProfileRepository.save(created);
                    return created;
                });

        return new AuthorProfileSnapshot(user.getUsername(), user.getFullName(), profile.getBio());
    }

    public AuthorProfileSnapshot updateAuthorProfile(String actingUsername,
                                                     String targetUsername,
                                                     String fullName,
                                                     String bio,
                                                     String newPassword) {
        return updateAuthorProfile(actingUsername, targetUsername, fullName, bio, newPassword, "", "");
    }

    public AuthorProfileSnapshot updateAuthorProfile(String actingUsername,
                                                     String targetUsername,
                                                     String fullName,
                                                     String bio,
                                                     String newPassword,
                                                     String currentPassword) {
        return updateAuthorProfile(actingUsername, targetUsername, fullName, bio, newPassword, currentPassword, "");
    }

    public AuthorProfileSnapshot updateAuthorProfile(String actingUsername,
                                                     String targetUsername,
                                                     String fullName,
                                                     String bio,
                                                     String newPassword,
                                                     String currentPassword,
                                                     String profilePhotoPath) {
        String normalizedActor = normalizeRequired(actingUsername, "Username cannot be empty.");
        String normalizedTarget = normalizeRequired(targetUsername, "Username cannot be empty.");
        if (!normalizedActor.equals(normalizedTarget)) {
            throw new ValidationException("Cannot update another author's profile.");
        }

        String normalizedFullName = NamePolicy.validateFullName(fullName);
        String normalizedBio = normalizeRequired(bio, "Bio cannot be empty.");
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new ValidationException("Current password is required to save profile changes.");
        }

        User user = userRepository.findByUsername(normalizedTarget)
                .orElseThrow(() -> new ValidationException("Author user not found."));
        if (user.getRole() != Role.AUTHOR) {
            throw new ValidationException("User is not an author.");
        }

        user.updateFullName(normalizedFullName);
        AuthService.validateCurrentPasswordForPasswordChange(user, newPassword, currentPassword);
        if (newPassword != null && !newPassword.isBlank()) {
            PasswordPolicy.validate(newPassword);
            user.updatePasswordHash(PasswordHasher.hashPassword(newPassword));
        }
        if (profilePhotoPath != null && !profilePhotoPath.isBlank()) {
            user.updateProfilePhotoPath(profilePhotoPath);
        }
        userRepository.save(user);

        AuthorProfile2 profile = new AuthorProfile2(user.getUsername(), normalizedBio);
        authorProfileRepository.save(profile);

        return new AuthorProfileSnapshot(user.getUsername(), user.getFullName(), profile.getBio());
    }

    public List<BookSubmission2> listSubmissionsByAuthor(String authorUsername) {
        String normalizedAuthorUsername = normalizeRequired(authorUsername, "Author username cannot be empty.");
        return submissionRepository.findByAuthorUsername(normalizedAuthorUsername).stream()
                .sorted(Comparator.comparing(BookSubmission2::getSubmittedDate, Comparator.reverseOrder())
                        .thenComparing(BookSubmission2::getId))
                .toList();
    }

    public List<Book> listPublishedBooksByAuthor(String authorUsername) {
        String normalizedAuthorUsername = normalizeRequired(authorUsername, "Author username cannot be empty.");
        return bookRepository.findAll().stream()
                .filter(Book::isApproved)
                .filter(book -> book.getAuthorUsername().equals(normalizedAuthorUsername))
                .sorted(Comparator.comparing(Book::getTitle).thenComparing(Book::getId))
                .toList();
    }

    public Book updateOwnedPublishedBook(String actingUsername,
                                         String bookId,
                                         String title,
                                         List<String> genres,
                                         String description) {
        Book existing = requireOwnedPublishedBook(actingUsername, bookId, "update");
        if (hasActiveBorrowForBook(existing.getId())) {
            throw new BusinessException(
                "Cannot modify a published book with active borrows. Wait until all copies are returned.");
        }
        String normalizedTitle = normalizeRequired(title, "Title cannot be empty.");
        List<String> normalizedGenres = normalizeGenres(genres, "At least one genre is required.");
        String normalizedDescription = normalizeRequired(description, "Description cannot be empty.");

        existing.updateMetadata(normalizedTitle, normalizedGenres, normalizedDescription);
        bookRepository.save(existing);
        return existing;
    }

    public void deleteOwnedPublishedBook(String actingUsername, String bookId) {
        Book existing = requireOwnedPublishedBook(actingUsername, bookId, "delete");
        if (hasActiveBorrowForBook(existing.getId())) {
            throw new BusinessException("Cannot delete a published book with active borrows.");
        }
        bookRepository.deleteById(existing.getId());
    }

    public FilePreview readOwnedSubmissionFilePreview(String actingUsername, String submissionId) {
        BookSubmission2 submission = requireOwnedSubmission(actingUsername, submissionId, "read");
        FileService.FilePreviewDetails preview = fileService.readPreviewDetails(submission.getFileName());
        return new FilePreview(
                submission.getId(),
                "submission",
                preview.absolutePath(),
                preview.sizeBytes(),
            preview.previewType(),
                preview.previewText()
        );
    }

    public FilePreview readOwnedPublishedBookFilePreview(String actingUsername, String bookId) {
        Book book = requireOwnedPublishedBook(actingUsername, bookId, "read");
        String filePath = normalizeRequired(book.getFilePath(), "Published book file path is not available.");
        FileService.FilePreviewDetails preview = fileService.readPreviewDetails(filePath);
        return new FilePreview(
                book.getId(),
                "published",
                preview.absolutePath(),
                preview.sizeBytes(),
            preview.previewType(),
                preview.previewText()
        );
    }

    public BookSubmission2 updatePendingSubmission(String actingUsername,
                                                   String submissionId,
                                                   String title,
                                                   List<String> genres,
                                                   String description,
                                                   String fileName) {
        BookSubmission2 existing = requireOwnedPendingSubmission(actingUsername, submissionId, "update", "updated");
        String normalizedTitle = normalizeRequired(title, "Title cannot be empty.");
        List<String> normalizedGenres = normalizeGenres(genres, "At least one genre is required.");
        String normalizedDescription = normalizeRequired(description, "Description cannot be empty.");

        String normalizedFileName = fileName == null || fileName.isBlank()
                ? existing.getFileName()
                : normalizeRequired(fileName, "Book file is required.");
        validateFileFormat(normalizedFileName);

        BookSubmission2 updated = new BookSubmission2(
                existing.getId(),
                normalizedTitle,
                existing.getAuthorUsername(),
                existing.getAuthorFullName(),
                normalizedGenres,
                normalizedDescription,
                normalizedFileName,
            existing.getCoverImagePath(),
                existing.getSubmittedDate()
        );
        submissionRepository.save(updated);
        return updated;
    }

    public void deletePendingSubmission(String actingUsername, String submissionId) {
        BookSubmission2 existing = requireOwnedPendingSubmission(actingUsername, submissionId, "delete", "deleted");
        submissionRepository.deleteById(existing.getId());
    }

    public BulkDeleteResult bulkDeletePendingSubmissions(String actingUsername, List<String> submissionIds) {
        List<String> deleted = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        if (submissionIds == null) {
            return new BulkDeleteResult(deleted, skipped);
        }
        for (String submissionId : submissionIds) {
            if (submissionId == null || submissionId.isBlank()) {
                continue;
            }
            try {
                deletePendingSubmission(actingUsername, submissionId);
                deleted.add(submissionId);
            } catch (RuntimeException e) {
                skipped.put(submissionId, e.getMessage() == null ? "unknown error" : e.getMessage());
            }
        }
        return new BulkDeleteResult(deleted, skipped);
    }

    public BulkDeleteResult bulkDeleteOwnedPublishedBooks(String actingUsername, List<String> bookIds) {
        List<String> deleted = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        if (bookIds == null) {
            return new BulkDeleteResult(deleted, skipped);
        }
        for (String bookId : bookIds) {
            if (bookId == null || bookId.isBlank()) {
                continue;
            }
            try {
                deleteOwnedPublishedBook(actingUsername, bookId);
                deleted.add(bookId);
            } catch (RuntimeException e) {
                skipped.put(bookId, e.getMessage() == null ? "unknown error" : e.getMessage());
            }
        }
        return new BulkDeleteResult(deleted, skipped);
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

            String canonicalGenre = GENRE_LOOKUP.get(normalizeGenreLookupKey(genre));
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

    private static String normalizeGenreLookupKey(String genre) {
        return genre == null
                ? ""
                : genre.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // Private helper method to validate the file format of the submitted book, ensuring that only allowed formats are accepted and throwing a ValidationException if the format is unsupported.
    private void validateFileFormat(String fileName) {
        String lower = fileName.toLowerCase();
        if (!(lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"))) {
            throw new ValidationException("Unsupported file format. Allowed: pdf, txt, doc, docx, jpg, jpeg, png.");
        }
    }

    // Private helper method to validate basic input fields such as username and full name, ensuring they are not null or blank before proceeding with registration or other operations.
    private String normalizeRequired(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(errorMessage);
        }
        return value.trim();
    }

    private BookSubmission2 requireOwnedPendingSubmission(String actingUsername,
                                                          String submissionId,
                                                          String ownershipVerb,
                                                          String pendingVerb) {
        BookSubmission2 existing = requireOwnedSubmission(actingUsername, submissionId, ownershipVerb);
        if (existing.getStatus() != SubmissionState.PENDING) {
            throw new ValidationException("Only pending submissions can be " + pendingVerb + ".");
        }
        return existing;
    }

    private BookSubmission2 requireOwnedSubmission(String actingUsername,
                                                   String submissionId,
                                                   String ownershipVerb) {
        String normalizedActor = normalizeRequired(actingUsername, "Author username cannot be empty.");
        String normalizedSubmissionId = normalizeRequired(submissionId, "Submission ID cannot be empty.");

        BookSubmission2 existing = submissionRepository.findById(normalizedSubmissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found."));
        if (!existing.getAuthorUsername().equals(normalizedActor)) {
            throw new ValidationException("Cannot " + ownershipVerb + " another author's submission.");
        }
        return existing;
    }

    private Book requireOwnedPublishedBook(String actingUsername, String bookId, String actionVerb) {
        String normalizedActor = normalizeRequired(actingUsername, "Author username cannot be empty.");
        String normalizedBookId = normalizeRequired(bookId, "Book ID cannot be empty.");

        Book existing = bookRepository.findById(normalizedBookId)
                .orElseThrow(() -> new NotFoundException("Published book not found."));

        if (!existing.isApproved()) {
            throw new ValidationException("Only approved books can be managed here.");
        }
        if (!existing.getAuthorUsername().equals(normalizedActor)) {
            throw new ValidationException("Cannot " + actionVerb + " another author's published book.");
        }
        return existing;
    }

    private boolean hasActiveBorrowForBook(String bookId) {
        for (BorrowRecord record : borrowRepository.findAll()) {
            if (record.getBookId().equals(bookId) && !record.isReturned()) {
                return true;
            }
        }
        return false;
    }

    public record AuthorProfileSnapshot(String username, String fullName, String bio) {
    }

    public record BulkDeleteResult(List<String> deletedIds,
                                   Map<String, String> skippedIdToReason) {
    }

    public record FilePreview(String itemId,
                              String sourceType,
                              String filePath,
                              long sizeBytes,
                              String previewType,
                              String previewText) {
    }
}
