package Library.Service;

import Library.Exception.AuthenticationException;
import Library.Exception.NotFoundException;
import Library.Exception.ValidationException;
import Library.Model.*;
import Library.Repository.BookRepository;
import Library.Repository.BookSubmissionRepository2;
import Library.Repository.LibrarianProfileRepository3;
import Library.Repository.UserRepository;
import Library.Security.PasswordHasher;
import Library.Security.PasswordPolicy;
import Library.Security.SessionManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

// Service class to handle librarian-related operations such as registration, login, and managing book submissions, including approving or rejecting submissions and converting approved submissions into published books.
public class LibrarianService3 {
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
        if (username == null || username.isBlank()) throw new ValidationException("Username cannot be empty.");
        if (fullName == null || fullName.isBlank()) throw new ValidationException("Full Name cannot be empty.");
        PasswordPolicy.validate(password);

        if (userRepository.existsByUsername(username)) {
            throw new ValidationException("Username already exists.");
        }

        // Create and save the new user with the LIBRARIAN role, and also create an associated librarian profile with the provided employee ID, ensuring that the librarian's credentials and profile information are properly stored in the system.
        User user = new User(username, fullName, PasswordHasher.hashPassword(password), Role.LIBRARIAN);
        userRepository.save(user);
        librarianProfileRepository.save(new LibrarianProfile3(username, employeeId == null ? "" : employeeId));
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
                                                           String newPassword) {
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

        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("Full Name cannot be empty.");
        }
        if (employeeId == null || employeeId.isBlank()) {
            throw new ValidationException("Employee ID cannot be empty.");
        }

        User user = userRepository.findByUsername(normalizedTarget)
                .orElseThrow(() -> new ValidationException("Librarian user not found."));
        if (user.getRole() != Role.LIBRARIAN) {
            throw new ValidationException("User is not a librarian.");
        }

        user.updateFullName(fullName.trim());
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
        Book book = new Book(s.getTitle(), s.getAuthorUsername(), s.getAuthorFullName(), s.getDescription());
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
        BookSubmission2 s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found."));
        if (s.getStatus() != SubmissionState.PENDING) {
            throw new ValidationException("Only pending submissions can be rejected.");
        }

        s.reject(comment == null ? "Rejected" : comment);
        submissionRepository.save(s);
    }

    // Method to bulk approve multiple book submissions, iterating through the list of submission IDs and calling the approveSubmission method for each ID, which allows librarians to efficiently manage and approve multiple submissions at once.
    public void bulkApprove(List<String> submissionIds, String comment) {
        for (String id : submissionIds) approveSubmission(id, comment);
    }

    // Method to bulk reject multiple book submissions, iterating through the list of submission IDs and calling the rejectSubmission method for each ID, which allows librarians to efficiently manage and reject multiple submissions at once.
    public void bulkReject(List<String> submissionIds, String comment) {
        for (String id : submissionIds) rejectSubmission(id, comment);
    }

    public record LibrarianProfileSnapshot(String username, String fullName, String employeeId) {
    }
}
