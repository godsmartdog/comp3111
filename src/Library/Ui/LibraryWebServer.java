package Library.Ui;

import Library.Service.AuthService;
import Library.Service.AuthorDraftService;
import Library.Service.AuthorService2;
import Library.Service.BookService;
import Library.Service.BookRequestService;
import Library.Service.BorrowService;
import Library.Service.BookReviewService;
import Library.Service.FileService;
import Library.Service.LibrarianService3;
import Library.Service.NotificationService;
import Library.Service.ReadingProgressService;
import Library.Service.RecommendationService;
import Library.Service.SessionSnapshotService;

import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class LibraryWebServer {

    private final AuthService authService;
    private final BookService bookService;
    private final BorrowService borrowService;
    private final BookReviewService bookReviewService;
    private final BookRequestService bookRequestService;
    private final RecommendationService recommendationService;
    private final AuthorService2 authorService;
    private final AuthorDraftService authorDraftService;
    private final FileService fileService;
    private final LibrarianService3 librarianService;
    private final NotificationService notificationService;
    private final ReadingProgressService readingProgressService;
    private final SessionSnapshotService sessionSnapshotService;

    private final int port;
    private final Path staticDir;
    private HttpServer server;

    public LibraryWebServer(AuthService authService,
                            BookService bookService,
                            BorrowService borrowService,
                            BookReviewService bookReviewService,
                            BookRequestService bookRequestService,
                            RecommendationService recommendationService,
                            AuthorService2 authorService,
                            AuthorDraftService authorDraftService,
                            FileService fileService,
                            LibrarianService3 librarianService,
                            int port) {
        this(authService,
                bookService,
                borrowService,
                bookReviewService,
                bookRequestService,
                recommendationService,
                authorService,
                authorDraftService,
                fileService,
                librarianService,
                null,
                null,
                null,
                port);
    }

    public LibraryWebServer(AuthService authService,
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
                            SessionSnapshotService sessionSnapshotService,
                            int port) {
        this.authService = authService;
        this.bookService = bookService;
        this.borrowService = borrowService;
        this.bookReviewService = bookReviewService;
        this.bookRequestService = bookRequestService;
        this.recommendationService = recommendationService;
        this.authorService = authorService;
        this.authorDraftService = authorDraftService;
        this.fileService = fileService;
        this.librarianService = librarianService;
        this.notificationService = notificationService;
        this.readingProgressService = readingProgressService;
        this.sessionSnapshotService = sessionSnapshotService;
        this.port = port;
        this.staticDir = resolveStaticDir();
    }

    public void start() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        LibraryApiHandlers apiHandlers = new LibraryApiHandlers(
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
        apiHandlers.register(server);
        server.createContext("/", new StaticFileHandler(staticDir));

        server.setExecutor(null);
        server.start();

        String url = "http://localhost:" + port;
        System.out.println("Library Web UI running at " + url);

        openBrowser(url);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static Path resolveStaticDir() {
        List<Path> candidates = List.of(
                Path.of("Library", "Ui", "web"),
                Path.of("src", "Library", "Ui", "web")
        );

        Optional<Path> found = candidates.stream().filter(Files::isDirectory).findFirst();
        if (found.isPresent()) {
            return found.get().toAbsolutePath().normalize();
        }

        throw new IllegalStateException("Cannot find static UI directory (Library/Ui/web).");
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // Browser auto-open failure should not stop server startup.
        }
    }
}
