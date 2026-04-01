package Library.Service;

import Library.Exception.AuthenticationException;
import Library.Exception.NotFoundException;
import Library.Exception.ValidationException;
import Library.Model.*;
import Library.Repository.BookRepository;
import Library.Repository.BookSubmissionRepository2;
import Library.Repository.LibrarianProfileRepository3;
import Library.Repository.UserRepository;
import Library.Security.NamePolicy;
import Library.Security.PasswordHasher;
import Library.Security.PasswordPolicy;
import Library.Security.SessionManager;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// Service class to handle librarian-related operations such as registration, login, and managing book submissions, including approving or rejecting submissions and converting approved submissions into published books.
public class LibrarianService3 {
    private static final int MAX_REJECTION_REASON_LENGTH = 500;

    private final UserRepository userRepository;
    private final LibrarianProfileRepository3 librarianProfileRepository;
    private final BookSubmissionRepository2 submissionRepository;
    private final BookRepository bookRepository;

    // Constructor to initialize the LibrarianService with the required repositories for user management, librarian profiles, book submissions, and books, allowing for dependency injection and better separation of concerns.
    public LibrarianService3 (UserRepository userRepository,
                            LibrarianProfileRepository3 librarianProfileRepository,
                            BookSubmissionRepository2 submissionRepository,
                            BookRepository bookRepository) {
        this.userRepository = userRepository;
        this.librarianProfileRepository = librarianProfileRepository;
        this.submissionRepository = submissionRepository;
        this.bookRepository = bookRepository;
    }

    // Method to register a new librarian, validating input and ensuring unique usernames, while also creating an associated librarian profile with the provided employee ID.
    public User registerLibrarian(String username, String fullName, String password, String employeeId) {
        String normalizedUsername = NamePolicy.validateUsername(username);
        String normalizedFullName = NamePolicy.validateFullName(fullName);
        PasswordPolicy.validate(password);

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ValidationException("Username already exists.");
        }

