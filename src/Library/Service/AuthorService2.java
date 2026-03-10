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

import java.util.List;

public class AuthorService2 {
    private final UserRepository userRepository;
    private final AuthorProfileRepository2 authorProfileRepository;
    private final BookSubmissionRepository2 submissionRepository;

    public AuthorService2(UserRepository userRepository,
                         AuthorProfileRepository2 authorProfileRepository,
                         BookSubmissionRepository2 submissionRepository) {
        this.userRepository = userRepository;
        this.authorProfileRepository = authorProfileRepository;
        this.submissionRepository = submissionRepository;
    }

    public User registerAuthor(String username, String fullName, String password, String bio) {
        validateBasic(username, fullName);
        PasswordPolicy.validate(password);

        if (userRepository.existsByUsername(username)) {
            throw new ValidationException("Username already exists.");
        }

        User user = new User(username, fullName, PasswordHasher.sha256(password), Role.AUTHOR);
        userRepository.save(user);
        authorProfileRepository.save(new AuthorProfile2(username, bio == null ? "" : bio));
        return user;
    }

    public User loginAuthor(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));
        if (user.getRole() != Role.AUTHOR) {
            throw new AuthenticationException("This username does not belong to AUTHOR account.");
        }
        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }
        return user;
    }

    public BookSubmission2 publishBook(String authorUsername, String title, List<String> genres,
                                      String description, String fileName) {
        if (title == null || title.isBlank()) throw new ValidationException("Title cannot be empty.");
        if (genres == null || genres.isEmpty()) throw new ValidationException("At least one genre is required.");
        if (description == null || description.isBlank()) throw new ValidationException("Description cannot be empty.");
        if (fileName == null || fileName.isBlank()) throw new ValidationException("Book file is required.");

        User author = userRepository.findByUsername(authorUsername)
                .orElseThrow(() -> new ValidationException("Author user not found."));
        if (author.getRole() != Role.AUTHOR) throw new ValidationException("User is not an author.");

        BookSubmission2 submission = new BookSubmission2(
                title, author.getUsername(), author.getFullName(), genres, description, fileName
        );
        submissionRepository.save(submission);
        return submission;
    }

    private void validateBasic(String username, String fullName) {
        if (username == null || username.isBlank()) throw new ValidationException("Username cannot be empty.");
        if (fullName == null || fullName.isBlank()) throw new ValidationException("Full Name cannot be empty.");
    }

     public String previewBook(String title, List<String> genres, String description) {
        return "=== Preview ===\nTitle: " + title + "\nGenres: " + genres + "\nDescription: " + description;
    }

    public List<String> getSupportedGenres() {
        return List.of("Fiction", "Non-Fiction", "Education", "Science", "Technology", "History", "Fantasy");
    }
}
