package Library.Js;

import Library.Model.Book;
import Library.Repository.MemoryAuthorProfileRepository2;
import Library.Repository.MemoryBookDraftRepository2;
import Library.Repository.MemoryBookRepository;
import Library.Repository.MemoryBookSubmissionRepository2;
import Library.Repository.MemoryBorrowRepository;
import Library.Repository.MemoryLibrarianProfileRepository3;
import Library.Repository.MemoryUserRepository;
import Library.Service.AuthService;
import Library.Service.AuthorDraftService;
import Library.Service.AuthorService2;
import Library.Service.BookService;
import Library.Service.BorrowService;
import Library.Service.FileService;
import Library.Service.LibrarianService3;
import Library.Service.RecommendationService;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public class LibraryManagementApp extends Application {
    // `start` method is the JavaFX entry point, where we set up the UI and show the stage
    @Override
    public void start(Stage stage) throws Exception {
        AppContext context = createContext();

        // Create the main UI component, passing in all necessary services from the context
        LibraryManagementUI ui = new LibraryManagementUI(
                context.authService,
                context.bookService,
                context.borrowService,
                context.recommendationService,
                context.authorService,
                context.authorDraftService,
                context.fileService,
                context.librarianService
        );

        stage.setTitle("COMP3111 Library Management System");
        stage.setScene(new Scene(ui.createContent(), 1280, 860));
        stage.show();

        if (getParameters().getRaw().contains("--smoke-test")) {
            PauseTransition delay = new PauseTransition(Duration.seconds(2));
            delay.setOnFinished(event -> {
                System.out.println("JavaFX smoke test passed.");
                Platform.exit();
            });
            delay.play();
        }
    }

    // Main method to launch the JavaFX application
    public static void main(String[] args) {
        System.out.println("Demo accounts: student1 / author1 / librarian1, password: Password1!");
        launch(args);
    }

    // Helper method to set up in-memory repositories and seed demo data
    // In a real application, this would connect to a database and not include demo seeding
    // This method is intentionally verbose for clarity and educational purposes
    private static AppContext createContext() throws Exception {
        MemoryBookRepository bookRepository = new MemoryBookRepository();
        MemoryBorrowRepository borrowRepository = new MemoryBorrowRepository();
        MemoryUserRepository userRepository = new MemoryUserRepository();
        MemoryAuthorProfileRepository2 authorProfileRepository = new MemoryAuthorProfileRepository2();
        MemoryBookSubmissionRepository2 submissionRepository = new MemoryBookSubmissionRepository2();
        MemoryBookDraftRepository2 draftRepository = new MemoryBookDraftRepository2();
        MemoryLibrarianProfileRepository3 librarianProfileRepository = new MemoryLibrarianProfileRepository3();

        AuthService authService = new AuthService(userRepository);
        BookService bookService = new BookService(bookRepository);
        BorrowService borrowService = new BorrowService(bookRepository, borrowRepository);
        RecommendationService recommendationService = new RecommendationService(bookRepository, borrowRepository);
        AuthorService2 authorService = new AuthorService2(userRepository, authorProfileRepository, submissionRepository);
        AuthorDraftService authorDraftService = new AuthorDraftService(draftRepository);
        FileService fileService = new FileService();
        LibrarianService3 librarianService = new LibrarianService3(userRepository, librarianProfileRepository, submissionRepository, bookRepository);

        authService.registerStudentOrStaff("student1", "Student Demo", "Password1!", Library.Model.Role.STUDENT);
        authorService.registerAuthor("author1", "Author Demo", "Password1!", "Writes demo content.");
        librarianService.registerLibrarian("librarian1", "Librarian Demo", "Password1!", "EMP-DEMO");

        Book available = new Book("Distributed Systems in Practice", "Author Demo", "A published sample title for the student portal.");
        available.approve(LocalDate.now());
        bookRepository.save(available);

        Book borrowed = new Book("Testing Java Applications", "Author Demo", "A borrowed sample title to show unavailable styling.");
        borrowed.approve(LocalDate.now());
        bookRepository.save(borrowed);
        borrowService.borrowBook("student1", borrowed.getId(), 10);

        Path pendingSubmissionFile = Files.createTempFile("library-demo-submission", ".txt");
        Files.write(pendingSubmissionFile, List.of("Pending submission preview", "This file is used by the JavaFX demo."));
        pendingSubmissionFile.toFile().deleteOnExit();
        authorService.publishBook(
                "author1",
                "Pending Review Sample",
                List.of("Technology"),
                "A pending submission seeded for the librarian tab.",
                pendingSubmissionFile.toString()
        );

        return new AppContext(
                authService,
                bookService,
                borrowService,
                recommendationService,
                authorService,
                authorDraftService,
                fileService,
                librarianService
        );
    }

    // Using record for simple immutable context holder
    private record AppContext(
            AuthService authService,
            BookService bookService,
            BorrowService borrowService,
            RecommendationService recommendationService,
            AuthorService2 authorService,
            AuthorDraftService authorDraftService,
            FileService fileService,
            LibrarianService3 librarianService
    ) {}
}