        // Create and save the new user with the LIBRARIAN role, and also create an associated librarian profile with the provided employee ID, ensuring that the librarian's credentials and profile information are properly stored in the system.
        User user = new User(normalizedUsername, normalizedFullName, PasswordHasher.hashPassword(password), Role.LIBRARIAN);
        userRepository.save(user);
        librarianProfileRepository.save(new LibrarianProfile3(normalizedUsername, employeeId == null ? "" : employeeId));
        return user;
    }

    // Method to authenticate a librarian, verifying credentials and ensuring the user has the LIBRARIAN role before creating a session for the authenticated user, which allows librarians to access their functionalities within the system.
    public User loginLibrarian(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));
        if (user.getRole() != Role.LIBRARIAN) {
            throw new AuthenticationException("This username does not belong to LIBRARIAN account.");
        }
        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }
        SessionManager.getInstance().createSession(user);
        return user;
    }

    public LibrarianProfileSnapshot getLibrarianProfile(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Username cannot be empty.");
        }

        String normalizedUsername = username.trim();
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new ValidationException("Librarian user not found."));
        if (user.getRole() != Role.LIBRARIAN) {
            throw new ValidationException("User is not a librarian.");
        }

        LibrarianProfile3 profile = librarianProfileRepository.findByUsername(normalizedUsername)
                .orElseGet(() -> {
                    LibrarianProfile3 created = new LibrarianProfile3(normalizedUsername, "");
                    librarianProfileRepository.save(created);
                    return created;
                });

        return new LibrarianProfileSnapshot(user.getUsername(), user.getFullName(), profile.getEmployeeId());
    }

    public LibrarianProfileSnapshot updateLibrarianProfile(String actingUsername,
                                                           String targetUsername,
                                                           String fullName,
                                                           String employeeId,
                                                           String newPassword,
                                                           String currentPassword) {
        if (actingUsername == null || actingUsername.isBlank()) {
            throw new ValidationException("Username cannot be empty.");
        }
        if (targetUsername == null || targetUsername.isBlank()) {
            throw new ValidationException("Username cannot be empty.");
        }

        String normalizedActor = actingUsername.trim();
        String normalizedTarget = targetUsername.trim();
        if (!normalizedActor.equals(normalizedTarget)) {
            throw new ValidationException("Cannot update another librarian's profile.");
        }

        String normalizedFullName = NamePolicy.validateFullName(fullName);
        if (employeeId == null || employeeId.isBlank()) {
            throw new ValidationException("Employee ID cannot be empty.");
        }

        User user = userRepository.findByUsername(normalizedTarget)
                .orElseThrow(() -> new ValidationException("Librarian user not found."));
        if (user.getRole() != Role.LIBRARIAN) {
            throw new ValidationException("User is not a librarian.");
        }

        user.updateFullName(normalizedFullName);
        AuthService.validateCurrentPasswordForPasswordChange(user, newPassword, currentPassword);
        if (newPassword != null && !newPassword.isBlank()) {
            PasswordPolicy.validate(newPassword);
            user.updatePasswordHash(PasswordHasher.hashPassword(newPassword));
        }
        userRepository.save(user);

        LibrarianProfile3 profile = new LibrarianProfile3(user.getUsername(), employeeId.trim());
        librarianProfileRepository.save(profile);

        return new LibrarianProfileSnapshot(user.getUsername(), user.getFullName(), profile.getEmployeeId());
    }

    // Method to retrieve a list of pending book submissions, allowing librarians to view and manage submissions that are awaiting approval or rejection, which is essential for maintaining the quality and relevance of the library's collection.
    public List<BookSubmission2> getPendingSubmissions() {
        return submissionRepository.findByStatus(SubmissionState.PENDING);
    }

    public List<BookSubmission2> querySubmissionsForReview(String keyword,
                                                           String status,
                                                           String sortBy,
                                                           String sortDir) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null || status.isBlank() ? "pending" : status.trim().toLowerCase(Locale.ROOT);
        String normalizedSortBy = sortBy == null ? "" : sortBy.trim();
        String normalizedSortDir = sortDir == null || sortDir.isBlank() ? "asc" : sortDir.trim().toLowerCase(Locale.ROOT);

        List<BookSubmission2> items = submissionRepository.findAll().stream()
                .filter(submission -> matchesSubmissionStatus(submission, normalizedStatus))
                .filter(submission -> matchesSubmissionKeyword(submission, normalizedKeyword))
                .toList();

        if (!"submittedDate".equalsIgnoreCase(normalizedSortBy)) {
            return items;
        }

        Comparator<BookSubmission2> comparator = Comparator
                .comparing(BookSubmission2::getSubmittedDate)
                .thenComparing(BookSubmission2::getId);
        if ("desc".equals(normalizedSortDir)) {
            comparator = comparator.reversed();
        }
        return items.stream().sorted(comparator).toList();
    }

    private boolean matchesSubmissionKeyword(BookSubmission2 submission, String normalizedKeyword) {
        if (normalizedKeyword.isEmpty()) {
            return true;
        }

        String title = submission.getTitle() == null ? "" : submission.getTitle().toLowerCase(Locale.ROOT);
        String author = submission.getAuthorFullName() == null ? "" : submission.getAuthorFullName().toLowerCase(Locale.ROOT);
        String authorUsername = submission.getAuthorUsername() == null ? "" : submission.getAuthorUsername().toLowerCase(Locale.ROOT);
        return title.contains(normalizedKeyword)
                || author.contains(normalizedKeyword)
                || authorUsername.contains(normalizedKeyword);
    }

    private boolean matchesSubmissionStatus(BookSubmission2 submission, String normalizedStatus) {
        if ("all".equals(normalizedStatus)) {
            return true;
        }
        if ("pending".equals(normalizedStatus)) {
            return submission.getStatus() == SubmissionState.PENDING;
        }
        if ("approved".equals(normalizedStatus)) {
            return submission.getStatus() == SubmissionState.APPROVED;
        }
        if ("rejected".equals(normalizedStatus)) {
            return submission.getStatus() == SubmissionState.REJECTED;
        }
        return false;
    }

    // Method to approve a book submission, validating the submission's existence and status before marking it as approved, saving the updated submission, and converting it into a published book in the system, which allows approved submissions to become part of the library's collection.
    public void approveSubmission(String submissionId, String comment) {
        BookSubmission2 s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found."));
        if (s.getStatus() != SubmissionState.PENDING) {
            throw new ValidationException("Only pending submissions can be approved.");
        }

        // Mark the submission as approved with an optional comment, save the updated submission, and then convert the approved submission into a published book by creating a new Book object with the relevant details from the submission and saving it to the book repository, which allows the approved book to be listed and available for borrowing in the library.
        s.approve(comment == null ? "Approved" : comment);
        submissionRepository.save(s);

        // Convert submission to published/approved book for Task 1.3 listing
        Book book = new Book(s.getTitle(), s.getAuthorUsername(), s.getAuthorFullName(), s.getGenres(), s.getDescription());
        book.setFileMetadata(s.getFileName(), detectContentType(s.getFileName()));
        book.approve(LocalDate.now());
        bookRepository.save(book);
    }

    private static String detectContentType(String fileName) {
        String value = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (value.endsWith(".txt") || value.endsWith(".md")) {
            return "text/plain";
        }
        if (value.endsWith(".doc") || value.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (value.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    // Method to reject a book submission, validating the submission's existence and status before marking it as rejected and saving the updated submission, which allows librarians to manage submissions that do not meet the library's standards or requirements.
    public void rejectSubmission(String submissionId, String comment) {
        rejectSubmission(submissionId, comment, comment);
    }

    public BookSubmission2 rejectSubmission(String submissionId, String comment, String rejectionReason) {
        BookSubmission2 s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found."));
        if (s.getStatus() != SubmissionState.PENDING) {
            throw new ValidationException("Only pending submissions can be rejected.");
        }

        String safeReason = normalizeRejectionReason(rejectionReason);
        s.reject(comment == null ? "Rejected" : comment, safeReason);
        submissionRepository.save(s);
        return s;
    }

    // Method to bulk approve multiple book submissions, iterating through the list of submission IDs and calling the approveSubmission method for each ID, which allows librarians to efficiently manage and approve multiple submissions at once.
    public void bulkApprove(List<String> submissionIds, String comment) {
        for (String id : submissionIds) approveSubmission(id, comment);
    }

    // Method to bulk reject multiple book submissions, iterating through the list of submission IDs and calling the rejectSubmission method for each ID, which allows librarians to efficiently manage and reject multiple submissions at once.
    public void bulkReject(List<String> submissionIds, String comment) {
        for (String id : submissionIds) rejectSubmission(id, comment);
    }

    private static String normalizeRejectionReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (value.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new ValidationException("Rejection reason must be at most " + MAX_REJECTION_REASON_LENGTH + " characters.");
        }
        return value;
    }

    public record LibrarianProfileSnapshot(String username, String fullName, String employeeId) {
    }
}
