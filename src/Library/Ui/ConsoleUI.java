package Library.Ui;

import Library.Exception.AuthenticationException;
import Library.Exception.BusinessException;
import Library.Exception.ValidationException;
import Library.Model.*;
import Library.Service.AuthService;
import Library.Service.BookService;
import Library.Service.BorrowService;
import Library.Service.RecommendationService;

import java.util.List;
import java.util.Scanner;

public class ConsoleUI {
    private final AuthService authService;
    private final BookService bookService;
    private final BorrowService borrowService;
    private final RecommendationService recommendationService;
    private User currentUser;

    public ConsoleUI(AuthService authService, BookService bookService, BorrowService borrowService,
                     RecommendationService recommendationService) {
        this.authService = authService;
        this.bookService = bookService;
        this.borrowService = borrowService;
        this.recommendationService = recommendationService;
    }

    public void start() {
        start(new Scanner(System.in));
    }

    public void start(Scanner sc) {
        boolean running = true;

        while (running) {
            System.out.println("\n=== COMP3111 Task 1 Portal ===");
            System.out.println("1. Register (Student/Staff)");
            System.out.println("2. Login (Student/Staff)");
            System.out.println("3. View Available Books");
            System.out.println("4. Borrow Book");
            System.out.println("0. Exit");
            System.out.print("Choose: ");

            String choice = sc.nextLine();

            try {
                switch (choice) {
                    case "1" -> register(sc);
                    case "2" -> login(sc);
                    case "3" -> listBooks();
                    case "4" -> borrow(sc);
                    case "0" -> running = false;
                    default -> System.out.println("Invalid choice.");
                }
            } catch (ValidationException | AuthenticationException | BusinessException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        System.out.println("Bye.");
    }

    private void register(Scanner sc) {
        System.out.print("Username: ");
        String username = sc.nextLine();
        System.out.print("Full Name: ");
        String fullName = sc.nextLine();
        System.out.print("Password: ");
        String password = sc.nextLine();
        System.out.print("Role (STUDENT/STAFF): ");
        Role role = Role.valueOf(sc.nextLine().trim().toUpperCase());

        authService.registerStudentOrStaff(username, fullName, password, role);
        System.out.println("Registration successful.");
        System.out.println("Redirecting to login...");
        login(sc);
    }

    private void login(Scanner sc) {
        System.out.print("Username: ");
        String username = sc.nextLine();
        System.out.print("Password: ");
        String password = sc.nextLine();
        System.out.print("Login as (STUDENT/STAFF): ");
        Role role = Role.valueOf(sc.nextLine().trim().toUpperCase());

        currentUser = authService.loginStudentOrStaff(username, password, role);
        System.out.println("Login successful. Welcome " + currentUser.getFullName());
        System.out.println("Redirecting to available books...");
        listBooks();
    }

    private void listBooks() {
        List<Book> books = bookService.listApprovedBooksWithAvailability();
        if (books.isEmpty()) {
            System.out.println("No approved books available.");
            return;
        }

        System.out.println("=== Available Books ===");
        for (Book b : books) {
            EnhancementHelper.printAvailability(b);
            System.out.printf("ID=%s | %s | %s | %s%n",
                    b.getId(), b.getTitle(), b.getAuthorFullName(), b.getPublishDate());
            EnhancementHelper.quickReadSummary(b);
            System.out.println();
        }

        List<Book> recommendations = recommendationService.recommendTopPopular(3);
        if (!recommendations.isEmpty()) {
            System.out.println("Recommended / Popular Titles:");
            for (Book recommendation : recommendations) {
                System.out.printf("- %s by %s%n", recommendation.getTitle(), recommendation.getAuthorFullName());
            }
        }
    }

    private void borrow(Scanner sc) {
        if (currentUser == null) {
            System.out.println("Please login first.");
            return;
        }
        System.out.print("Enter Book ID to borrow: ");
        String bookId = sc.nextLine();
        System.out.print("Borrow duration in days (default 14): ");
        String durationInput = sc.nextLine().trim();
        int durationDays;
        try {
            durationDays = durationInput.isEmpty() ? 14 : Integer.parseInt(durationInput);
        } catch (NumberFormatException e) {
            throw new ValidationException("Borrow duration must be a valid number of days.");
        }

        Book selectedBook = bookService.listApprovedBooksWithAvailability().stream()
                .filter(book -> book.getId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Book not found in approved catalog."));
        EnhancementHelper.confirmBorrow(selectedBook.getTitle(), durationDays);
        System.out.print("Proceed with borrow? (Y/N): ");
        if (!"Y".equalsIgnoreCase(sc.nextLine().trim())) {
            System.out.println("Borrow cancelled.");
            return;
        }

        BorrowRecord record = borrowService.borrowBook(currentUser.getUsername(), bookId, durationDays);
        EnhancementHelper.printBorrowResult(record);
    }
}
