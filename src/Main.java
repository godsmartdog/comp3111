import Library.Model.*;
import Library.Repository.*;
import Library.Service.*;
import Library.Ui.*;

import java.time.LocalDate;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        UserRepository userRepo = new MemoryUserRepository();
        BookRepository bookRepo = new MemoryBookRepository();
        BorrowRepository borrowRepo = new MemoryBorrowRepository();
        AuthorProfileRepository2 authorProfileRepo = new MemoryAuthorProfileRepository2();
        BookSubmissionRepository2 submissionRepository = new MemoryBookSubmissionRepository2();
        LibrarianProfileRepository3 librarianProfileRepo = new MemoryLibrarianProfileRepository3();
        BookDraftRepository2 draftRepository = new MemoryBookDraftRepository2();

        // seed approved books (from teammate author/librarian flow eventually)
        Book b1 = new Book("Clean Code", "Robert C. Martin", "A handbook of agile software craftsmanship.");
        b1.approve(LocalDate.now().minusDays(2));
        bookRepo.save(b1);

        Book b2 = new Book("Design Patterns", "GoF", "Classic OO design patterns and examples.");
        b2.approve(LocalDate.now().minusDays(1));
        bookRepo.save(b2);

        AuthService authService = new AuthService(userRepo);
        BookService bookService = new BookService(bookRepo);
        BorrowService borrowService = new BorrowService(bookRepo, borrowRepo);
        RecommendationService recommendationService = new RecommendationService(bookRepo, borrowRepo);
        FileService fileService = new FileService();
        AuthorService2 authorService = new AuthorService2(userRepo, authorProfileRepo, submissionRepository);
        AuthorDraftService authorDraftService = new AuthorDraftService(draftRepository);
        LibrarianService3 librarianService = new LibrarianService3(
                userRepo, librarianProfileRepo, submissionRepository, bookRepo
        );

        ConsoleUI consoleUi = new ConsoleUI(authService, bookService, borrowService, recommendationService);
        AuthorConsoleUI2 authorPortal = new AuthorConsoleUI2(authorService, authorDraftService, fileService);
        LibrarianConsoleUI3 librarianPortal = new LibrarianConsoleUI3(librarianService, fileService);

        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        while (running) {
            System.out.println("\n=== COMP3111 Library Management System ===");
            System.out.println("1. Student/Staff Portal");
            System.out.println("2. Author Portal");
            System.out.println("3. Librarian Portal");
            System.out.println("0. Exit");
            System.out.print("Choose: ");

            switch (scanner.nextLine()) {
                case "1" -> consoleUi.start(scanner);
                case "2" -> authorPortal.start(scanner);
                case "3" -> librarianPortal.start(scanner);
                case "0" -> running = false;
                default -> System.out.println("Invalid choice.");
            }
        }

        System.out.println("Bye.");
    }
}
