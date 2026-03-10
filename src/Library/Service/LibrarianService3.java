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

import java.time.LocalDate;
import java.util.List;

public class LibrarianService3 {
    private final UserRepository userRepository;
    private final LibrarianProfileRepository3 librarianProfileRepository;
    private final BookSubmissionRepository2 submissionRepository;
    private final BookRepository bookRepository;

    public LibrarianService3 (UserRepository userRepository,
                            LibrarianProfileRepository3 librarianProfileRepository,
                            BookSubmissionRepository2 submissionRepository,
                            BookRepository bookRepository) {
        this.userRepository = userRepository;
        this.librarianProfileRepository = librarianProfileRepository;
        this.submissionRepository = submissionRepository;
        this.bookRepository = bookRepository;
    }

    public User registerLibrarian(String username, String fullName, String password, String employeeId) {
        if (username == null || username.isBlank()) throw new ValidationException("Username cannot be empty.");
        if (fullName == null || fullName.isBlank()) throw new ValidationException("Full Name cannot be empty.");
        PasswordPolicy.validate(password);

        if (userRepository.existsByUsername(username)) {
            throw new ValidationException("Username already exists.");
        }

        User user = new User(username, fullName, PasswordHasher.sha256(password), Role.LIBRARIAN);
        userRepository.save(user);
        librarianProfileRepository.save(new LibrarianProfile3(username, employeeId == null ? "" : employeeId));
        return user;
    }

    public User loginLibrarian(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));
        if (user.getRole() != Role.LIBRARIAN) {
            throw new AuthenticationException("This username does not belong to LIBRARIAN account.");
        }
        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }
        return user;
    }

    public List<BookSubmission2> getPendingSubmissions() {
        return submissionRepository.findByStatus(SubmissionState.PENDING);
    }

    public void approveSubmission(String submissionId, String comment) {
        BookSubmission2 s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found."));
        if (s.getStatus() != SubmissionState.PENDING) {
            throw new ValidationException("Only pending submissions can be approved.");
        }

        s.approve(comment == null ? "Approved" : comment);
        submissionRepository.save(s);

        // Convert submission to published/approved book for Task 1.3 listing
        Book book = new Book(s.getTitle(), s.getAuthorFullName(), s.getDescription());
        book.approve(LocalDate.now());
        bookRepository.save(book);
    }

    public void rejectSubmission(String submissionId, String comment) {
        BookSubmission2 s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found."));
        if (s.getStatus() != SubmissionState.PENDING) {
            throw new ValidationException("Only pending submissions can be rejected.");
        }

        s.reject(comment == null ? "Rejected" : comment);
        submissionRepository.save(s);
    }

    public void bulkApprove(List<String> submissionIds, String comment) {
        for (String id : submissionIds) approveSubmission(id, comment);
    }

    public void bulkReject(List<String> submissionIds, String comment) {
        for (String id : submissionIds) rejectSubmission(id, comment);
    }
}