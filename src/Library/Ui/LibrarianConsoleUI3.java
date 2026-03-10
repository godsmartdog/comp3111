package Library.Ui;

import Library.Exception.AuthenticationException;
import Library.Exception.ValidationException;
import Library.Model.BookSubmission2;
import Library.Model.User;
import Library.Service.LibrarianService3;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class LibrarianConsoleUI3 {
    private final LibrarianService3 librarianService;
    private User currentLibrarian;

    public LibrarianConsoleUI3(LibrarianService3 librarianService) {
        this.librarianService = librarianService;
    }

    public void start(Scanner sc) {
        boolean running = true;
        while (running) {
            System.out.println("\n=== Librarian Portal ===");
            System.out.println("1. Register Librarian");
            System.out.println("2. Login Librarian");
            System.out.println("3. View Pending Submissions");
            System.out.println("4. Approve Submission");
            System.out.println("5. Reject Submission");
            System.out.println("6. Bulk Approve");
            System.out.println("7. Bulk Reject");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String c = sc.nextLine();

            try {
                switch (c) {
                    case "1" -> register(sc);
                    case "2" -> login(sc);
                    case "3" -> listPending();
                    case "4" -> approve(sc);
                    case "5" -> reject(sc);
                    case "6" -> bulkApprove(sc);
                    case "7" -> bulkReject(sc);
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
        System.out.print("Employee ID (optional): ");
        String employeeId = sc.nextLine();

        librarianService.registerLibrarian(username, fullName, password, employeeId);
        System.out.println("Librarian registration successful.");
    }

    private void login(Scanner sc) {
        System.out.print("Username: ");
        String username = sc.nextLine();
        System.out.print("Password: ");
        String password = sc.nextLine();

        currentLibrarian = librarianService.loginLibrarian(username, password);
        System.out.println("Librarian login successful. Welcome " + currentLibrarian.getFullName());
    }

    private void listPending() {
        ensureLogin();
        List<BookSubmission2> list = librarianService.getPendingSubmissions();
        if (list.isEmpty()) {
            System.out.println("No pending submissions.");
            return;
        }

        System.out.println("=== Pending Submissions ===");
        for (BookSubmission2 s : list) {
            System.out.printf("ID=%s | Title=%s | AuthorUsername=%s | AuthorFullName=%s | Genre=%s | Submitted=%s | Status=%s | File=%s%n",
                    s.getId(), s.getTitle(), s.getAuthorUsername(), s.getAuthorFullName(),
                    s.getGenres(), s.getSubmittedDate(), s.getStatus(), s.getFileName());
        }
    }

    private void approve(Scanner sc) {
        ensureLogin();
        System.out.print("Submission ID: ");
        String id = sc.nextLine();
        System.out.print("Comment: ");
        String comment = sc.nextLine();
        librarianService.approveSubmission(id, comment);
        System.out.println("Approved.");
    }

    private void reject(Scanner sc) {
        ensureLogin();
        System.out.print("Submission ID: ");
        String id = sc.nextLine();
        System.out.print("Comment: ");
        String comment = sc.nextLine();
        librarianService.rejectSubmission(id, comment);
        System.out.println("Rejected.");
    }

    private void bulkApprove(Scanner sc) {
        ensureLogin();
        System.out.print("Submission IDs (comma separated): ");
        List<String> ids = Arrays.stream(sc.nextLine().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        System.out.print("Comment: ");
        String comment = sc.nextLine();
        librarianService.bulkApprove(ids, comment);
        System.out.println("Bulk approve done.");
    }

    private void bulkReject(Scanner sc) {
        ensureLogin();
        System.out.print("Submission IDs (comma separated): ");
        List<String> ids = Arrays.stream(sc.nextLine().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        System.out.print("Comment: ");
        String comment = sc.nextLine();
        librarianService.bulkReject(ids, comment);
        System.out.println("Bulk reject done.");
    }

    private void ensureLogin() {
        if (currentLibrarian == null) {
            throw new ValidationException("Please login as librarian first.");
        }
    }
}
