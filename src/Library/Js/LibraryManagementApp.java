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
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

// Entry point for the JavaScript-based web UI.
// Starts a local HTTP server and opens the browser at the app URL.
public class LibraryManagementApp {

    // Main method — starts the HTTP server and opens the browser
    public static void main(String[] args) throws Exception {
        AppContext context = createContext();

        LibraryApiServer apiServer = new LibraryApiServer(
                context.authService,
                context.bookService,
                context.borrowService,
                context.recommendationService,
                context.authorService,
                context.authorDraftService,
                context.fileService,
                context.librarianService
        );

        int port = 8080;
        HttpServer server = apiServer.start(port);
        String url = "http://localhost:" + port;
        System.out.println("Library Management System running at " + url);
        System.out.println("Demo accounts: student1 / author1 / librarian1, password: Password1!");

        // Open the browser automatically if Desktop is supported
        try {
            Desktop desktop = Desktop.getDesktop();
            if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(new URI(url));
            }
        } catch (Exception ignored) {
            // Not critical — user can open the URL manually
        }

        // Keep the server running until the process is terminated
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
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