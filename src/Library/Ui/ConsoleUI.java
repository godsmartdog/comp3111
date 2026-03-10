package Library.Ui;

import Library.Exception.AuthenticationException;
import Library.Exception.BusinessException;
import Library.Exception.ValidationException;
import Library.Model.*;
import Library.Service.AuthService;
import Library.Service.BookService;
import Library.Service.BorrowService;

import java.util.List;
import java.util.Scanner;

public class ConsoleUI {
    private final AuthService authService;
    private final BookService bookService;
    private final BorrowService borrowService;
    private User currentUser;

    public ConsoleUI(AuthService authService, BookService bookService, BorrowService borrowService) {
        this.authService = authService;
        this.bookService = bookService;
        this.borrowService = borrowService;
    }

    public void start() {
        Scanner sc = new Scanner(System.in);
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
    }

    private void listBooks() {
        List<Book> books = bookService.listApprovedBooksWithAvailability();
        if (books.isEmpty()) {
            System.out.println("No approved books available.");
            return;
        }

        System.out.println("=== Available Books ===");
        for (Book b : books) {
            String status = b.isAvailable() ? "AVAILABLE" : "BORROWED";
            System.out.printf("ID=%s | %s | %s | %s | %s%nSummary: %s%n%n",
                    b.getId(), b.getTitle(), b.getAuthorFullName(), b.getPublishDate(), status, b.getSummary());
        }
    }

    private void borrow(Scanner sc) {
        if (currentUser == null) {
            System.out.println("Please login first.");
            return;
        }
        System.out.print("Enter Book ID to borrow: ");
        String bookId = sc.nextLine();

        BorrowRecord record = borrowService.borrowBook(currentUser.getUsername(), bookId);
        System.out.printf("Borrow successful. Borrow Date: %s, Due Date: %s%n",
                record.getBorrowDate(), record.getDueDate());
    }
}