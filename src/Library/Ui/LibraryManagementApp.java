package Library.Ui;

import Library.Model.Book;
import Library.Repository.MemoryAuthorProfileRepository2;
import Library.Repository.MemoryBookDraftRepository2;
import Library.Repository.MemoryBookRequestRepository2;
import Library.Repository.MemoryBookRepository;
import Library.Repository.MemoryBookReviewRepository;
import Library.Repository.MemoryBookSubmissionRepository2;
import Library.Repository.MemoryBorrowRepository;
import Library.Repository.MemoryLibrarianProfileRepository3;
import Library.Repository.MemoryReadingProgressRepository;
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
import Library.Service.ReadingProgressService;
import Library.Service.RecommendationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

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
                    WEB_PORT
            );
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

    // Helper method to set up in-memory repositories and seed demo data
    // In a real application, this would connect to a database and not include demo seeding
    // This method is intentionally verbose for clarity and educational purposes
    private static AppContext createContext() throws Exception {
        MemoryBookRepository bookRepository = new MemoryBookRepository();
        MemoryBorrowRepository borrowRepository = new MemoryBorrowRepository();
        MemoryBookReviewRepository bookReviewRepository = new MemoryBookReviewRepository();
        MemoryBookRequestRepository2 bookRequestRepository = new MemoryBookRequestRepository2();
        MemoryUserRepository userRepository = new MemoryUserRepository();
        MemoryAuthorProfileRepository2 authorProfileRepository = new MemoryAuthorProfileRepository2();
        MemoryBookSubmissionRepository2 submissionRepository = new MemoryBookSubmissionRepository2();
        MemoryBookDraftRepository2 draftRepository = new MemoryBookDraftRepository2();
        MemoryLibrarianProfileRepository3 librarianProfileRepository = new MemoryLibrarianProfileRepository3();

        AuthService authService = new AuthService(userRepository);
        BookService bookService = new BookService(bookRepository);
        ReadingProgressService readingProgressService = new ReadingProgressService(new MemoryReadingProgressRepository());
        BorrowService borrowService = new BorrowService(bookRepository, borrowRepository, readingProgressService);
        BookReviewService bookReviewService = new BookReviewService(bookReviewRepository, bookService, borrowService);
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

        return new AppContext(
                authService,
                bookService,
                borrowService,
                bookReviewService,
                bookRequestService,
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
            BookReviewService bookReviewService,
            BookRequestService bookRequestService,
            RecommendationService recommendationService,
            AuthorService2 authorService,
            AuthorDraftService authorDraftService,
            FileService fileService,
            LibrarianService3 librarianService
    ) {}
}