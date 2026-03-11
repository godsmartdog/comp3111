import Library.Model.*;
import Library.Repository.*;
import Library.Service.*;
import Library.Ui.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.time.LocalDate;

public class Main extends Application {
    private AuthService authService;
    private BookService bookService;
    private BorrowService borrowService;
    private RecommendationService recommendationService;
    private FileService fileService;
    private AuthorService2 authorService;
    private AuthorDraftService authorDraftService;
    private LibrarianService3 librarianService;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
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

        authService = new AuthService(userRepo);
        bookService = new BookService(bookRepo);
        borrowService = new BorrowService(bookRepo, borrowRepo);
        recommendationService = new RecommendationService(bookRepo, borrowRepo);
        fileService = new FileService();
        authorService = new AuthorService2(userRepo, authorProfileRepo, submissionRepository);
        authorDraftService = new AuthorDraftService(draftRepository);
        librarianService = new LibrarianService3(
                userRepo, librarianProfileRepo, submissionRepository, bookRepo
        );

        LibraryManagementUI ui = new LibraryManagementUI(
                authService,
                bookService,
                borrowService,
                recommendationService,
                authorService,
                authorDraftService,
                fileService,
                librarianService
        );

        stage.setTitle("COMP3111 Library Management System");
        stage.setScene(new Scene(ui.createContent(), 1220, 820));
        stage.show();
    }
}
