package Library.Ui;

import Library.Model.Book;
import Library.Persistence.LibraryDatabase;
import Library.Persistence.LibraryDatabaseService;
import Library.Repository.MemoryAuthorProfileRepository2;
import Library.Repository.MemoryBookDraftRepository2;
import Library.Repository.MemoryBookRequestRepository2;
import Library.Repository.MemoryBookRepository;
import Library.Repository.MemoryBookReviewRepository;
import Library.Repository.MemoryBookSubmissionRepository2;
import Library.Repository.MemoryBorrowRepository;
import Library.Repository.MemoryLibrarianProfileRepository3;
import Library.Repository.MemoryNotificationRepository;
import Library.Repository.MemoryReadingProgressRepository;
import Library.Repository.MemorySessionSnapshotRepository;
import Library.Repository.MemoryUserRepository;
import Library.Service.AuthService;
import Library.Service.AuthorDraftService;
import Library.Service.AuthorService2;
import Library.Service.BookService;
import Library.Service.BookRequestService;
import Library.Service.BookReviewService;
import Library.Service.BorrowService;
import Library.Service.FileService;
import Library.Service.LibrarianService3;
import Library.Service.NotificationService;
import Library.Service.ReadingProgressService;
import Library.Service.RecommendationService;
import Library.Service.SessionSnapshotService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LibraryManagementApp {
    private static final int WEB_PORT = 8080;

    public static void main(String[] args) {
        System.out.println("Demo accounts: student1 / author1 / librarian1, password: Password1!");
        try {
            AppContext context = createContext();
            LibraryWebServer webServer = new LibraryWebServer(
                    context.authService,
                    context.bookService,
                    context.borrowService,
                    context.bookReviewService,
                    context.bookRequestService,
                    context.recommendationService,
                    context.authorService,
                    context.authorDraftService,
                    context.fileService,
                    context.librarianService,
                    context.notificationService,
                    context.readingProgressService,
                    context.sessionSnapshotService,
                    WEB_PORT
            );
            startAutosave(context.database);
            webServer.start();

            if (containsArg(args, "--smoke-test")) {
                Thread.sleep(1500);
                webServer.stop();
                System.out.println("Web UI smoke test passed.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean containsArg(String[] args, String target) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (target.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    // Helper method to set up persistent repositories and seed demo data on first run.
    private static AppContext createContext() throws Exception {
        DatabaseStartupState startupState = loadDatabase();
        LibraryDatabase database = startupState.database;
        MemoryBookRepository bookRepository = database.bookRepository;
        MemoryBorrowRepository borrowRepository = database.borrowRepository;
        MemoryBookReviewRepository bookReviewRepository = database.bookReviewRepository;
        MemoryBookRequestRepository2 bookRequestRepository = database.bookRequestRepository;
        MemoryUserRepository userRepository = database.userRepository;
        MemoryAuthorProfileRepository2 authorProfileRepository = database.authorProfileRepository;
        MemoryBookSubmissionRepository2 submissionRepository = database.submissionRepository;
        MemoryBookDraftRepository2 draftRepository = database.draftRepository;
        MemoryLibrarianProfileRepository3 librarianProfileRepository = database.librarianProfileRepository;
        MemoryReadingProgressRepository readingProgressRepository = database.readingProgressRepository;
        MemoryNotificationRepository notificationRepository = database.notificationRepository;
        MemorySessionSnapshotRepository sessionSnapshotRepository = database.sessionSnapshotRepository;

        AuthService authService = new AuthService(userRepository);
        BookService bookService = new BookService(bookRepository);
        ReadingProgressService readingProgressService = new ReadingProgressService(readingProgressRepository);
        NotificationService notificationService = new NotificationService(notificationRepository);
        SessionSnapshotService sessionSnapshotService = new SessionSnapshotService(sessionSnapshotRepository);
        BorrowService borrowService = new BorrowService(bookRepository, borrowRepository, readingProgressService, notificationService);
        BookReviewService bookReviewService = new BookReviewService(bookReviewRepository, bookService, borrowService, notificationService);
        BookRequestService bookRequestService = new BookRequestService(bookRequestRepository, bookRepository);
        RecommendationService recommendationService = new RecommendationService(bookRepository, borrowRepository);
        FileService fileService = new FileService();
        AuthorService2 authorService = new AuthorService2(
            userRepository,
            authorProfileRepository,
            submissionRepository,
            bookRepository,
            borrowRepository,
            fileService
        );
        AuthorDraftService authorDraftService = new AuthorDraftService(draftRepository);
        LibrarianService3 librarianService = new LibrarianService3(
            userRepository,
            authorProfileRepository,
            librarianProfileRepository,
            submissionRepository,
            bookRepository
        );

        if (startupState.seedRequired) {
            seedDemoData(authService, authorService, librarianService, bookRepository, borrowService);
            saveDatabase(database, false);
            System.out.println("Created new persistent library database at " + LibraryDatabaseService.DEFAULT_DB_PATH);
        }

        return new AppContext(
                database,
                authService,
                bookService,
                borrowService,
                bookReviewService,
                bookRequestService,
                recommendationService,
                authorService,
                authorDraftService,
                fileService,
                librarianService,
                notificationService,
                readingProgressService,
                sessionSnapshotService
        );
    }

    private static DatabaseStartupState loadDatabase() {
        Path path = LibraryDatabaseService.DEFAULT_DB_PATH;
        if (LibraryDatabaseService.exists(path)) {
            Optional<LibraryDatabase> database = LibraryDatabaseService.load(path);
            if (database.isPresent()) {
                System.out.println("Loaded persistent library database from " + path);
                return new DatabaseStartupState(database.get(), false);
            }
        }
        return new DatabaseStartupState(new LibraryDatabase(), true);
    }

    private static void seedDemoData(AuthService authService,
                                     AuthorService2 authorService,
                                     LibrarianService3 librarianService,
                                     MemoryBookRepository bookRepository,
                                     BorrowService borrowService) throws Exception {
        authService.registerStudentOrStaff("student1", "Student Demo", "Password1!", Library.Model.Role.STUDENT);
        authorService.registerAuthor("author1", "Author Demo", "Password1!", "Writes demo content.");
        librarianService.registerLibrarian("librarian1", "Librarian Demo", "Password1!", "EMP-DEMO");

        Book book1 = new Book(
            "How to become a Leetcode Master",
            "",
            "Dickson Lam",
            List.of("Technology", "Education"),
            "Available"
        );
        book1.approve(LocalDate.now());
        bookRepository.save(book1);

        Book book2 = new Book(
            "How to become a board game Master",
            "",
            "Dickson Lam",
            List.of("Education", "Children"),
            "Available"
        );
        book2.approve(LocalDate.now());
        bookRepository.save(book2);

        Book book3 = new Book(
            "Why R18 is very useful for New-Generation",
            "",
            "FelixMau",
            List.of("Philosophy", "Young Adult"),
            "Borrowed"
        );
        book3.approve(LocalDate.now());
        bookRepository.save(book3);
        borrowService.borrowBook("student1", book3.getId(), 10);

        Book book4 = new Book(
            "How to become GrandMaster of CodeForce",
            "",
            "GodSmartDog",
            List.of("Technology", "Science"),
            "Available"
        );
        book4.approve(LocalDate.now());
        bookRepository.save(book4);

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
    }

    private static void startAutosave(LibraryDatabase database) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> saveDatabase(database, true), "library-database-shutdown-save"));
        ScheduledExecutorService autosave = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "library-database-autosave");
            thread.setDaemon(true);
            return thread;
        });
        autosave.scheduleAtFixedRate(() -> saveDatabase(database, false), 2, 2, TimeUnit.SECONDS);
    }

    private static void saveDatabase(LibraryDatabase database, boolean logSuccess) {
        try {
            LibraryDatabaseService.save(LibraryDatabaseService.DEFAULT_DB_PATH, database);
            if (logSuccess) {
                System.out.println("Library database autosaved.");
            }
        } catch (RuntimeException e) {
            System.err.println("Warning: failed to save persistent library database: " + e.getMessage());
        }
    }

    // Using record for simple immutable context holder
    private record DatabaseStartupState(LibraryDatabase database, boolean seedRequired) {}

    private record AppContext(
            LibraryDatabase database,
            AuthService authService,
            BookService bookService,
            BorrowService borrowService,
            BookReviewService bookReviewService,
            BookRequestService bookRequestService,
            RecommendationService recommendationService,
            AuthorService2 authorService,
            AuthorDraftService authorDraftService,
            FileService fileService,
            LibrarianService3 librarianService,
            NotificationService notificationService,
            ReadingProgressService readingProgressService,
            SessionSnapshotService sessionSnapshotService
    ) {}
}