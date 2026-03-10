package Library.Ui;

import Library.Exception.AuthenticationException;
import Library.Exception.ValidationException;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.User;
import Library.Service.AuthorDraftService2;
import Library.Service.AuthorService2;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class AuthorConsoleUI2 {
    private final AuthorService2 authorService;
    private final AuthorDraftService2 draftService;
    private User currentAuthor;

    public AuthorConsoleUI2(AuthorService2 authorService, AuthorDraftService2 draftService) {
        this.authorService = authorService;
        this.draftService = draftService;
    }

    public void start(Scanner sc) {
        boolean running = true;
        while (running) {
            System.out.println("\n=== Author Portal ===");
            System.out.println("1. Register Author");
            System.out.println("2. Login Author");
            System.out.println("3. Load Draft");
            System.out.println("4. Publish New Book");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String c = sc.nextLine();

            try {
                switch (c) {
                    case "1" -> register(sc);
                    case "2" -> login(sc);
                    case "3" -> loadDraft();
                    case "4" -> publish(sc);
                    case "0" -> running = false;
                    default -> System.out.println("Invalid choice.");
                }
            } catch (ValidationException | AuthenticationException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void register(Scanner sc) {
        System.out.print("Username: ");
        String username = sc.nextLine();
        System.out.print("Full Name: ");
        String fullName = sc.nextLine();
        System.out.print("Password: ");
        String password = sc.nextLine();
        System.out.print("Bio (optional): ");
        String bio = sc.nextLine();

        authorService.registerAuthor(username, fullName, password, bio);
        System.out.println("Author registration successful.");
    }

    private void login(Scanner sc) {
        System.out.print("Username: ");
        String username = sc.nextLine();
        System.out.print("Password: ");
        String password = sc.nextLine();

        currentAuthor = authorService.loginAuthor(username, password);
        System.out.println("Author login successful. Welcome " + currentAuthor.getFullName());
    }

    private void loadDraft() {
        if (currentAuthor == null) {
            System.out.println("Please login first.");
            return;
        }
        BookDraft2 draft = draftService.loadDraft(currentAuthor.getUsername()).orElse(null);
        if (draft == null) {
            System.out.println("No draft found.");
        } else {
            System.out.println("Draft loaded. Last saved: " + draft.getLastSavedAt());
            System.out.println("Title: " + draft.getTitle());
            System.out.println("Genres: " + draft.getGenres());
        }
    }

    private void publish(Scanner sc) {
        if (currentAuthor == null) {
            System.out.println("Please login first.");
            return;
        }

        System.out.println("Supported genres: " + authorService.getSupportedGenres());

        System.out.print("Title: ");
        String title = sc.nextLine();

        System.out.print("Genres (comma separated): ");
        List<String> genres = Arrays.stream(sc.nextLine().split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();

        System.out.print("Description: ");
        String description = sc.nextLine();

        System.out.print("Book file name/path (e.g. mybook.pdf): ");
        String fileName = sc.nextLine();

        // Auto-save draft
        draftService.autoSave(currentAuthor.getUsername(), title, genres, description, fileName);

        // Preview
        System.out.println(authorService.previewBook(title, genres, description));

        System.out.print("Submit now? (Y/N): ");
        String confirm = sc.nextLine();
        if (!"Y".equalsIgnoreCase(confirm)) {
            System.out.println("Saved as draft. Not submitted.");
            return;
        }

        BookSubmission2 s = authorService.publishBook(
                currentAuthor.getUsername(), title, genres, description, fileName
        );
        draftService.clearDraft(currentAuthor.getUsername());
        System.out.println("Submission sent to librarian. Submission ID: " + s.getId());
    }
}
