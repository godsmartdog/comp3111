package Library.Ui;

import Library.Model.Book;
import Library.Model.BookRequest2;
import Library.Model.BookRequestStatus;
import Library.Model.BookReview;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.NotificationItem;
import Library.Model.NotificationPriority;
import Library.Model.ReadingProgress;
import Library.Model.Role;
import Library.Model.SessionSnapshot;
import Library.Model.User;
import Library.Repository.MemoryNotificationRepository;
import Library.Repository.MemoryBookRequestRepository2;
import Library.Repository.MemoryBookReviewRepository;
import Library.Repository.MemoryReadingProgressRepository;
import Library.Repository.MemorySessionSnapshotRepository;
import Library.Security.SecurityConfig;
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

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.BufferedReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibraryApiHandlers {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String SESSION_HEADER = "X-Session-Id";
    private static final String CRASH_TEST_HEADER = "X-Crash-Test-Hook";
    private static final String[] BASIC_NOTIFICATION_CATEGORIES = {
            "submission",
            "review",
            "account-update",
            "auto-return",
            "borrow-reminder",
            "book-deleted",
            "announcement"
    };
    // Local/dev internal testing token only.
    // TODO: Externalize this to environment/config before any production deployment.
    private static final String CRASH_TEST_TOKEN = "enable";
    private static final Path PROFILE_PHOTO_DIR = Paths.get(System.getProperty("user.dir"), "profile-photos");
    private static final Path REQUESTED_BOOK_DIR = Paths.get(System.getProperty("user.dir"), "downloaded-books");
    private static final int PDF_SEARCH_LIMIT = 5;
    private static final int PDF_SEARCH_PREFETCH_PAGES = 6;
    private static final int PDF_SEARCH_MAX_SOURCE_PAGES = 12;
    private static final int PDF_SEARCH_CACHE_MAX_AGE_MINUTES = 15;
    private static final int PDF_SEARCH_CACHE_MAX_RESULTS = 60;
    private static final String PDF_RERANKER_PROVIDER_ENV = "PDF_RERANKER_PROVIDER";
    private static final String PDF_RERANKER_URL_ENV = "PDF_RERANKER_URL";
    private static final String PDF_RERANKER_MODEL_ENV = "PDF_RERANKER_MODEL";
    private static final String GGUF_MODEL_PATH_ENV = "GGUF_MODEL_PATH";

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

    private final Map<String, PdfSearchCacheEntry> pdfSearchCache = new ConcurrentHashMap<>();

    private final Map<String, User> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionLastActiveAtMs = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userActivityLogs = new ConcurrentHashMap<>();
    // Slice 6: in-memory edit ledger for published books (3.8 NTH Version History).
    // Keyed by bookId; capped per-book at MAX_VERSION_HISTORY entries.
    private static final int MAX_VERSION_HISTORY = 50;
    private final Map<String, List<BookVersion>> bookVersions = new ConcurrentHashMap<>();

    private record BookVersion(String timestamp,
                               String editorUsername,
                               String fieldName,
                               String oldValue,
                               String newValue) {}

    private volatile SessionSnapshotSchema latestSessionSnapshot;

    private record ReadingHistoryEntry(String recordId,
                                       String bookId,
                                       String bookTitle,
                                       String authorUsername,
                                       String authorFullName,
                                       List<String> genres,
                                       LocalDate borrowDate,
                                       LocalDate dueDate,
                                       LocalDate returnedDate,
                                       boolean returned,
                                       boolean overdue,
                                       int readingDurationMinutes,
                                       int bookmarkPage,
                                       int highlightCount,
                                       String progressUpdatedAt) {
    }

    public LibraryApiHandlers(AuthService authService,
                              BookService bookService,
                              BorrowService borrowService,
                              BookReviewService bookReviewService,
                              BookRequestService bookRequestService,
                              RecommendationService recommendationService,
                              AuthorService2 authorService,
                              AuthorDraftService authorDraftService,
                              FileService fileService,
                              LibrarianService3 librarianService) {
                    this(
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
                        new NotificationService(new MemoryNotificationRepository()),
                        new ReadingProgressService(new MemoryReadingProgressRepository()),
                        new SessionSnapshotService(new MemorySessionSnapshotRepository())
                    );
    }

    public LibraryApiHandlers(AuthService authService,
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
                              SessionSnapshotService sessionSnapshotService) {
        this.authService = authService;
        this.bookService = bookService;
        this.borrowService = borrowService;
        this.notificationService = notificationService == null
            ? new NotificationService(new MemoryNotificationRepository())
            : notificationService;
        this.bookReviewService = bookReviewService == null
            ? new BookReviewService(new MemoryBookReviewRepository(), bookService, borrowService, this.notificationService)
            : bookReviewService;
        this.bookRequestService = bookRequestService == null
            ? new BookRequestService(new MemoryBookRequestRepository2(), bookService.getBookRepository())
            : bookRequestService;
        this.recommendationService = recommendationService;
        this.authorService = authorService;
        this.authorDraftService = authorDraftService;
        this.fileService = fileService;
        this.librarianService = librarianService;
        this.readingProgressService = readingProgressService == null
            ? new ReadingProgressService(new MemoryReadingProgressRepository())
            : readingProgressService;
        this.sessionSnapshotService = sessionSnapshotService == null
            ? new SessionSnapshotService(new MemorySessionSnapshotRepository())
            : sessionSnapshotService;
        this.latestSessionSnapshot = SessionSnapshotSchema.empty();
        if (borrowService != null) {
            borrowService.setNotificationService(this.notificationService);
        }
    }

    public void register(HttpServer server) {
        server.createContext("/api/register", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                Map<String, String> form = readForm(exchange);
                String username = required(form, "username");
                String fullName = required(form, "fullName");
                String password = required(form, "password");
                Role role = Role.valueOf(required(form, "role").toUpperCase());
                String bio = form.getOrDefault("bio", "");
                String employeeId = form.getOrDefault("employeeId", "");

                switch (role) {
                    case STUDENT, STAFF -> authService.registerStudentOrStaff(username, fullName, password, role);
                    case AUTHOR -> authorService.registerAuthor(username, fullName, password, bio);
                    case LIBRARIAN -> librarianService.registerLibrarian(username, fullName, password, employeeId);
                }

                notificationService.addNotification(
                        username,
                        "Welcome to E-Library",
                    "Your account is ready. Explore your portal functions from the main page.",
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "announcement")
                );
                appendUserActivity(username, "Registered new account as " + role + ".");

                sendText(exchange, 200, "Registration successful.");
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/login", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                Map<String, String> form = readForm(exchange);
                String username = required(form, "username");
                String password = required(form, "password");
                Role role = Role.valueOf(required(form, "role").toUpperCase());

                User user;
                switch (role) {
                    case STUDENT, STAFF -> user = authService.loginStudentOrStaff(username, password, role);
                    case AUTHOR -> user = authorService.loginAuthor(username, password);
                    case LIBRARIAN -> user = librarianService.loginLibrarian(username, password);
                    default -> throw new IllegalStateException("Unsupported role.");
                }

                String sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, user);
                sessionLastActiveAtMs.put(sessionId, Instant.now().toEpochMilli());
                refreshSessionSnapshot();
                appendUserActivity(user.getUsername(), "Logged in as " + role + ".");

                String payload = "{" +
                        "\"username\":\"" + JsonUtil.escape(user.getUsername()) + "\"," +
                        "\"fullName\":\"" + JsonUtil.escape(user.getFullName()) + "\"," +
                        "\"role\":\"" + user.getRole() + "\"," +
                        "\"sessionId\":\"" + sessionId + "\"" +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/logout", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            String sessionId = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
            if (sessionId != null) {
                String trimmedSessionId = sessionId.trim();
                sessionSnapshotService.clearSnapshotForSession(trimmedSessionId);
                sessions.remove(trimmedSessionId);
                sessionLastActiveAtMs.remove(trimmedSessionId);
                refreshSessionSnapshot();
            }
            sendText(exchange, 200, "Logged out.");
        });

        server.createContext("/api/session-snapshot/save", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireAuthenticated(exchange);
                String sessionId = requireSessionId(exchange);
                Map<String, String> form = readForm(exchange);
                String portalKey = RequestFilters.getTrimmed(form, "portalKey", "");
                String lastViewKey = RequestFilters.getTrimmed(form, "lastViewKey", "");
                String lastAction = RequestFilters.getTrimmed(form, "lastAction", "");
                String statePayload = RequestFilters.getTrimmed(form, "statePayload", "");

                SessionSnapshot snapshot = sessionSnapshotService.saveSnapshot(
                        sessionId,
                        user.getUsername(),
                        user.getRole(),
                        portalKey,
                        lastViewKey,
                        lastAction,
                        statePayload
                );
                sendJson(exchange, 200, sessionSnapshotToJson(snapshot, true));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/session-snapshot", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireAuthenticated(exchange);
                String sessionId = requireSessionId(exchange);
                SessionSnapshot snapshot = sessionSnapshotService
                        .getSnapshot(sessionId, user.getUsername(), user.getRole())
                        .orElse(null);
                sendJson(exchange, 200, sessionSnapshotToJson(snapshot, snapshot != null));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/session-snapshot/clear", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireAuthenticated(exchange);
                String sessionId = requireSessionId(exchange);
                boolean cleared = sessionSnapshotService.clearSnapshot(sessionId, user.getUsername(), user.getRole());
                sendJson(exchange, 200, "{" +
                        "\"status\":\"cleared\"," +
                        "\"cleared\":" + cleared +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/dev/crash-test", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed (dev-only endpoint).");
                return;
            }

            if (!isCrashHookEnabled(exchange)) {
                sendText(exchange, 403, "Crash test hook disabled (dev-only endpoint).");
                return;
            }

            try {
                User user = requireAuthenticated(exchange);
                String sessionId = requireSessionId(exchange);
                Map<String, String> form = readForm(exchange);
                String portalKey = RequestFilters.getTrimmed(form, "portalKey", "");
                String lastViewKey = RequestFilters.getTrimmed(form, "lastViewKey", "");
                String lastAction = RequestFilters.getTrimmed(form, "lastAction", "simulate");
                String statePayload = RequestFilters.getTrimmed(form, "statePayload", "");

                SessionSnapshot snapshot = sessionSnapshotService.saveSnapshot(
                        sessionId,
                        user.getUsername(),
                        user.getRole(),
                        portalKey,
                        lastViewKey,
                        lastAction,
                        statePayload
                );

                String payload = sessionSnapshotToJson(snapshot, true);
                payload = payload.substring(0, payload.length() - 1)
                        + ",\"status\":\"simulated\",\"scope\":\"dev-only endpoint\"}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage() + " (dev-only endpoint)");
            }
        });

        server.createContext("/api/internal/crash-test", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed (internal/dev-only endpoint).");
                return;
            }

            if (!isCrashHookEnabled(exchange)) {
                sendText(exchange, 403, "Crash test hook disabled (internal/dev-only endpoint).");
                return;
            }

            try {
                Map<String, String> values = "POST".equalsIgnoreCase(exchange.getRequestMethod())
                        ? readForm(exchange)
                        : readQuery(exchange.getRequestURI());
                String action = RequestFilters.getTrimmed(values, "action", "snapshot").toLowerCase();

                switch (action) {
                    case "snapshot" -> {
                        refreshSessionSnapshot();
                        String snapshotJson = latestSessionSnapshot.toJson();
                        String payload = snapshotJson.substring(0, snapshotJson.length() - 1)
                                + ",\"scope\":\"internal/dev-only crash-test endpoint\"}";
                        sendJson(exchange, 200, payload);
                    }
                    case "simulate" -> {
                        refreshSessionSnapshot();
                        int beforeCount = sessions.size();
                        sessions.clear();
                        sessionLastActiveAtMs.clear();
                        sendJson(exchange, 200, "{" +
                                "\"status\":\"simulated\"," +
                                "\"evictedSessions\":" + beforeCount + "," +
                                "\"scope\":\"internal/dev-only crash-test endpoint\"" +
                                "}");
                    }
                    case "recover" -> {
                        int recovered = restoreSessionsFromSnapshot(latestSessionSnapshot);
                        sendJson(exchange, 200, "{" +
                                "\"status\":\"recovered\"," +
                                "\"restoredSessions\":" + recovered + "," +
                                "\"scope\":\"internal/dev-only crash-test endpoint\"" +
                                "}");
                    }
                    default -> sendText(exchange, 400, "Unsupported crash-test action (internal/dev-only endpoint).");
                }
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage() + " (internal/dev-only endpoint)");
            }
        });

        server.createContext("/api/books", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String keyword = RequestFilters.getTrimmed(query, "keyword", "");
                if (keyword.isEmpty()) {
                    keyword = RequestFilters.getTrimmed(query, "q", "");
                }
                Boolean availabilityFilter = parseAvailabilityFilter(query);

                List<Book> books = bookService.listApprovedBooksWithFilters(keyword, availabilityFilter);

                sendJson(exchange, 200, booksToJson(books));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/books/summary", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String bookId = required(query, "bookId");
                Book book = bookService.findBookById(bookId)
                        .orElseThrow(() -> new IllegalArgumentException("Book not found."));

                String summary = nullToEmpty(book.getSummary()).trim();
                String preview = readFirstTwoLinesIfTextFile(book.getFilePath());
                String filePath = nullToEmpty(book.getFilePath()).trim();
                String lower = filePath.toLowerCase(Locale.ROOT);
                String previewType = (lower.endsWith(".pdf") || lower.endsWith(".docx")) ? "file" : "text";
                String previewUrl = previewType.equals("file") ? ("/api/books/preview-file?bookId=" + JsonUtil.escape(book.getId())) : "";
                String coverImageUrl = nullToEmpty(book.getCoverImagePath()).isBlank()
                        ? ""
                        : ("/api/books/cover?bookId=" + JsonUtil.escape(book.getId()));

                String payload = "{" +
                        "\"bookId\":\"" + JsonUtil.escape(book.getId()) + "\"," +
                        "\"title\":\"" + JsonUtil.escape(book.getTitle()) + "\"," +
                        "\"summary\":\"" + JsonUtil.escape(summary) + "\"," +
                        "\"preview\":\"" + JsonUtil.escape(preview) + "\"," +
                        "\"previewType\":\"" + JsonUtil.escape(previewType) + "\"," +
                        "\"previewUrl\":\"" + JsonUtil.escape(previewUrl) + "\"," +
                        "\"coverImagePath\":\"" + JsonUtil.escape(nullToEmpty(book.getCoverImagePath())) + "\"," +
                    "\"coverImageUrl\":\"" + JsonUtil.escape(coverImageUrl) + "\"," +
                    reviewStatsJson(book.getId()) +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/books/preview-file", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String bookId = required(query, "bookId");

                Book book = bookService.findBookById(bookId)
                        .orElseThrow(() -> new IllegalArgumentException("Book not found."));
                if (!book.isApproved()) {
                    throw new IllegalArgumentException("Book is not approved yet.");
                }

                String filePath = required(Map.of("filePath", nullToEmpty(book.getFilePath()).trim()), "filePath");
                Path file = Paths.get(filePath);
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Book file not found on server.");
                }

                byte[] bytes = Files.readAllBytes(file);
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                exchange.getResponseHeaders().set("Content-Type", detectContentType(fileName));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/books/cover", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String bookId = required(query, "bookId");

                Book book = bookService.findBookById(bookId)
                        .orElseThrow(() -> new IllegalArgumentException("Book not found."));
                if (!book.isApproved()) {
                    throw new IllegalArgumentException("Book is not approved yet.");
                }

                String coverPath = required(Map.of("coverPath", nullToEmpty(book.getCoverImagePath()).trim()), "coverPath");
                Path file = Paths.get(coverPath);
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Cover image not found on server.");
                }

                byte[] bytes = Files.readAllBytes(file);
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                exchange.getResponseHeaders().set("Content-Type", detectContentType(fileName));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/reviews", exchange -> {
            String method = exchange.getRequestMethod();
            String path = nullToEmpty(exchange.getRequestURI().getPath()).trim();
            boolean requestMine = path.endsWith("/me");

            if ("GET".equalsIgnoreCase(method)) {
                try {
                    if (requestMine) {
                        User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                        sendJson(exchange, 200, myReviewsToJson(user.getUsername()));
                        return;
                    }

                    User viewer = requireRole(exchange, Role.STUDENT, Role.STAFF, Role.AUTHOR, Role.LIBRARIAN);
                    Map<String, String> query = readQuery(exchange.getRequestURI());
                    String bookId = required(query, "bookId");
                    String sort = RequestFilters.getTrimmed(query, "sort", "recent");
                    sendJson(exchange, 200, reviewsToJson(bookReviewService.listReviewsForBook(bookId, sort), viewer.getUsername()));
                } catch (ApiAuthException e) {
                    sendText(exchange, 401, e.getMessage());
                } catch (Exception e) {
                    sendText(exchange, 400, e.getMessage());
                }
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                try {
                    if (requestMine) {
                        sendText(exchange, 405, "Method not allowed.");
                        return;
                    }

                    User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                    Map<String, String> form = readForm(exchange);
                    String bookId = required(form, "bookId");
                    int rating = Integer.parseInt(required(form, "rating"));
                    String reviewText = RequestFilters.getTrimmed(form, "reviewText", "");
                    boolean anonymous = parseBooleanFlag(form.get("anonymous"));
                    BookReview review = bookReviewService.submitReview(user.getUsername(), bookId, rating, reviewText, anonymous);
                    sendJson(exchange, 200, reviewToJson(review, user.getUsername()));
                } catch (ApiAuthException e) {
                    sendText(exchange, 401, e.getMessage());
                } catch (Exception e) {
                    sendText(exchange, 400, e.getMessage());
                }
                return;
            }

            // Compatibility fallback: allow query-based submit from clients that accidentally issue GET.
            if ("GET".equalsIgnoreCase(method)) {
                try {
                    Map<String, String> query = readQuery(exchange.getRequestURI());
                    if (query.containsKey("bookId") && query.containsKey("rating")) {
                        User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                        String bookId = required(query, "bookId");
                        int rating = Integer.parseInt(required(query, "rating"));
                        String reviewText = RequestFilters.getTrimmed(query, "reviewText", "");
                        boolean anonymous = parseBooleanFlag(query.get("anonymous"));
                        BookReview review = bookReviewService.submitReview(user.getUsername(), bookId, rating, reviewText, anonymous);
                        sendJson(exchange, 200, reviewToJson(review, user.getUsername()));
                        return;
                    }
                } catch (ApiAuthException e) {
                    sendText(exchange, 401, e.getMessage());
                    return;
                } catch (Exception e) {
                    sendText(exchange, 400, e.getMessage());
                    return;
                }
            }

            sendText(exchange, 405, "Method not allowed.");
        });

        server.createContext("/api/reviews/me", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                sendJson(exchange, 200, myReviewsToJson(user.getUsername()));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/reviews/submit", exchange -> {
            String method = exchange.getRequestMethod();
            if (!"POST".equalsIgnoreCase(method) && !"GET".equalsIgnoreCase(method)) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> values = "POST".equalsIgnoreCase(method)
                        ? readForm(exchange)
                        : readQuery(exchange.getRequestURI());
                String bookId = required(values, "bookId");
                int rating = Integer.parseInt(required(values, "rating"));
                String reviewText = RequestFilters.getTrimmed(values, "reviewText", "");
                boolean anonymous = parseBooleanFlag(values.get("anonymous"));
                BookReview review = bookReviewService.submitReview(user.getUsername(), bookId, rating, reviewText, anonymous);
                sendJson(exchange, 200, reviewToJson(review, user.getUsername()));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        // Register more specific author review endpoints BEFORE the generic /api/author/reviews
        server.createContext("/api/author/reviews/reply", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String reviewId = required(form, "reviewId");
                String replyText = required(form, "replyText");
                BookReview review = bookReviewService.replyToReview(user.getUsername(), reviewId, replyText);
                sendJson(exchange, 200, reviewToJson(review));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/reviews/flag", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String reviewId = required(form, "reviewId");
                String reason = RequestFilters.getTrimmed(form, "reason", "Inappropriate content");
                BookReview review = bookReviewService.flagReview(user.getUsername(), reviewId, reason);
                sendJson(exchange, 200, reviewToJson(review));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/reviews", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                sendJson(exchange, 200, reviewsToJson(bookReviewService.listReviewsForAuthor(user.getUsername())));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/profile", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF, Role.AUTHOR, Role.LIBRARIAN);
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String payload = "{" +
                            "\"username\":\"" + JsonUtil.escape(user.getUsername()) + "\"," +
                            "\"fullName\":\"" + JsonUtil.escape(user.getFullName()) + "\"," +
                            "\"role\":\"" + user.getRole() + "\"," +
                            "\"photoUrl\":\"" + JsonUtil.escape(profilePhotoUrl(user)) + "\"" +
                            "}";
                    sendJson(exchange, 200, payload);
                    return;
                }

                Map<String, String> form;
                UploadedFile uploadedPhoto = null;
                String contentType = nullToEmpty(exchange.getRequestHeaders().getFirst("Content-Type")).toLowerCase(Locale.ROOT);
                if (contentType.startsWith("multipart/form-data")) {
                    MultipartData multipartData = readMultipartForm(exchange);
                    form = multipartData.fields();
                    uploadedPhoto = multipartData.uploadedFile("photo");
                } else {
                    form = readForm(exchange);
                }
                String fullName = required(form, "fullName");
                String newPassword = form.getOrDefault("password", "");
                String currentPassword = form.getOrDefault("currentPassword", "");
                boolean passwordChanged = !nullToEmpty(newPassword).isBlank();
                String profilePhotoPath = uploadedPhoto == null ? "" : storeProfilePhoto(user.getUsername(), user.getRole(), uploadedPhoto);
                User updated = authService.updateStudentOrStaffProfile(user.getUsername(), fullName, newPassword, currentPassword, profilePhotoPath);
                if (!profilePhotoPath.isBlank()) {
                    updated.updateProfilePhotoPath(profilePhotoPath);
                }

                if (passwordChanged) {
                    invalidateSessionsByUsername(user.getUsername());
                } else {
                    String sessionId = nullToEmpty(exchange.getRequestHeaders().getFirst(SESSION_HEADER)).trim();
                    if (!sessionId.isEmpty()) {
                        sessions.put(sessionId, updated);
                        sessionLastActiveAtMs.put(sessionId, Instant.now().toEpochMilli());
                        refreshSessionSnapshot();
                    }
                }

                notificationService.addNotification(
                        user.getUsername(),
                        "Profile Updated",
                    "Your profile details were updated successfully.",
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "account-update")
                );

                sendText(exchange, 200, passwordChanged
                    ? "Password updated successfully. Please log in again."
                    : "Profile updated successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/profile/photo", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF, Role.AUTHOR, Role.LIBRARIAN);
                String photoPath = nullToEmpty(user.getProfilePhotoPath()).trim();
                if (photoPath.isEmpty()) {
                    sendText(exchange, 404, "Profile photo not found.");
                    return;
                }

                Path file = Paths.get(photoPath);
                if (!Files.isRegularFile(file)) {
                    sendText(exchange, 404, "Profile photo not found.");
                    return;
                }

                byte[] bytes = Files.readAllBytes(file);
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                exchange.getResponseHeaders().set("Content-Type", detectContentType(fileName));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/notifications", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF, Role.AUTHOR, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String scopeRaw = RequestFilters.getTrimmed(query, "scope", "active");
                NotificationService.NotificationScope scope = NotificationService.NotificationScope.fromString(scopeRaw);
                String keyword = RequestFilters.getTrimmed(query, "q", "");
                NotificationService.NotificationReadFilter readFilter = parseNotificationReadFilter(query);
                NotificationPriority priorityFilter = parseNotificationPriorityFilter(query);
                String categoryFilter = RequestFilters.getTrimmed(query, "category", "all");
                LocalDate createdDateFrom = parseDateFilter(query, "createdDateFrom");
                LocalDate createdDateTo = parseDateFilter(query, "createdDateTo");
                NotificationService.NotificationSortBy sortBy = parseNotificationSortBy(query);
                NotificationService.NotificationSortDirection sortDir = parseNotificationSortDir(query);
                List<NotificationItem> items = notificationService.listByUser(
                        user.getUsername(),
                        scope,
                        keyword,
                        readFilter,
                        priorityFilter,
                        sortBy,
                        sortDir
                );
                items = filterNotificationsByCategory(items, categoryFilter);
                items = filterNotificationsByCreatedDate(items, createdDateFrom, createdDateTo);
                sendJson(exchange, 200, notificationsToJson(items));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/notifications/read", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF, Role.AUTHOR, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String notificationId = required(form, "notificationId");
                NotificationItem item = notificationService.markAsRead(user.getUsername(), notificationId);

                String payload = "{" +
                        "\"id\":\"" + JsonUtil.escape(item.getId()) + "\"," +
                        "\"status\":\"read\"," +
                        "\"message\":\"Notification marked as read.\"" +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/notifications/delete", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF, Role.AUTHOR, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String notificationId = required(form, "notificationId");
                NotificationItem item = notificationService.deleteNotification(user.getUsername(), notificationId);

                String payload = "{" +
                        "\"id\":\"" + JsonUtil.escape(item.getId()) + "\"," +
                        "\"status\":\"deleted\"," +
                        "\"message\":\"Notification deleted.\"" +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/notifications/archive", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF, Role.AUTHOR, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String notificationId = required(form, "notificationId");
                NotificationItem item = notificationService.archiveNotification(user.getUsername(), notificationId);

                String payload = "{" +
                        "\"id\":\"" + JsonUtil.escape(item.getId()) + "\"," +
                        "\"status\":\"archived\"," +
                        "\"message\":\"Notification archived.\"" +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/notifications/unarchive", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF, Role.AUTHOR, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String notificationId = required(form, "notificationId");
                NotificationItem item = notificationService.unarchiveNotification(user.getUsername(), notificationId);

                String payload = "{" +
                        "\"id\":\"" + JsonUtil.escape(item.getId()) + "\"," +
                        "\"status\":\"active\"," +
                        "\"message\":\"Notification unarchived.\"" +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/book-requests", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                    Map<String, String> form = readForm(exchange);
                    BookRequest2 request = bookRequestService.submitRequest(
                            user.getUsername(),
                            user.getFullName(),
                            required(form, "title"),
                            required(form, "authorName"),
                            required(form, "genres"),
                            required(form, "reason")
                    );
                    notifyLibrariansForBookRequest(request, user);
                    sendJson(exchange, 200, "{" +
                            "\"message\":\"Book request submitted successfully.\"," +
                            "\"request\":" + bookRequestToJson(request) +
                            "}");
                } catch (ApiAuthException e) {
                    sendText(exchange, 401, e.getMessage());
                } catch (Exception e) {
                    sendText(exchange, 400, e.getMessage());
                }
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                    sendJson(exchange, 200, bookRequestsToJson(bookRequestService.listRequestsByRequester(user.getUsername())));
                } catch (ApiAuthException e) {
                    sendText(exchange, 401, e.getMessage());
                } catch (Exception e) {
                    sendText(exchange, 400, e.getMessage());
                }
                return;
            }

            sendText(exchange, 405, "Method not allowed.");
        });

        server.createContext("/api/librarian/book-requests", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String statusRaw = RequestFilters.getTrimmed(query, "status", "all");
                String keyword = RequestFilters.getTrimmed(query, "q", "");

                List<BookRequest2> items = bookRequestService.listRequests();
                if (!keyword.isBlank()) {
                    String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
                    items = items.stream()
                            .filter(item -> containsIgnoreCase(item.getTitle(), normalizedKeyword)
                                    || containsIgnoreCase(item.getAuthorName(), normalizedKeyword)
                                    || containsIgnoreCase(item.getRequesterFullName(), normalizedKeyword)
                                    || containsIgnoreCase(item.getRequesterUsername(), normalizedKeyword)
                                    || item.getGenres().stream().anyMatch(genre -> containsIgnoreCase(genre, normalizedKeyword))
                                    || containsIgnoreCase(item.getReason(), normalizedKeyword))
                            .toList();
                }

                String normalizedStatus = statusRaw == null ? "all" : statusRaw.trim().toLowerCase(Locale.ROOT);
                if (!"all".equals(normalizedStatus)) {
                    BookRequestStatus status = BookRequestStatus.valueOf(normalizedStatus.toUpperCase(Locale.ROOT));
                    items = items.stream().filter(item -> item.getStatus() == status).toList();
                }

                sendJson(exchange, 200, bookRequestsToJson(items));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/book-request/review", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String requestId = required(form, "requestId");
                String action = required(form, "action").toLowerCase(Locale.ROOT);
                String comment = nullToEmpty(form.getOrDefault("comment", "")).trim();
                String reason = nullToEmpty(form.getOrDefault("reason", "")).trim();

                if ("approve".equals(action)) {
                    BookRequest2 approved = bookRequestService.approveRequest(requestId, comment);
                    String approvalComment = comment.isBlank() ? "" : " Comment: " + comment;
                    notificationService.addNotification(
                            approved.getRequesterUsername(),
                            "Book Request Approved",
                        "Your request for \"" + approved.getTitle() + "\" was approved by a librarian." + approvalComment,
                        NotificationPriority.NORMAL,
                            null,
                            Map.of("type", "submission", "requestId", approved.getId(), "status", approved.getStatus().name())
                    );
                    sendJson(exchange, 200, "{" +
                            "\"message\":\"Book request approved.\"," +
                            "\"request\":" + bookRequestToJson(approved) +
                            "}");
                } else if ("reject".equals(action)) {
                    BookRequest2 rejected = bookRequestService.rejectRequest(requestId, comment, reason);
                    String rejectionReason = rejected.getRejectionReason().isBlank() ? "" : " Reason: " + rejected.getRejectionReason();
                    String rejectionComment = rejected.getLibrarianComment().isBlank() ? "" : " Comment: " + rejected.getLibrarianComment();
                    notificationService.addNotification(
                            rejected.getRequesterUsername(),
                            "Book Request Rejected",
                        "Your request for \"" + rejected.getTitle() + "\" was rejected by a librarian." + rejectionReason + rejectionComment,
                        NotificationPriority.NORMAL,
                            null,
                            Map.of("type", "submission", "requestId", rejected.getId(), "status", rejected.getStatus().name())
                    );
                    sendJson(exchange, 200, "{" +
                            "\"message\":\"Book request rejected.\"," +
                            "\"request\":" + bookRequestToJson(rejected) +
                            "}");
                } else if ("upload".equals(action)) {
                    BookRequest2 uploaded = bookRequestService.uploadRequest(requestId, comment);
                    String uploadComment = uploaded.getLibrarianComment().isBlank() ? "" : " Comment: " + uploaded.getLibrarianComment();
                    notificationService.addNotification(
                            uploaded.getRequesterUsername(),
                            "Requested Book Uploaded",
                        "Your requested book \"" + uploaded.getTitle() + "\" is now available in the library." + uploadComment,
                            NotificationPriority.NORMAL,
                            null,
                            Map.of("type", "submission", "requestId", uploaded.getId(), "bookId", uploaded.getBookId(), "status", uploaded.getStatus().name())
                    );
                    sendJson(exchange, 200, "{" +
                            "\"message\":\"Requested book uploaded to the library.\"," +
                            "\"request\":" + bookRequestToJson(uploaded) +
                            "}");
                } else {
                    sendText(exchange, 400, "Action must be approve, reject, or upload.");
                }
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/book-request-priority", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String requestId = required(form, "requestId");
                boolean priority = parseBooleanFlag(form.get("priority"));
                BookRequest2 updated = bookRequestService.setPriority(requestId, priority);
                sendJson(exchange, 200, "{" +
                        "\"message\":\"" + (priority ? "Marked as priority." : "Priority cleared.") + "\"," +
                        "\"request\":" + bookRequestToJson(updated) +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/book-request/search-pdf", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                String sessionId = requireSessionId(exchange);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String title = RequestFilters.getTrimmed(query, "title", "");
                String authorName = RequestFilters.getTrimmed(query, "authorName", "");
                int limit = RequestFilters.parseIntInRange(query, "limit", PDF_SEARCH_LIMIT, 1, 10);
                int page = RequestFilters.parseIntInRange(query, "page", 1, 1, 1000);
                String searchMode = normalizePdfSearchMode(RequestFilters.getTrimmed(query, "searchMode", "exact"));
                boolean debug = "1".equals(RequestFilters.getTrimmed(query, "debug", ""));

                PdfSearchStats stats = new PdfSearchStats();
                PdfSearchPageResult searchPage = searchPublicDomainPdfSources(sessionId, title, authorName, limit, page, searchMode, stats);
                if (debug) {
                    sendJson(exchange, 200, "{" +
                            "\"page\":" + page + "," +
                            "\"limit\":" + limit + "," +
                            "\"searchMode\":\"" + JsonUtil.escape(searchMode) + "\"," +
                            "\"hasNext\":" + searchPage.hasNext() + "," +
                            "\"results\":" + pdfSearchResultsToJson(searchPage.results()) + "," +
                            "\"debug\":" + pdfSearchDebugToJson(stats) +
                            "}");
                } else {
                    sendJson(exchange, 200, "{" +
                            "\"page\":" + page + "," +
                            "\"limit\":" + limit + "," +
                            "\"searchMode\":\"" + JsonUtil.escape(searchMode) + "\"," +
                            "\"hasNext\":" + searchPage.hasNext() + "," +
                            "\"results\":" + pdfSearchResultsToJson(searchPage.results()) +
                            "}");
                }
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/book-request/generate-description", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String title = required(form, "title");
                String authorName = required(form, "authorName");
                List<String> genres = RequestFilters.parseCsv(form, "genres");
                String reason = RequestFilters.getTrimmed(form, "reason", "");
                String content = RequestFilters.getTrimmed(form, "content", "");

                String description = generateBookRequestSummary(title, authorName, genres, reason, content, 50);
                sendJson(exchange, 200, "{" +
                        "\"description\":\"" + JsonUtil.escape(description) + "\"" +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/book-request/download", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String requestId = required(form, "requestId");
                String pdfUrl = required(form, "pdfUrl");
                String comment = nullToEmpty(form.getOrDefault("comment", "")).trim();
                String description = nullToEmpty(form.getOrDefault("description", "")).trim();
                String content = RequestFilters.getTrimmed(form, "content", "");

                BookRequest2 request = bookRequestService.getRequestByIdForReview(requestId);
                DownloadedPdf downloadedPdf = downloadRequestedPdf(pdfUrl, request.getId(), request.getTitle());
                fileService.validateSubmissionFile(downloadedPdf.filePath());

                String resolvedDescription = description.isBlank()
                    ? generateBookRequestSummary(request.getTitle(), request.getAuthorName(), request.getGenres(), request.getReason(), content, 50)
                    : description;

                BookRequest2 uploaded = bookRequestService.uploadRequest(
                        requestId,
                        comment,
                        resolvedDescription,
                        downloadedPdf.filePath(),
                        downloadedPdf.contentType()
                );

                String uploadComment = uploaded.getLibrarianComment().isBlank() ? "" : " Comment: " + uploaded.getLibrarianComment();
                notificationService.addNotification(
                        uploaded.getRequesterUsername(),
                        "Requested Book Downloaded",
                        "Your requested book \"" + uploaded.getTitle() + "\" was downloaded and added to the library." + uploadComment,
                        NotificationPriority.HIGH,
                        null,
                        Map.of("type", "other", "requestId", uploaded.getId(), "bookId", uploaded.getBookId(), "status", uploaded.getStatus().name())
                );

                sendJson(exchange, 200, "{" +
                        "\"message\":\"Requested book downloaded and uploaded.\"," +
                        "\"request\":" + bookRequestToJson(uploaded) +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/download", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String sourceUrl = required(query, "url");
                String fileName = RequestFilters.getTrimmed(query, "filename", "download.pdf");
                proxyDownloadPdf(exchange, sourceUrl, fileName);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/book-request/upload", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                String contentType = nullToEmpty(exchange.getRequestHeaders().getFirst("Content-Type")).toLowerCase(Locale.ROOT);
                if (!contentType.startsWith("multipart/form-data")) {
                    sendText(exchange, 400, "Multipart upload is required.");
                    return;
                }

                MultipartData multipartData = readMultipartForm(exchange);
                Map<String, String> form = multipartData.fields();
                UploadedFile uploadedFile = multipartData.uploadedFile("file");
                if (uploadedFile == null) {
                    sendText(exchange, 400, "Book file is required.");
                    return;
                }

                String requestId = required(form, "requestId");
                String comment = nullToEmpty(form.getOrDefault("comment", "")).trim();
                String description = nullToEmpty(form.getOrDefault("description", "")).trim();

                fileService.validateSubmissionFile(uploadedFile.path().toString());
                BookRequest2 uploaded = bookRequestService.uploadRequest(
                        requestId,
                        comment,
                        description,
                        uploadedFile.path().toString(),
                        detectContentType(uploadedFile.originalFileName())
                );

                String uploadComment = uploaded.getLibrarianComment().isBlank() ? "" : " Comment: " + uploaded.getLibrarianComment();
                notificationService.addNotification(
                        uploaded.getRequesterUsername(),
                        "Requested Book Uploaded",
                        "Your requested book \"" + uploaded.getTitle() + "\" was uploaded to the library." + uploadComment,
                        NotificationPriority.HIGH,
                        null,
                        Map.of("type", "other", "requestId", uploaded.getId(), "bookId", uploaded.getBookId(), "status", uploaded.getStatus().name())
                );

                sendJson(exchange, 200, "{" +
                        "\"message\":\"Requested book uploaded to the library.\"," +
                        "\"request\":" + bookRequestToJson(uploaded) +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/borrow", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> form = readForm(exchange);
                String bookId = required(form, "bookId");
                int days = RequestFilters.parseIntInRange(form, "days", 14, 1, 14);

                BorrowRecord record = borrowService.borrowBook(user.getUsername(), bookId, days);
                String borrowedBookTitle = bookService.findBookById(bookId)
                    .map(Book::getTitle)
                    .orElse(bookId);
                notificationService.addNotification(
                        user.getUsername(),
                        "Book Borrowed",
                    "You borrow this book (" + borrowedBookTitle + "). Due date: " + record.getDueDate(),
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "borrow-reminder", "bookId", bookId)
                );
                generateBorrowReminderNotifications(user.getUsername());
                sendText(exchange, 200, "Borrowed successfully. Due date: " + record.getDueDate());
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/borrow/bulk", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> form = readForm(exchange);
                List<String> bookIds = RequestFilters.parseCsv(form, "bookIds");
                if (bookIds.isEmpty()) {
                    throw new IllegalArgumentException("Missing required field: bookIds");
                }
                int days = RequestFilters.parseIntInRange(form, "days", 14, 1, 14);

                List<BorrowRecord> records = borrowService.borrowBooks(user.getUsername(), bookIds, days);
                BorrowRecord sample = records.get(0);
                List<String> borrowedTitles = new ArrayList<>();
                for (BorrowRecord record : records) {
                    String title = bookService.findBookById(record.getBookId())
                        .map(Book::getTitle)
                        .orElse(record.getBookId());
                    borrowedTitles.add(title);
                }
                notificationService.addNotification(
                        user.getUsername(),
                        "Books Borrowed",
                    "You borrowed " + records.size() + " book(s): " + String.join(", ", borrowedTitles) + ". Due date: " + sample.getDueDate(),
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "borrow-reminder", "count", String.valueOf(records.size()))
                );
                generateBorrowReminderNotifications(user.getUsername());

                sendText(exchange, 200, "Borrowed " + records.size() + " books successfully. Due date: " + sample.getDueDate());
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/borrow/reminders/check", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                int generated = generateBorrowReminderNotifications(user.getUsername());
                sendJson(exchange, 200, "{" +
                        "\"status\":\"checked\"," +
                        "\"generated\":" + generated +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/recommendations", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.STUDENT, Role.STAFF);
                int limit = 10;
                List<Book> books = recommendationService.recommendTopPopular(limit);
                sendJson(exchange, 200, booksToJson(books));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/return", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> form = readForm(exchange);
                String bookId = required(form, "bookId");
                BorrowRecord record = borrowService.returnBook(user.getUsername(), bookId);
                String returnedBookTitle = bookService.findBookById(bookId)
                    .map(Book::getTitle)
                    .orElse(bookId);
                notificationService.addNotification(
                        user.getUsername(),
                        "Book Returned",
                    "You return this book (" + returnedBookTitle + "). Due date was: " + record.getDueDate(),
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "borrow-reminder", "bookId", bookId)
                );
                sendText(exchange, 200, "Returned successfully. Due date was: " + record.getDueDate());
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        // Slice 10: bulk / partial return (1.5 NTH "Partial Return Option").
        // Form-encoded `bookIds` (CSV). Loops BorrowService.returnBook with
        // try/catch per id; succeeded count + failed [{id, reason}] returned.
        // Emits the same NORMAL "Book Returned" notification as /api/return
        // for each successful return so notification UX matches single-return.
        server.createContext("/api/return-bulk", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> form = readForm(exchange);
                String csv = RequestFilters.getTrimmed(form, "bookIds", "");
                if (csv.isEmpty()) {
                    sendText(exchange, 400, "At least one book must be selected.");
                    return;
                }
                int succeeded = 0;
                List<String> failedItems = new ArrayList<>();
                for (String raw : csv.split(",")) {
                    String id = raw.trim();
                    if (id.isEmpty()) continue;
                    try {
                        BorrowRecord record = borrowService.returnBook(user.getUsername(), id);
                        String returnedBookTitle = bookService.findBookById(id)
                            .map(Book::getTitle)
                            .orElse(id);
                        notificationService.addNotification(
                                user.getUsername(),
                                "Book Returned",
                                "You return this book (" + returnedBookTitle + "). Due date was: " + record.getDueDate(),
                                NotificationPriority.NORMAL,
                                null,
                                Map.of("type", "borrow-reminder", "bookId", id)
                        );
                        succeeded++;
                    } catch (Exception ex) {
                        failedItems.add("{\"id\":\"" + JsonUtil.escape(id)
                                + "\",\"reason\":\"" + JsonUtil.escape(ex.getMessage() == null ? "Failed" : ex.getMessage()) + "\"}");
                    }
                }
                String payload = "{\"succeeded\":" + succeeded
                        + ",\"failed\":[" + String.join(",", failedItems) + "]}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/borrows", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                generateBorrowReminderNotifications(user.getUsername());
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String status = parseBorrowStatus(query);
                String sortBy = parseBorrowSortBy(query);
                String sortDir = parseBorrowSortDir(query);
                LocalDate borrowDateFrom = parseDateFilter(query, "borrowDateFrom");
                LocalDate borrowDateTo = parseDateFilter(query, "borrowDateTo");
                LocalDate dueDateFrom = parseDateFilter(query, "dueDateFrom");
                LocalDate dueDateTo = parseDateFilter(query, "dueDateTo");

                List<BorrowRecord> records = borrowService.listBorrowRecordsByUser(
                        user.getUsername(),
                        status,
                        borrowDateFrom,
                        borrowDateTo,
                        dueDateFrom,
                        dueDateTo,
                        sortBy,
                        sortDir
                );

                sendJson(exchange, 200, borrowsToJson(records));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/borrows/history", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String keyword = RequestFilters.getTrimmed(query, "q", "");
                String authorFilter = RequestFilters.getTrimmed(query, "author", "");
                String genreFilter = RequestFilters.getTrimmed(query, "genre", "");
                LocalDate borrowDateFrom = parseDateFilter(query, "borrowDateFrom");
                LocalDate borrowDateTo = parseDateFilter(query, "borrowDateTo");
                LocalDate returnDateFrom = parseDateFilter(query, "returnDateFrom");
                LocalDate returnDateTo = parseDateFilter(query, "returnDateTo");
                String sortBy = parseReadingHistorySortBy(query);
                String sortDir = parseReadingHistorySortDir(query);

                List<ReadingHistoryEntry> entries = new ArrayList<>();
                for (BorrowRecord record : borrowService.listBorrowsByUser(user.getUsername())) {
                    Book book = bookService.findBookById(record.getBookId()).orElse(null);
                    String title = book == null ? record.getBookId() : book.getTitle();
                    String authorUsername = book == null ? "" : book.getAuthorUsername();
                    String authorFullName = book == null ? "" : book.getAuthorFullName();
                    List<String> genres = book == null ? List.of() : book.getGenres();
                    ReadingProgress progress = readingProgressService.findProgress(user.getUsername(), record.getBookId()).orElse(null);
                    LocalDate returnedDate = record.getReturnedDate();
                    int durationMinutes = progress == null ? 0 : progress.getTotalReadingMinutes();
                    int bookmarkPage = progress == null ? 0 : progress.getBookmarkPage();
                    int highlightCount = progress == null ? 0 : progress.getHighlights().size();
                    String progressUpdatedAt = progress == null ? "" : DATE_TIME_FORMATTER.format(progress.getUpdatedAt());

                    ReadingHistoryEntry entry = new ReadingHistoryEntry(
                            record.getId(),
                            record.getBookId(),
                            title,
                            authorUsername,
                            authorFullName,
                            genres,
                            record.getBorrowDate(),
                            record.getDueDate(),
                            returnedDate,
                            record.isReturned(),
                            record.isOverdue(LocalDate.now()),
                            durationMinutes,
                            bookmarkPage,
                            highlightCount,
                            progressUpdatedAt
                    );

                    if (matchesReadingHistoryFilters(entry, keyword, authorFilter, genreFilter, borrowDateFrom, borrowDateTo, returnDateFrom, returnDateTo)) {
                        entries.add(entry);
                    }
                }

                entries.sort(readingHistoryComparator(sortBy, sortDir));

                List<String> jsonItems = new ArrayList<>();
                for (ReadingHistoryEntry entry : entries) {
                    List<String> genreJson = new ArrayList<>();
                    for (String genre : entry.genres()) {
                        genreJson.add("\"" + JsonUtil.escape(genre) + "\"");
                    }

                    String returnedDate = entry.returnedDate() == null ? "" : entry.returnedDate().toString();
                    String progressUpdatedAt = entry.progressUpdatedAt() == null ? "" : entry.progressUpdatedAt();
                    jsonItems.add("{" +
                            "\"recordId\":\"" + JsonUtil.escape(entry.recordId()) + "\"," +
                            "\"bookId\":\"" + JsonUtil.escape(entry.bookId()) + "\"," +
                            "\"bookTitle\":\"" + JsonUtil.escape(entry.bookTitle()) + "\"," +
                            "\"authorUsername\":\"" + JsonUtil.escape(entry.authorUsername()) + "\"," +
                            "\"authorFullName\":\"" + JsonUtil.escape(entry.authorFullName()) + "\"," +
                            "\"genres\":[" + String.join(",", genreJson) + "]," +
                            "\"borrowDate\":\"" + entry.borrowDate() + "\"," +
                            "\"dueDate\":\"" + entry.dueDate() + "\"," +
                            "\"returned\":" + entry.returned() + "," +
                            "\"returnedDate\":\"" + JsonUtil.escape(returnedDate) + "\"," +
                            "\"overdue\":" + entry.overdue() + "," +
                            "\"readingDurationMinutes\":" + entry.readingDurationMinutes() + "," +
                            "\"bookmarkPage\":" + entry.bookmarkPage() + "," +
                            "\"highlightCount\":" + entry.highlightCount() + "," +
                            "\"progressUpdatedAt\":\"" + JsonUtil.escape(progressUpdatedAt) + "\"" +
                            "}");
                }
                sendJson(exchange, 200, "[" + String.join(",", jsonItems) + "]");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/student/reading-history-export", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String keyword = RequestFilters.getTrimmed(query, "q", "");
                String authorFilter = RequestFilters.getTrimmed(query, "author", "");
                String genreFilter = RequestFilters.getTrimmed(query, "genre", "");
                LocalDate borrowDateFrom = parseDateFilter(query, "borrowDateFrom");
                LocalDate borrowDateTo = parseDateFilter(query, "borrowDateTo");
                LocalDate returnDateFrom = parseDateFilter(query, "returnDateFrom");
                LocalDate returnDateTo = parseDateFilter(query, "returnDateTo");
                String sortBy = parseReadingHistorySortBy(query);
                String sortDir = parseReadingHistorySortDir(query);

                List<ReadingHistoryEntry> entries = new ArrayList<>();
                for (BorrowRecord record : borrowService.listBorrowsByUser(user.getUsername())) {
                    Book book = bookService.findBookById(record.getBookId()).orElse(null);
                    String title = book == null ? record.getBookId() : book.getTitle();
                    String authorUsername = book == null ? "" : book.getAuthorUsername();
                    String authorFullName = book == null ? "" : book.getAuthorFullName();
                    List<String> genres = book == null ? List.of() : book.getGenres();
                    ReadingProgress progress = readingProgressService.findProgress(user.getUsername(), record.getBookId()).orElse(null);
                    LocalDate returnedDate = record.getReturnedDate();
                    int durationMinutes = progress == null ? 0 : progress.getTotalReadingMinutes();
                    int bookmarkPage = progress == null ? 0 : progress.getBookmarkPage();
                    int highlightCount = progress == null ? 0 : progress.getHighlights().size();
                    String progressUpdatedAt = progress == null ? "" : DATE_TIME_FORMATTER.format(progress.getUpdatedAt());

                    ReadingHistoryEntry entry = new ReadingHistoryEntry(
                            record.getId(),
                            record.getBookId(),
                            title,
                            authorUsername,
                            authorFullName,
                            genres,
                            record.getBorrowDate(),
                            record.getDueDate(),
                            returnedDate,
                            record.isReturned(),
                            record.isOverdue(LocalDate.now()),
                            durationMinutes,
                            bookmarkPage,
                            highlightCount,
                            progressUpdatedAt
                    );

                    if (matchesReadingHistoryFilters(entry, keyword, authorFilter, genreFilter, borrowDateFrom, borrowDateTo, returnDateFrom, returnDateTo)) {
                        entries.add(entry);
                    }
                }
                entries.sort(readingHistoryComparator(sortBy, sortDir));

                LocalDate today = LocalDate.now();
                StringBuilder csv = new StringBuilder();
                csv.append("Book Title,Author,Genres,Borrow Date,Return Date,Reading Duration,Bookmark Page,Highlights Count\n");
                for (ReadingHistoryEntry entry : entries) {
                    String author = entry.authorFullName() == null || entry.authorFullName().isEmpty()
                            ? entry.authorUsername() : entry.authorFullName();
                    String genres = String.join("; ", entry.genres());
                    String borrowDate = entry.borrowDate() == null ? "" : entry.borrowDate().toString();
                    String returnDate = entry.returnedDate() == null
                            ? (entry.returned() ? "Returned" : "")
                            : entry.returnedDate().toString();
                    String duration = entry.readingDurationMinutes() + " min";
                    csv.append(csvEscape(entry.bookTitle())).append(',')
                            .append(csvEscape(author)).append(',')
                            .append(csvEscape(genres)).append(',')
                            .append(csvEscape(borrowDate)).append(',')
                            .append(csvEscape(returnDate)).append(',')
                            .append(csvEscape(duration)).append(',')
                            .append(csvEscape(String.valueOf(entry.bookmarkPage()))).append(',')
                            .append(csvEscape(String.valueOf(entry.highlightCount()))).append('\n');
                }

                byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
                String filename = "reading-history-" + today + ".csv";
                exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/borrow/content", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String bookId = required(query, "bookId");
                borrowService.requireActiveBorrow(user.getUsername(), bookId);

                Book book = bookService.findBookById(bookId)
                        .orElseThrow(() -> new IllegalArgumentException("Book not found."));

                String filePath = nullToEmpty(book.getFilePath()).trim();
                String lowerPath = filePath.toLowerCase();
                boolean hasPdf = !filePath.isEmpty() && lowerPath.endsWith(".pdf") && Files.isRegularFile(Paths.get(filePath));
                boolean hasDocx = !filePath.isEmpty() && lowerPath.endsWith(".docx") && Files.isRegularFile(Paths.get(filePath));
                if (hasPdf) {
                    sendJson(exchange, 200, "{" +
                            "\"type\":\"pdf\"," +
                            "\"url\":\"/api/borrow/file?bookId=" + JsonUtil.escape(bookId) + "\"" +
                            "}");
                    return;
                }

                if (hasDocx) {
                    sendJson(exchange, 200, "{" +
                        "\"type\":\"docx\"," +
                        "\"url\":\"/api/borrow/file?bookId=" + JsonUtil.escape(bookId) + "\"" +
                        "}");
                    return;
                }

                String fallback = nullToEmpty(book.getSummary()).isBlank()
                        ? "No readable content attached for this borrowed book yet."
                        : book.getSummary();
                sendJson(exchange, 200, "{" +
                        "\"type\":\"text\"," +
                        "\"content\":\"" + JsonUtil.escape(fallback) + "\"" +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/borrow/file", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String bookId = required(query, "bookId");
                borrowService.requireActiveBorrow(user.getUsername(), bookId);

                Book book = bookService.findBookById(bookId)
                        .orElseThrow(() -> new IllegalArgumentException("Book not found."));
                String filePath = required(Map.of("filePath", nullToEmpty(book.getFilePath()).trim()), "filePath");
                Path file = Paths.get(filePath);
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Book file not found on server.");
                }

                byte[] bytes = Files.readAllBytes(file);
                String fileName = file.getFileName().toString().toLowerCase();
                String contentType = fileName.endsWith(".pdf")
                        ? "application/pdf"
                        : (fileName.endsWith(".docx")
                        ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        : "application/octet-stream");
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/reading-progress", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> query = readQuery(exchange.getRequestURI());
                    String bookId = required(query, "bookId");
                    borrowService.requireActiveBorrow(user.getUsername(), bookId);
                    ReadingProgress progress = readingProgressService.getProgress(user.getUsername(), bookId);
                    sendJson(exchange, 200, readingProgressToJson(progress));
                    return;
                }

                Map<String, String> form = readForm(exchange);
                String bookId = required(form, "bookId");
                borrowService.requireActiveBorrow(user.getUsername(), bookId);
                int bookmark = RequestFilters.parseIntInRange(form, "bookmark", 1, 1, Integer.MAX_VALUE);
                List<String> highlights = RequestFilters.parseNewlineList(form, "highlights");
                ReadingProgress updated = readingProgressService.updateProgress(user.getUsername(), bookId, bookmark, highlights);
                sendJson(exchange, 200, readingProgressToJson(updated));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/reading-progress/time", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> form = readForm(exchange);
                String bookId = required(form, "bookId");
                borrowService.requireActiveBorrow(user.getUsername(), bookId);
                int seconds;
                if (form.containsKey("seconds")) {
                    seconds = RequestFilters.parseIntInRange(form, "seconds", 0, 0, 60_000_000);
                } else {
                    int minutes = RequestFilters.parseIntInRange(form, "minutes", 0, 0, 1_000_000);
                    seconds = Math.max(0, minutes) * 60;
                }
                ReadingProgress updated = readingProgressService.addReadingSeconds(user.getUsername(), bookId, seconds);
                sendJson(exchange, 200, readingProgressToJson(updated));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/draft", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String title = required(form, "title");
                List<String> genres = RequestFilters.parseCsv(form, "genres");
                String description = form.getOrDefault("description", "");
                String filePath = form.getOrDefault("filePath", "");

                authorDraftService.autoSave(user.getUsername(), title, genres, description, filePath);
                sendText(exchange, 200, "Draft saved.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/drafts", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                List<BookDraft2> drafts = authorDraftService.loadDrafts(user.getUsername());
                List<String> items = new ArrayList<>();
                for (BookDraft2 draft : drafts) {
                    StringJoiner genresJoiner = new StringJoiner(",", "[", "]");
                    for (String genre : draft.getGenres()) {
                        genresJoiner.add("\"" + JsonUtil.escape(genre) + "\"");
                    }
                    items.add("{" +
                            "\"title\":\"" + JsonUtil.escape(nullToEmpty(draft.getTitle())) + "\"," +
                            "\"description\":\"" + JsonUtil.escape(nullToEmpty(draft.getDescription())) + "\"," +
                            "\"filePath\":\"" + JsonUtil.escape(nullToEmpty(draft.getFilePath())) + "\"," +
                            "\"lastSavedAt\":\"" + DATE_TIME_FORMATTER.format(draft.getLastSavedAt()) + "\"," +
                            "\"genres\":" + genresJoiner +
                            "}");
                }
                sendJson(exchange, 200, "[" + String.join(",", items) + "]");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/published", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                List<Book> books = authorService.listPublishedBooksByAuthor(user.getUsername());
                sendJson(exchange, 200, authorPublishedBooksToJson(books));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/published-books", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                List<Book> books = authorService.listPublishedBooksByAuthor(user.getUsername());
                sendJson(exchange, 200, authorPublishedBooksToJson(books));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/published-stats", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                List<Book> books = authorService.listPublishedBooksByAuthor(user.getUsername());
                List<BorrowRecord> borrows = borrowService.listAllBorrowRecords();
                sendJson(exchange, 200, authorPublishedStatsToJson(books, borrows));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/stats-export", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                User user = requireRole(exchange, Role.AUTHOR);
                List<Book> books = authorService.listPublishedBooksByAuthor(user.getUsername());
                List<BorrowRecord> borrows = borrowService.listAllBorrowRecords();

                int totalReads = 0;
                int totalBorrows = 0;
                int totalActiveBorrows = 0;
                int totalReviews = 0;
                double ratingSum = 0.0;
                int[] ratingBuckets = new int[5];

                java.util.Set<String> authorBookIds = new java.util.LinkedHashSet<>();
                for (Book b : books) authorBookIds.add(b.getId());

                java.util.LinkedHashMap<String, Integer> readsByTitle = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, Integer> genreCounter = new java.util.LinkedHashMap<>();

                for (Book book : books) {
                    int borrowCount = 0;
                    int activeBorrowCount = 0;
                    java.util.Set<String> uniqueReaders = new java.util.LinkedHashSet<>();
                    for (BorrowRecord record : borrows) {
                        if (book.getId().equals(record.getBookId())) {
                            borrowCount++;
                            uniqueReaders.add(record.getUsername());
                            if (!record.isReturned()) activeBorrowCount++;
                        }
                    }
                    List<BookReview> reviews = bookReviewService.listReviewsForBook(book.getId());
                    BookReviewService.RatingSummary ratingSummary = bookReviewService.getRatingSummary(book.getId());
                    for (BookReview r : reviews) {
                        int rating = Math.max(1, Math.min(5, r.getRating()));
                        ratingBuckets[rating - 1]++;
                    }
                    int readCount = uniqueReaders.size();
                    int reviewCount = ratingSummary.reviewCount();
                    totalReads += readCount;
                    totalBorrows += borrowCount;
                    totalActiveBorrows += activeBorrowCount;
                    totalReviews += reviewCount;
                    ratingSum += ratingSummary.averageRating() * reviewCount;
                    readsByTitle.merge(book.getTitle(), readCount, Integer::sum);
                    List<String> genres = book.getGenres();
                    if (genres == null || genres.isEmpty()) {
                        genreCounter.merge("Unspecified", 1, Integer::sum);
                    } else {
                        for (String g : genres) {
                            String key = g == null || g.isBlank() ? "Unspecified" : g.trim();
                            genreCounter.merge(key, 1, Integer::sum);
                        }
                    }
                }

                java.util.TreeMap<String, Integer> dailyCounts = new java.util.TreeMap<>();
                for (BorrowRecord record : borrows) {
                    if (!authorBookIds.contains(record.getBookId())) continue;
                    if (record.getBorrowDate() == null) continue;
                    dailyCounts.merge(record.getBorrowDate().toString(), 1, Integer::sum);
                }

                String overallAverage = totalReviews == 0
                        ? "0.00"
                        : String.format(Locale.US, "%.2f", ratingSum / totalReviews);

                LocalDate today = LocalDate.now();
                StringBuilder csv = new StringBuilder();
                csv.append("Summary\n");
                csv.append("Metric,Value\n");
                csv.append("Published Books,").append(books.size()).append('\n');
                csv.append("Total Reads,").append(totalReads).append('\n');
                csv.append("Total Borrows,").append(totalBorrows).append('\n');
                csv.append("Active Borrows,").append(totalActiveBorrows).append('\n');
                csv.append("Total Reviews,").append(totalReviews).append('\n');
                csv.append("Average Rating,").append(overallAverage).append('\n');
                csv.append('\n');

                csv.append("Top Books by Reads\n");
                csv.append("Title,Reads\n");
                readsByTitle.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .forEach(e -> csv.append(csvEscape(e.getKey())).append(',').append(e.getValue()).append('\n'));
                csv.append('\n');

                csv.append("Review Rating Distribution\n");
                csv.append("Stars,Count\n");
                for (int i = 5; i >= 1; i--) {
                    csv.append(i).append(',').append(ratingBuckets[i - 1]).append('\n');
                }
                csv.append('\n');

                csv.append("Genre Mix\n");
                csv.append("Genre,Books\n");
                genreCounter.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .forEach(e -> csv.append(csvEscape(e.getKey())).append(',').append(e.getValue()).append('\n'));
                csv.append('\n');

                csv.append("Borrows Timeline\n");
                csv.append("Date,Count\n");
                for (Map.Entry<String, Integer> e : dailyCounts.entrySet()) {
                    csv.append(csvEscape(e.getKey())).append(',').append(e.getValue()).append('\n');
                }

                byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
                String filename = "author-stats-" + today + ".csv";
                exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/published-book/update", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String bookId = required(form, "bookId");
                String title = required(form, "title");
                List<String> genres = RequestFilters.parseCsv(form, "genres");
                String description = required(form, "description");

                Book updated = authorService.updateOwnedPublishedBook(
                        user.getUsername(),
                        bookId,
                        title,
                        genres,
                        description
                );
                notificationService.addNotification(
                        user.getUsername(),
                        "Published Book Updated",
                    "Your published book metadata was updated: " + updated.getTitle(),
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "submission", "bookId", updated.getId())
                );
                sendText(exchange, 200, "Published book updated: " + updated.getId());
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/published-book/delete", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String bookId = required(form, "bookId");
                String deletedTitle = bookService.findBookById(bookId)
                        .map(Book::getTitle)
                        .orElse(bookId);

                java.util.Set<String> affectedUsers = new java.util.LinkedHashSet<>();
                for (BorrowRecord record : borrowService.listAllBorrowRecords()) {
                    if (bookId.equals(record.getBookId())) {
                        affectedUsers.add(record.getUsername());
                    }
                }

                authorService.deleteOwnedPublishedBook(user.getUsername(), bookId);
                for (String username : affectedUsers) {
                    notificationService.addNotification(
                            username,
                            "Book Deleted",
                            "The book \"" + deletedTitle + "\" you borrowed has been removed from the catalog.",
                            NotificationPriority.HIGH,
                            null,
                            Map.of("type", "book-deleted", "bookId", bookId)
                    );
                }
                sendText(exchange, 200, "Published book deleted: " + bookId);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/published-book/bulk-delete", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                List<String> bookIds = RequestFilters.parseCsv(form, "bookIds");

                Map<String, String> bookIdToTitle = new java.util.LinkedHashMap<>();
                Map<String, java.util.Set<String>> bookIdToAffectedUsers = new java.util.LinkedHashMap<>();
                for (String bookId : bookIds == null ? java.util.List.<String>of() : bookIds) {
                    if (bookId == null || bookId.isBlank()) continue;
                    bookIdToTitle.put(bookId, bookService.findBookById(bookId).map(Book::getTitle).orElse(bookId));
                    java.util.Set<String> affected = new java.util.LinkedHashSet<>();
                    for (BorrowRecord record : borrowService.listAllBorrowRecords()) {
                        if (bookId.equals(record.getBookId())) {
                            affected.add(record.getUsername());
                        }
                    }
                    bookIdToAffectedUsers.put(bookId, affected);
                }

                AuthorService2.BulkDeleteResult result =
                        authorService.bulkDeleteOwnedPublishedBooks(user.getUsername(), bookIds);

                for (String deletedId : result.deletedIds()) {
                    String title = bookIdToTitle.getOrDefault(deletedId, deletedId);
                    for (String username : bookIdToAffectedUsers.getOrDefault(deletedId, java.util.Set.of())) {
                        notificationService.addNotification(
                                username,
                                "Book Deleted",
                                "The book \"" + title + "\" you borrowed has been removed from the catalog.",
                                NotificationPriority.HIGH,
                                null,
                                Map.of("type", "book-deleted", "bookId", deletedId)
                        );
                    }
                }

                sendJson(exchange, 200, bulkDeleteResultToJson(result));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/profile", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    AuthorService2.AuthorProfileSnapshot profile = authorService.getAuthorProfile(user.getUsername());
                    String payload = "{" +
                            "\"username\":\"" + JsonUtil.escape(profile.username()) + "\"," +
                            "\"fullName\":\"" + JsonUtil.escape(profile.fullName()) + "\"," +
                            "\"bio\":\"" + JsonUtil.escape(profile.bio()) + "\"," +
                            "\"photoUrl\":\"" + JsonUtil.escape(profilePhotoUrl(user)) + "\"" +
                            "}";
                    sendJson(exchange, 200, payload);
                    return;
                }

                Map<String, String> form;
                UploadedFile uploadedPhoto = null;
                String contentType = nullToEmpty(exchange.getRequestHeaders().getFirst("Content-Type")).toLowerCase(Locale.ROOT);
                if (contentType.startsWith("multipart/form-data")) {
                    MultipartData multipartData = readMultipartForm(exchange);
                    form = multipartData.fields();
                    uploadedPhoto = multipartData.uploadedFile("photo");
                } else {
                    form = readForm(exchange);
                }
                String fullName = required(form, "fullName");
                String bio = required(form, "bio");
                String password = form.getOrDefault("password", "");
                String currentPassword = form.getOrDefault("currentPassword", "");
                boolean passwordChanged = !nullToEmpty(password).isBlank();
                String profilePhotoPath = uploadedPhoto == null ? "" : storeProfilePhoto(user.getUsername(), user.getRole(), uploadedPhoto);

                authorService.updateAuthorProfile(user.getUsername(), user.getUsername(), fullName, bio, password, currentPassword, profilePhotoPath);
                if (!profilePhotoPath.isBlank()) {
                    user.updateProfilePhotoPath(profilePhotoPath);
                }
                if (passwordChanged) {
                    invalidateSessionsByUsername(user.getUsername());
                }
                notificationService.addNotification(
                        user.getUsername(),
                        "Author Profile Updated",
                    "Your author profile has been updated successfully.",
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "account-update")
                );
                sendText(exchange, 200, passwordChanged
                    ? "Password updated successfully. Please log in again."
                    : "Author profile updated successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/notifications", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                List<NotificationItem> items = notificationService.listByUser(user.getUsername());
                sendJson(exchange, 200, notificationsToJson(items));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/notifications/summary", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                List<NotificationItem> items = notificationService.listByUser(user.getUsername());
                sendJson(exchange, 200, notificationsSummaryToJson(items));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/notifications/read", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String notificationId = required(form, "notificationId");
                NotificationItem item = notificationService.markAsRead(user.getUsername(), notificationId);

                String payload = "{" +
                        "\"id\":\"" + JsonUtil.escape(item.getId()) + "\"," +
                        "\"status\":\"read\"," +
                        "\"message\":\"Notification marked as read.\"" +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/preview", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String title = required(form, "title");
                List<String> genres = RequestFilters.parseCsv(form, "genres");
                String description = required(form, "description");
                String preview = authorService.previewBook(title, genres, description);
                sendText(exchange, 200, preview);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/submit/generate-summary", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String title = required(form, "title");
                List<String> genres = RequestFilters.parseCsv(form, "genres");
                String note = RequestFilters.getTrimmed(form, "note", "");
                String content = RequestFilters.getTrimmed(form, "content", "");
                String summaryLevel = RequestFilters.getTrimmed(form, "summaryLevel", "medium");
                int summaryWordLimit = parseSummaryWordLimit(summaryLevel);

                String summary = generateAuthorSubmissionSummary(user.getFullName(), title, genres, note, content, summaryWordLimit);
                sendJson(exchange, 200, "{" +
                        "\"summary\":\"" + JsonUtil.escape(summary) + "\"," +
                        "\"message\":\"Summary generated! You can edit it or click Submit to proceed.\"" +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/submit", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                String contentType = nullToEmpty(exchange.getRequestHeaders().getFirst("Content-Type")).toLowerCase();
                Map<String, String> form;
                UploadedFile uploadedFile = null;
                UploadedFile uploadedCoverImage = null;

                if (contentType.startsWith("multipart/form-data")) {
                    MultipartData multipartData = readMultipartForm(exchange);
                    form = multipartData.fields();
                    uploadedFile = multipartData.uploadedFile("file");
                    uploadedCoverImage = multipartData.uploadedFile("coverImage");
                } else {
                    form = readForm(exchange);
                }

                String title = required(form, "title");
                List<String> genres = RequestFilters.parseCsv(form, "genres");
                String description = form.getOrDefault("description", "").trim();
                String filePath = form.getOrDefault("filePath", "").trim();
                String coverImagePath = form.getOrDefault("coverImagePath", "").trim();

                String submissionFileReference;
                if (uploadedFile != null) {
                    fileService.validateSubmissionFile(uploadedFile.path().toString());
                    submissionFileReference = uploadedFile.path().toString();
                } else {
                    filePath = required(form, "filePath");
                    fileService.validateSubmissionFile(filePath);
                    submissionFileReference = filePath;
                }

                String submissionCoverImageReference = "";
                if (uploadedCoverImage != null) {
                    fileService.validateCoverImageFile(uploadedCoverImage.path().toString());
                    submissionCoverImageReference = uploadedCoverImage.path().toString();
                } else if (!coverImagePath.isEmpty()) {
                    fileService.validateCoverImageFile(coverImagePath);
                    submissionCoverImageReference = coverImagePath;
                }

                BookSubmission2 submission = authorService.publishBook(
                        user.getUsername(),
                        title,
                        genres,
                        description,
                        submissionFileReference,
                        submissionCoverImageReference
                );
                authorDraftService.clearDraft(user.getUsername(), title);
                notificationService.addNotification(
                        user.getUsername(),
                        "Submission Created",
                    "Your book submission is now pending librarian review.",
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "submission", "submissionId", submission.getId())
                );
                sendText(exchange, 200, "Submission created successfully. Summary finalized and ready for review.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/submissions", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                List<BookSubmission2> items = authorService.listSubmissionsByAuthor(user.getUsername());
                sendJson(exchange, 200, authorSubmissionsToJson(items));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/submission/update", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);

                String submissionId = required(form, "submissionId");
                String title = required(form, "title");
                List<String> genres = RequestFilters.parseCsv(form, "genres");
                String description = required(form, "description");
                String filePath = RequestFilters.getTrimmed(form, "filePath", "");
                if (!filePath.isEmpty()) {
                    fileService.validateSubmissionFile(filePath);
                }

                BookSubmission2 updated = authorService.updatePendingSubmission(
                        user.getUsername(),
                        submissionId,
                        title,
                        genres,
                        description,
                        filePath
                );
                notificationService.addNotification(
                        user.getUsername(),
                        "Submission Updated",
                    "Your pending submission was updated: " + updated.getTitle(),
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "submission", "submissionId", updated.getId())
                );
                sendText(exchange, 200, "Submission updated successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/submission/delete", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String submissionId = required(form, "submissionId");

                authorService.deletePendingSubmission(user.getUsername(), submissionId);
                notificationService.addNotification(
                        user.getUsername(),
                        "Submission Deleted",
                    "Your pending submission was deleted.",
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "submission")
                );
                sendText(exchange, 200, "Submission deleted successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/submission/bulk-delete", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                List<String> submissionIds = RequestFilters.parseCsv(form, "submissionIds");
                AuthorService2.BulkDeleteResult result =
                        authorService.bulkDeletePendingSubmissions(user.getUsername(), submissionIds);
                if (!result.deletedIds().isEmpty()) {
                    notificationService.addNotification(
                            user.getUsername(),
                            "Submissions Deleted",
                            "Your pending submissions were deleted. Count: " + result.deletedIds().size(),
                            NotificationPriority.NORMAL,
                            null,
                            Map.of("type", "submission")
                    );
                }
                sendJson(exchange, 200, bulkDeleteResultToJson(result));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/submission/read", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> values = "POST".equalsIgnoreCase(exchange.getRequestMethod())
                        ? readForm(exchange)
                        : readQuery(exchange.getRequestURI());
                String submissionId = required(values, "submissionId");

                AuthorService2.FilePreview preview = authorService.readOwnedSubmissionFilePreview(user.getUsername(), submissionId);
                sendJson(exchange, 200, filePreviewToJson(preview));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/submission/file", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String submissionId = required(query, "submissionId");

                AuthorService2.FilePreview preview = authorService.readOwnedSubmissionFilePreview(user.getUsername(), submissionId);
                Path file = Paths.get(preview.filePath());
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Submission file not found on server.");
                }

                byte[] bytes = Files.readAllBytes(file);
                String fileName = file.getFileName().toString().toLowerCase();
                exchange.getResponseHeaders().set("Content-Type", detectContentType(fileName));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/published-book/read", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> values = "POST".equalsIgnoreCase(exchange.getRequestMethod())
                        ? readForm(exchange)
                        : readQuery(exchange.getRequestURI());
                String bookId = required(values, "bookId");

                AuthorService2.FilePreview preview = authorService.readOwnedPublishedBookFilePreview(user.getUsername(), bookId);
                sendJson(exchange, 200, filePreviewToJson(preview));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/author/published-book/file", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.AUTHOR);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String bookId = required(query, "bookId");

                AuthorService2.FilePreview preview = authorService.readOwnedPublishedBookFilePreview(user.getUsername(), bookId);
                Path file = Paths.get(preview.filePath());
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Published book file not found on server.");
                }

                byte[] bytes = Files.readAllBytes(file);
                String fileName = file.getFileName().toString().toLowerCase();
                exchange.getResponseHeaders().set("Content-Type", detectContentType(fileName));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/pending", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String keyword = RequestFilters.getTrimmed(query, "q", "");
                String status = parseLibrarianSubmissionStatus(query);
                String sortBy = parseLibrarianSubmissionSortBy(query);
                String sortDir = parseLibrarianSubmissionSortDir(query);

                List<BookSubmission2> items = librarianService.querySubmissionsForReview(
                        keyword,
                        status,
                        sortBy,
                        sortDir
                );
                sendJson(exchange, 200, librarianSubmissionsToJson(items));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/approved-books", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String genreFilter = RequestFilters.getTrimmed(query, "genre", "");
                String authorFilter = RequestFilters.getTrimmed(query, "author", "").toLowerCase(Locale.ROOT);
                String statusFilter = RequestFilters.getTrimmed(query, "status", "").toLowerCase(Locale.ROOT);
                List<Book> items = bookService.listApprovedBooksForLibrarian();
                List<String> jsonItems = new ArrayList<>();
                for (Book book : items) {
                    if (!genreFilter.isEmpty()) {
                        boolean matched = false;
                        for (String g : book.getGenres()) {
                            if (g.equalsIgnoreCase(genreFilter)) {
                                matched = true;
                                break;
                            }
                        }
                        if (!matched) {
                            continue;
                        }
                    }
                    if (!authorFilter.isEmpty()) {
                        String authorFullName = book.getAuthorFullName() == null ? "" : book.getAuthorFullName().toLowerCase(Locale.ROOT);
                        String authorUsername = book.getAuthorUsername() == null ? "" : book.getAuthorUsername().toLowerCase(Locale.ROOT);
                        if (!authorFullName.contains(authorFilter) && !authorUsername.contains(authorFilter)) {
                            continue;
                        }
                    }
                    if (statusFilter.equals("available") && !book.isAvailable()) {
                        continue;
                    }
                    if (statusFilter.equals("unavailable") && book.isAvailable()) {
                        continue;
                    }
                    jsonItems.add(librarianPublishedBookToJson(book));
                }
                sendJson(exchange, 200, "[" + String.join(",", jsonItems) + "]");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/published-book/generate-description", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String title = required(form, "title");
                String authorNames = RequestFilters.getTrimmed(form, "authorNames", "");
                List<String> genres = validateSupportedGenres(RequestFilters.parseCsv(form, "genres"));

                String generated = generateBookDescriptionSuggestion(title, authorNames, genres);
                sendJson(exchange, 200, "{" +
                        "\"description\":\"" + JsonUtil.escape(generated) + "\"" +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/published-book/add", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.LIBRARIAN);
                String contentType = nullToEmpty(exchange.getRequestHeaders().getFirst("Content-Type")).toLowerCase(Locale.ROOT);
                Map<String, String> form;
                UploadedFile uploadedFile = null;
                UploadedFile uploadedCoverImage = null;

                if (contentType.startsWith("multipart/form-data")) {
                    MultipartData multipartData = readMultipartForm(exchange);
                    form = multipartData.fields();
                    uploadedFile = multipartData.uploadedFile("file");
                    uploadedCoverImage = multipartData.uploadedFile("coverImage");
                } else {
                    form = readForm(exchange);
                }

                String title = required(form, "title");
                String authorNames = required(form, "authorNames");
                String authorUsername = RequestFilters.getTrimmed(form, "authorUsername", "");
                List<String> genres = validateSupportedGenres(RequestFilters.parseCsv(form, "genres"));
                String description = required(form, "description");
                String filePath = RequestFilters.getTrimmed(form, "filePath", "");
                String coverImagePath = RequestFilters.getTrimmed(form, "coverImagePath", "");

                String bookFileReference;
                if (uploadedFile != null) {
                    fileService.validateSubmissionFile(uploadedFile.path().toString());
                    bookFileReference = uploadedFile.path().toString();
                } else {
                    if (filePath.isEmpty()) {
                        throw new IllegalArgumentException("Book file is required.");
                    }
                    fileService.validateSubmissionFile(filePath);
                    bookFileReference = filePath;
                }

                String coverReference = "";
                if (uploadedCoverImage != null) {
                    fileService.validateCoverImageFile(uploadedCoverImage.path().toString());
                    coverReference = uploadedCoverImage.path().toString();
                } else if (!coverImagePath.isEmpty()) {
                    fileService.validateCoverImageFile(coverImagePath);
                    coverReference = coverImagePath;
                }

                Book book = new Book(title, authorUsername, authorNames, genres, description);
                String fileName = bookFileReference.toLowerCase(Locale.ROOT);
                book.setFileMetadata(bookFileReference, detectContentType(fileName));
                book.setCoverImagePath(coverReference);
                book.approve(LocalDate.now());
                bookService.getBookRepository().save(book);

                notificationService.addNotification(
                        user.getUsername(),
                        "Published Book Added",
                        "A new published book was added by librarian: " + book.getTitle(),
                        NotificationPriority.NORMAL,
                        null,
                        Map.of("type", "submission", "bookId", book.getId())
                );
                sendText(exchange, 200, "Published book added successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/published-book/update", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.LIBRARIAN);
                String contentType = nullToEmpty(exchange.getRequestHeaders().getFirst("Content-Type")).toLowerCase(Locale.ROOT);
                Map<String, String> form;
                UploadedFile uploadedFile = null;
                UploadedFile uploadedCoverImage = null;

                if (contentType.startsWith("multipart/form-data")) {
                    MultipartData multipartData = readMultipartForm(exchange);
                    form = multipartData.fields();
                    uploadedFile = multipartData.uploadedFile("file");
                    uploadedCoverImage = multipartData.uploadedFile("coverImage");
                } else {
                    form = readForm(exchange);
                }

                String bookId = required(form, "bookId");
                String title = required(form, "title");
                String authorNames = required(form, "authorNames");
                List<String> genres = validateSupportedGenres(RequestFilters.parseCsv(form, "genres"));
                String description = required(form, "description");
                String filePath = RequestFilters.getTrimmed(form, "filePath", "");
                String coverImagePath = RequestFilters.getTrimmed(form, "coverImagePath", "");

                Book existing = bookService.findBookById(bookId)
                        .orElseThrow(() -> new IllegalArgumentException("Book not found."));
                if (!existing.isApproved()) {
                    throw new IllegalArgumentException("Only approved books can be edited.");
                }

                if (!authorNames.trim().equals(existing.getAuthorFullName())) {
                    throw new IllegalArgumentException("Author name cannot be changed for an existing published book.");
                }

                // Slice 6: capture pre-update snapshot for version-history diff.
                String oldTitle = nullToEmpty(existing.getTitle());
                String oldAuthorFullName = nullToEmpty(existing.getAuthorFullName());
                String oldDescription = nullToEmpty(existing.getSummary());
                String oldGenresCsv = existing.getGenres() == null ? "" : String.join(",", existing.getGenres());

                existing.updateMetadata(title, genres, description);

                String effectiveFilePath = filePath;
                if (uploadedFile != null) {
                    fileService.validateSubmissionFile(uploadedFile.path().toString());
                    effectiveFilePath = uploadedFile.path().toString();
                }
                if (effectiveFilePath.isEmpty()) {
                    effectiveFilePath = existing.getFilePath();
                }
                if (effectiveFilePath.isEmpty()) {
                    throw new IllegalArgumentException("Book file is required.");
                }
                fileService.validateSubmissionFile(effectiveFilePath);
                existing.setFileMetadata(effectiveFilePath, detectContentType(effectiveFilePath.toLowerCase(Locale.ROOT)));

                String effectiveCoverPath = coverImagePath;
                if (uploadedCoverImage != null) {
                    fileService.validateCoverImageFile(uploadedCoverImage.path().toString());
                    effectiveCoverPath = uploadedCoverImage.path().toString();
                }
                if (!effectiveCoverPath.isEmpty()) {
                    fileService.validateCoverImageFile(effectiveCoverPath);
                    existing.setCoverImagePath(effectiveCoverPath);
                }

                bookService.getBookRepository().save(existing);

                // Slice 6: diff and append to in-memory version history (cap last 50).
                String newTitle = nullToEmpty(existing.getTitle());
                String newAuthorFullName = nullToEmpty(existing.getAuthorFullName());
                String newDescription = nullToEmpty(existing.getSummary());
                String newGenresCsv = existing.getGenres() == null ? "" : String.join(",", existing.getGenres());
                recordBookVersionIfChanged(existing.getId(), user.getUsername(), "title", oldTitle, newTitle);
                recordBookVersionIfChanged(existing.getId(), user.getUsername(), "authorFullName", oldAuthorFullName, newAuthorFullName);
                recordBookVersionIfChanged(existing.getId(), user.getUsername(), "description", oldDescription, newDescription);
                recordBookVersionIfChanged(existing.getId(), user.getUsername(), "genres", oldGenresCsv, newGenresCsv);

                notificationService.addNotification(
                        user.getUsername(),
                        "Published Book Updated",
                        "A published book was updated by librarian: " + existing.getTitle(),
                        NotificationPriority.NORMAL,
                        null,
                        Map.of("type", "submission", "bookId", existing.getId())
                );
                sendText(exchange, 200, "Published book updated successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        // Slice 6: bulk delete published books (3.8 NTH Bulk Operations).
        server.createContext("/api/librarian/published-books-bulk-delete", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String csv = RequestFilters.getTrimmed(form, "bookIds", "");
                if (csv.isEmpty()) {
                    throw new IllegalArgumentException("bookIds is required.");
                }
                int deleted = 0;
                List<String> failedItems = new ArrayList<>();
                for (String raw : csv.split(",")) {
                    String id = raw.trim();
                    if (id.isEmpty()) continue;
                    try {
                        Book existing = bookService.findBookById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Book not found."));
                        if (!existing.isApproved()) {
                            throw new IllegalArgumentException("Only approved books can be deleted.");
                        }
                        bookService.getBookRepository().deleteById(id);
                        bookVersions.remove(id);
                        deleted++;
                    } catch (Exception ex) {
                        failedItems.add("{\"id\":\"" + JsonUtil.escape(id) + "\",\"reason\":\"" + JsonUtil.escape(ex.getMessage() == null ? "Failed" : ex.getMessage()) + "\"}");
                    }
                }
                String payload = "{\"deleted\":" + deleted + ",\"failed\":[" + String.join(",", failedItems) + "]}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        // Slice 6: version history per published book (3.8 NTH Version History).
        server.createContext("/api/librarian/published-book-history", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String bookId = RequestFilters.getTrimmed(query, "bookId", "");
                if (bookId.isEmpty()) {
                    throw new IllegalArgumentException("bookId is required.");
                }
                List<BookVersion> entries = bookVersions.getOrDefault(bookId, new ArrayList<>());
                List<BookVersion> sorted = new ArrayList<>(entries);
                sorted.sort((a, b) -> b.timestamp().compareTo(a.timestamp()));
                List<String> jsonItems = new ArrayList<>();
                for (BookVersion v : sorted) {
                    jsonItems.add("{" +
                            "\"timestamp\":\"" + JsonUtil.escape(v.timestamp()) + "\"," +
                            "\"editorUsername\":\"" + JsonUtil.escape(v.editorUsername()) + "\"," +
                            "\"fieldName\":\"" + JsonUtil.escape(v.fieldName()) + "\"," +
                            "\"oldValue\":\"" + JsonUtil.escape(v.oldValue()) + "\"," +
                            "\"newValue\":\"" + JsonUtil.escape(v.newValue()) + "\"" +
                            "}");
                }
                sendJson(exchange, 200, "[" + String.join(",", jsonItems) + "]");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        // Slice 6: library admin stats (3.8 NTH Admin Tools).
        server.createContext("/api/librarian/library-admin-stats", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                requireRole(exchange, Role.LIBRARIAN);
                List<Book> approved = bookService.listApprovedBooksForLibrarian();
                int totalBooks = approved.size();
                java.util.Set<String> authors = new java.util.HashSet<>();
                java.util.Set<String> genres = new java.util.HashSet<>();
                for (Book book : approved) {
                    String au = book.getAuthorFullName();
                    if (au == null || au.isBlank()) au = book.getAuthorUsername();
                    if (au != null && !au.isBlank()) authors.add(au.trim().toLowerCase(Locale.ROOT));
                    if (book.getGenres() != null) {
                        for (String g : book.getGenres()) {
                            if (g != null && !g.isBlank()) genres.add(g.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                }
                int totalAuthors = authors.size();
                int genreCoverage = genres.size();
                double avg = totalAuthors == 0 ? 0.0 : ((double) totalBooks / (double) totalAuthors);
                String avgFmt = String.format(Locale.ROOT, "%.2f", avg);
                String payload = "{" +
                        "\"totalBooks\":" + totalBooks + "," +
                        "\"totalAuthors\":" + totalAuthors + "," +
                        "\"genreCoverage\":" + genreCoverage + "," +
                        "\"avgBooksPerAuthor\":" + avgFmt +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/approved-book/copies", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String bookId = required(form, "bookId");
                int totalCopies = RequestFilters.parseIntInRange(form, "totalCopies", 1, 1, 1000);

                Book book = bookService.findBookById(bookId)
                        .orElseThrow(() -> new IllegalArgumentException("Book not found."));
                if (!book.isApproved()) {
                    throw new IllegalArgumentException("Only approved books can update copies.");
                }

                book.setTotalCopies(totalCopies);
                sendText(exchange, 200, "Book copies updated successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/borrowed-records", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                List<BorrowRecord> records = borrowService.listAllBorrowRecords();
                List<String> jsonItems = new ArrayList<>();

                for (BorrowRecord record : records) {
                    String title = bookService.findBookById(record.getBookId())
                            .map(Book::getTitle)
                            .orElse(record.getBookId());
                    String returnDate = record.getReturnedDate() == null ? "" : record.getReturnedDate().toString();
                    boolean returned = record.isReturned();
                    boolean overdue = !returned && record.getDueDate().isBefore(java.time.LocalDate.now());
                    String status = record.isReturned() ? "Returned" : "Borrowed";

                    jsonItems.add("{" +
                            "\"borrowId\":\"" + JsonUtil.escape(record.getId()) + "\"," +
                            "\"bookId\":\"" + JsonUtil.escape(record.getBookId()) + "\"," +
                            "\"bookTitle\":\"" + JsonUtil.escape(title) + "\"," +
                            "\"borrowerUsername\":\"" + JsonUtil.escape(record.getUsername()) + "\"," +
                            "\"borrowDate\":\"" + record.getBorrowDate() + "\"," +
                            "\"dueDate\":\"" + record.getDueDate() + "\"," +
                            "\"returnDate\":\"" + JsonUtil.escape(returnDate) + "\"," +
                        "\"status\":\"" + status + "\"," +
                        "\"returned\":" + returned + "," +
                        "\"overdue\":" + overdue +
                            "}");
                }

                sendJson(exchange, 200, "[" + String.join(",", jsonItems) + "]");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/borrowed-records-export", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String keyword = RequestFilters.getTrimmed(query, "keyword", "").toLowerCase(Locale.ROOT);
                String tab = RequestFilters.getTrimmed(query, "tab", "all").toLowerCase(Locale.ROOT);
                java.time.LocalDate today = java.time.LocalDate.now();

                StringBuilder csv = new StringBuilder();
                csv.append("Borrow ID,Book ID,Book Title,Borrower Username,Borrow Date,Due Date,Return Date,Status\n");

                for (BorrowRecord record : borrowService.listAllBorrowRecords()) {
                    String title = bookService.findBookById(record.getBookId())
                            .map(Book::getTitle)
                            .orElse(record.getBookId());
                    boolean returned = record.isReturned();
                    boolean overdue = !returned && record.getDueDate() != null && record.getDueDate().isBefore(today);
                    String status = returned ? "Returned" : "Borrowed";

                    if (!keyword.isEmpty()) {
                        String haystack = (title + " " + record.getUsername() + " " + record.getId()).toLowerCase(Locale.ROOT);
                        if (!haystack.contains(keyword)) continue;
                    }
                    if (tab.equals("active") && (returned || overdue)) continue;
                    if (tab.equals("overdue") && (returned || !overdue)) continue;
                    if (tab.equals("returned") && !returned) continue;

                    csv.append(csvEscape(record.getId())).append(',')
                            .append(csvEscape(record.getBookId())).append(',')
                            .append(csvEscape(title)).append(',')
                            .append(csvEscape(record.getUsername())).append(',')
                            .append(csvEscape(String.valueOf(record.getBorrowDate()))).append(',')
                            .append(csvEscape(String.valueOf(record.getDueDate()))).append(',')
                            .append(csvEscape(record.getReturnedDate() == null ? "" : record.getReturnedDate().toString())).append(',')
                            .append(csvEscape(status)).append('\n');
                }

                byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
                String filename = "borrowed-records-" + today + ".csv";
                exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/submission/read", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String submissionId = required(query, "submissionId");

                BookSubmission2 submission = librarianService.getSubmissionByIdForReview(submissionId);
                String submissionPath = nullToEmpty(submission.getFileName()).trim();
                String previewType = resolvePreviewTypeFromPath(submissionPath);
                String previewText = "";
                String fileUrl = "";

                if (!submissionPath.isBlank()) {
                    Path submissionFile = Paths.get(submissionPath);
                    if (Files.isRegularFile(submissionFile)) {
                        FileService.FilePreviewDetails details = fileService.readPreviewDetails(submissionPath);
                        previewType = details.previewType();
                        previewText = details.previewText();
                        fileUrl = "/api/librarian/submission/file?submissionId=" + JsonUtil.escape(submission.getId());
                    } else {
                        previewText = "Submission file is not available on server. Please verify and re-upload the file if needed.";
                    }
                } else {
                    previewText = "Submission file path is missing.";
                }

                String coverImageUrl = "";
                String coverPath = nullToEmpty(submission.getCoverImagePath()).trim();
                if (!coverPath.isBlank()) {
                    Path coverFile = Paths.get(coverPath);
                    if (Files.isRegularFile(coverFile)) {
                        coverImageUrl = "/api/librarian/submission/cover?submissionId=" + JsonUtil.escape(submission.getId());
                    }
                }

                String payload = "{" +
                        "\"submissionId\":\"" + JsonUtil.escape(submission.getId()) + "\"," +
                        "\"title\":\"" + JsonUtil.escape(submission.getTitle()) + "\"," +
                        "\"authorFullName\":\"" + JsonUtil.escape(submission.getAuthorFullName()) + "\"," +
                        "\"previewType\":\"" + JsonUtil.escape(previewType) + "\"," +
                        "\"previewText\":\"" + JsonUtil.escape(previewText) + "\"," +
                        "\"fileUrl\":\"" + JsonUtil.escape(fileUrl) + "\"," +
                        "\"coverImageUrl\":\"" + JsonUtil.escape(coverImageUrl) + "\"" +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/submission/file", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String submissionId = required(query, "submissionId");

                BookSubmission2 submission = librarianService.getSubmissionByIdForReview(submissionId);
                Path file = Paths.get(submission.getFileName());
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Submission file not found on server.");
                }

                byte[] bytes = Files.readAllBytes(file);
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                exchange.getResponseHeaders().set("Content-Type", detectContentType(fileName));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/submission/cover", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String submissionId = required(query, "submissionId");

                BookSubmission2 submission = librarianService.getSubmissionByIdForReview(submissionId);
                String coverPath = required(Map.of("coverPath", nullToEmpty(submission.getCoverImagePath()).trim()), "coverPath");
                Path file = Paths.get(coverPath);
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Cover image not found on server.");
                }

                byte[] bytes = Files.readAllBytes(file);
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                exchange.getResponseHeaders().set("Content-Type", detectContentType(fileName));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/users", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User librarian = requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String keyword = RequestFilters.getTrimmed(query, "q", "");
                String role = parseManagedUserRole(query);
                String status = parseManagedUserStatus(query);

                List<User> users = librarianService.listUsersForManagement(keyword, role, status);
                List<BorrowRecord> borrows = borrowService.listAllBorrowRecords();
                sendJson(exchange, 200, managedUsersToJson(users, borrows, librarian.getUsername()));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/users-create", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }
            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String username = required(form, "username");
                String fullName = required(form, "fullName");
                String password = required(form, "password");
                String roleRaw = required(form, "role").trim().toUpperCase(Locale.ROOT);
                Role role;
                try {
                    role = Role.valueOf(roleRaw);
                } catch (IllegalArgumentException ex) {
                    sendText(exchange, 400, "Invalid role.");
                    return;
                }
                String bio = nullToEmpty(form.get("bio"));
                String employeeId = nullToEmpty(form.get("employeeId"));
                User created = librarianService.createManagedUser(username, fullName, password, role, bio, employeeId);
                sendJson(exchange, 200, "{" +
                        "\"message\":\"User created.\"," +
                        "\"username\":\"" + JsonUtil.escape(created.getUsername()) + "\"," +
                        "\"role\":\"" + created.getRole() + "\"" +
                        "}");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/users/profile", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String username = required(query, "username");

                LibrarianService3.ManagedUserProfileSnapshot profile = librarianService.getManagedUserProfile(username);
                String payload = "{" +
                        "\"username\":\"" + JsonUtil.escape(profile.username()) + "\"," +
                        "\"role\":\"" + profile.role().name() + "\"," +
                        "\"fullName\":\"" + JsonUtil.escape(profile.fullName()) + "\"," +
                        "\"active\":" + profile.active() + "," +
                        "\"bio\":\"" + JsonUtil.escape(profile.bio()) + "\"," +
                        "\"employeeId\":\"" + JsonUtil.escape(profile.employeeId()) + "\"" +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/users/update", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User librarian = requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String username = required(form, "username");
                String fullName = required(form, "fullName");
                String bio = form.getOrDefault("bio", "");
                String employeeId = form.getOrDefault("employeeId", "");
                String password = form.getOrDefault("password", "");

                LibrarianService3.ManagedUserProfileSnapshot updated = librarianService.updateManagedUserProfile(
                        librarian.getUsername(),
                        username,
                        fullName,
                        bio,
                        employeeId,
                        password
                );

                if (updated.passwordUpdated()) {
                    invalidateSessionsByUsername(updated.username());
                }

                if (updated.username().equals(librarian.getUsername()) && !updated.passwordUpdated()) {
                    String sessionId = nullToEmpty(exchange.getRequestHeaders().getFirst(SESSION_HEADER)).trim();
                    if (!sessionId.isEmpty()) {
                        librarian.updateFullName(updated.fullName());
                        sessions.put(sessionId, librarian);
                        sessionLastActiveAtMs.put(sessionId, Instant.now().toEpochMilli());
                        refreshSessionSnapshot();
                    }
                }

                appendUserActivity(updated.username(), "Account profile updated by librarian " + librarian.getUsername() + ".");
                if (updated.passwordUpdated()) {
                    appendUserActivity(updated.username(), "Account password reset by librarian " + librarian.getUsername() + ".");
                }
                appendUserActivity(librarian.getUsername(), "Updated account profile for " + updated.username() + ".");

                notificationService.addNotification(
                        updated.username(),
                        "Account Updated by Librarian",
                        "Your account profile was updated by librarian " + librarian.getUsername() + ".",
                        NotificationPriority.HIGH,
                        null,
                        Map.of("type", "account-update", "actor", librarian.getUsername())
                );
                notificationService.addNotification(
                        librarian.getUsername(),
                        "Managed User Updated",
                        "You updated account profile for " + updated.username() + ".",
                        NotificationPriority.NORMAL,
                        null,
                        Map.of("type", "account-update", "target", updated.username())
                );
                sendText(exchange, 200, "User updated successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/users/deactivate", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User librarian = requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String username = required(form, "username");
                String activeRaw = RequestFilters.getTrimmed(form, "active", "false");
                boolean active = Boolean.parseBoolean(activeRaw);

                User updated = librarianService.setManagedUserActive(librarian.getUsername(), username, active);
                if (!updated.isActive()) {
                    invalidateSessionsByUsername(updated.getUsername());
                }

                String actionLabel = updated.isActive() ? "activated" : "deactivated";
                appendUserActivity(updated.getUsername(), "Account was " + actionLabel + " by librarian " + librarian.getUsername() + ".");
                appendUserActivity(librarian.getUsername(), "" + actionLabel.substring(0, 1).toUpperCase() + actionLabel.substring(1) + " account " + updated.getUsername() + ".");

                notificationService.addNotification(
                        updated.getUsername(),
                        "Account Status Updated",
                        "Your account was " + actionLabel + " by librarian " + librarian.getUsername() + ".",
                        NotificationPriority.HIGH,
                        null,
                        Map.of("type", "account-update", "status", actionLabel)
                );
                sendText(exchange, 200, "User " + actionLabel + " successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/users/bulk", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User librarian = requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String action = required(form, "action").toLowerCase();
                List<String> usernames = RequestFilters.parseCsv(form, "usernames");

                boolean active;
                if ("deactivate".equals(action)) {
                    active = false;
                } else if ("activate".equals(action)) {
                    active = true;
                } else {
                    throw new IllegalArgumentException("action must be one of: deactivate, activate.");
                }

                int changed = librarianService.setManagedUsersActiveBulk(librarian.getUsername(), usernames, active);
                if (!active) {
                    for (String username : usernames) {
                        invalidateSessionsByUsername(username);
                    }
                }

                String actionLabel = active ? "activated" : "deactivated";
                appendUserActivity(librarian.getUsername(), "Bulk " + actionLabel + " " + changed + " account(s).");
                for (String username : usernames) {
                    appendUserActivity(username, "Account was " + actionLabel + " via librarian bulk action.");
                    notificationService.addNotification(
                            username,
                            "Account Status Updated",
                            "Your account was " + actionLabel + " via librarian bulk action.",
                            NotificationPriority.HIGH,
                            null,
                            Map.of("type", "account-update", "status", actionLabel)
                    );
                }

                sendText(exchange, 200, "Bulk action complete. Updated " + changed + " account(s).");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/profile", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.LIBRARIAN);
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    LibrarianService3.LibrarianProfileSnapshot profile = librarianService.getLibrarianProfile(user.getUsername());
                    String payload = "{" +
                            "\"username\":\"" + JsonUtil.escape(profile.username()) + "\"," +
                            "\"fullName\":\"" + JsonUtil.escape(profile.fullName()) + "\"," +
                            "\"employeeId\":\"" + JsonUtil.escape(profile.employeeId()) + "\"," +
                            "\"photoUrl\":\"" + JsonUtil.escape(profilePhotoUrl(user)) + "\"" +
                            "}";
                    sendJson(exchange, 200, payload);
                    return;
                }

                Map<String, String> form;
                UploadedFile uploadedPhoto = null;
                String contentType = nullToEmpty(exchange.getRequestHeaders().getFirst("Content-Type")).toLowerCase(Locale.ROOT);
                if (contentType.startsWith("multipart/form-data")) {
                    MultipartData multipartData = readMultipartForm(exchange);
                    form = multipartData.fields();
                    uploadedPhoto = multipartData.uploadedFile("photo");
                } else {
                    form = readForm(exchange);
                }
                String fullName = required(form, "fullName");
                String employeeId = required(form, "employeeId");
                String password = form.getOrDefault("password", "");
                String currentPassword = form.getOrDefault("currentPassword", "");
                boolean passwordChanged = !nullToEmpty(password).isBlank();
                String profilePhotoPath = uploadedPhoto == null ? "" : storeProfilePhoto(user.getUsername(), user.getRole(), uploadedPhoto);

                LibrarianService3.LibrarianProfileSnapshot updated = librarianService.updateLibrarianProfile(
                        user.getUsername(),
                        user.getUsername(),
                        fullName,
                        employeeId,
                    password,
                    currentPassword,
                    profilePhotoPath
                );
                if (!profilePhotoPath.isBlank()) {
                    user.updateProfilePhotoPath(profilePhotoPath);
                }

                notificationService.addNotification(
                    user.getUsername(),
                    "Librarian Profile Updated",
                    "Your librarian profile has been updated successfully.",
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "account-update")
                );

                if (passwordChanged) {
                    invalidateSessionsByUsername(user.getUsername());
                } else {
                    String sessionId = nullToEmpty(exchange.getRequestHeaders().getFirst(SESSION_HEADER)).trim();
                    if (!sessionId.isEmpty()) {
                        user.updateFullName(updated.fullName());
                        sessions.put(sessionId, user);
                        sessionLastActiveAtMs.put(sessionId, Instant.now().toEpochMilli());
                        refreshSessionSnapshot();
                    }
                }

                sendText(exchange, 200, passwordChanged
                        ? "Password updated successfully. Please log in again."
                        : "Librarian profile updated successfully.");
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/notifications", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.LIBRARIAN);
                List<NotificationItem> items = notificationService.listByUser(user.getUsername());
                sendJson(exchange, 200, notificationsToJson(items));
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/notifications/read", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String notificationId = required(form, "notificationId");
                NotificationItem item = notificationService.markAsRead(user.getUsername(), notificationId);

                String payload = "{" +
                        "\"id\":\"" + JsonUtil.escape(item.getId()) + "\"," +
                        "\"status\":\"read\"," +
                        "\"message\":\"Notification marked as read.\"" +
                        "}";
                sendJson(exchange, 200, payload);
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });

        server.createContext("/api/librarian/review", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.LIBRARIAN);
                Map<String, String> form = readForm(exchange);
                String submissionId = required(form, "submissionId");
                String action = required(form, "action").toLowerCase();
                String comment = nullToEmpty(form.getOrDefault("comment", form.getOrDefault("librarianComment", ""))).trim();
                String reason = nullToEmpty(form.getOrDefault("reason", form.getOrDefault("rejectionReason", ""))).trim();
                boolean sendFeedback = Boolean.parseBoolean(RequestFilters.getTrimmed(form, "sendFeedback", "true"));

                if ("approve".equals(action)) {
                    BookSubmission2 approved = librarianService.approveSubmission(submissionId, comment);
                    if (sendFeedback) {
                        String safeComment = nullToEmpty(comment).trim();
                        String notificationMessage = safeComment.isBlank()
                                ? "Your submission \"" + approved.getTitle() + "\" was approved by a librarian."
                                : "Your submission \"" + approved.getTitle() + "\" was approved. Comment: " + safeComment;
                        notificationService.addNotification(
                                approved.getAuthorUsername(),
                                "Submission Approved",
                        notificationMessage,
                        NotificationPriority.NORMAL,
                        null,
                        Map.of("type", "submission", "submissionId", approved.getId())
                        );
                    }
                    sendText(exchange, 200, "Submission approved.");
                } else if ("reject".equals(action)) {
                    String storedComment = comment.isBlank() ? "No comment provided." : comment;
                    String storedReason = reason.isBlank() ? "Unspecified" : reason;
                    BookSubmission2 rejected = librarianService.rejectSubmission(submissionId, storedComment, storedReason);

                    // Prevent accidental reason leakage when older clients mirror reason into comment.
                    String notificationComment = comment;
                    if (!reason.isBlank() && reason.equalsIgnoreCase(comment)) {
                        notificationComment = "";
                    }
                    String notificationMessage;
                    if (!notificationComment.isBlank()) {
                        notificationMessage = "Your submission \"" + rejected.getTitle() + "\" was rejected. Comment: " + notificationComment;
                    } else {
                        notificationMessage = "Your submission \"" + rejected.getTitle() + "\" was rejected by a librarian.";
                    }
                    notificationService.addNotification(
                            rejected.getAuthorUsername(),
                            "Submission Rejected",
                            notificationMessage,
                            NotificationPriority.NORMAL,
                            null,
                            Map.of("type", "submission", "submissionId", rejected.getId())
                    );
                    sendText(exchange, 200, "Submission rejected.");
                } else {
                    sendText(exchange, 400, "Action must be approve or reject.");
                }
            } catch (ApiAuthException e) {
                sendText(exchange, 401, e.getMessage());
            } catch (Exception e) {
                sendText(exchange, 400, e.getMessage());
            }
        });
    }

    private User requireRole(HttpExchange exchange, Role... allowedRoles) {
        String sessionId = nullToEmpty(exchange.getRequestHeaders().getFirst(SESSION_HEADER)).trim();
        if (sessionId.isEmpty()) {
            throw new ApiAuthException("Missing session. Please login again.");
        }

        User user = sessions.get(sessionId);
        if (user == null) {
            throw new ApiAuthException("Session expired or invalid. Please login again.");
        }

        long now = Instant.now().toEpochMilli();
        long lastActiveAt = sessionLastActiveAtMs.getOrDefault(sessionId, now);
        long idleTimeoutMs = SecurityConfig.sessionIdleTimeoutMs();
        if (now - lastActiveAt > idleTimeoutMs) {
            sessionSnapshotService.clearSnapshotForSession(sessionId);
            sessions.remove(sessionId);
            sessionLastActiveAtMs.remove(sessionId);
            refreshSessionSnapshot();
            throw new ApiAuthException("Session expired due to inactivity. Please login again.");
        }

        sessionLastActiveAtMs.put(sessionId, now);

        if (!user.isActive()) {
            sessionSnapshotService.clearSnapshotForSession(sessionId);
            sessions.remove(sessionId);
            sessionLastActiveAtMs.remove(sessionId);
            refreshSessionSnapshot();
            throw new ApiAuthException("Account is deactivated. Please contact a librarian.");
        }

        for (Role role : allowedRoles) {
            if (user.getRole() == role) {
                return user;
            }
        }

        throw new ApiAuthException("Permission denied for role " + user.getRole() + ".");
    }

    private User requireAuthenticated(HttpExchange exchange) {
        return requireRole(exchange, Role.STUDENT, Role.STAFF, Role.AUTHOR, Role.LIBRARIAN);
    }

    private String requireSessionId(HttpExchange exchange) {
        String sessionId = nullToEmpty(exchange.getRequestHeaders().getFirst(SESSION_HEADER)).trim();
        if (sessionId.isEmpty()) {
            throw new ApiAuthException("Missing session. Please login again.");
        }
        return sessionId;
    }

    private boolean isCrashHookEnabled(HttpExchange exchange) {
        String token = nullToEmpty(exchange.getRequestHeaders().getFirst(CRASH_TEST_HEADER)).trim();
        return CRASH_TEST_TOKEN.equals(token);
    }

    private void refreshSessionSnapshot() {
        latestSessionSnapshot = SessionSnapshotSchema.capture(sessions);
    }

    private int restoreSessionsFromSnapshot(SessionSnapshotSchema snapshot) {
        sessions.clear();
        sessionLastActiveAtMs.clear();
        int restored = 0;

        for (SessionSnapshotSchema.SessionEntry entry : snapshot.sessions()) {
            try {
                Role role = Role.valueOf(entry.role());
                User user = new User(entry.username(), entry.fullName(), "", role);
                sessions.put(entry.sessionId(), user);
                sessionLastActiveAtMs.put(entry.sessionId(), Instant.now().toEpochMilli());
                restored++;
            } catch (Exception ignored) {
                // Skip invalid or unknown session entries.
            }
        }

        refreshSessionSnapshot();
        return restored;
    }

        private int generateBorrowReminderNotifications(String username) {
        LocalDate today = LocalDate.now();
        List<BorrowService.BorrowReminderCandidate> candidates = borrowService.findReturnReminderCandidates(
            username,
            today,
            SecurityConfig.returnReminderDueSoonDays()
        );

        int generated = 0;
        for (BorrowService.BorrowReminderCandidate candidate : candidates) {
            String category = candidate.level() == BorrowService.BorrowReminderLevel.OVERDUE
                ? "overdue"
                : "due-soon";
            NotificationPriority priority = candidate.level() == BorrowService.BorrowReminderLevel.OVERDUE
                ? NotificationPriority.HIGH
                : (candidate.daysUntilDue() <= 1 ? NotificationPriority.HIGH : NotificationPriority.NORMAL);
            String title = candidate.level() == BorrowService.BorrowReminderLevel.OVERDUE
                ? "Overdue Return Reminder"
                : "Due Soon Return Reminder";
            String message = candidate.level() == BorrowService.BorrowReminderLevel.OVERDUE
                ? "Your borrowed book \"" + candidate.bookTitle() + "\" is overdue. It was due on " + candidate.dueDate() + "."
                : "Your borrowed book \"" + candidate.bookTitle() + "\" is due in " + candidate.daysUntilDue() + " day(s) on " + candidate.dueDate() + ".";

            boolean created = notificationService.addBorrowReminderIfAbsent(
                username,
                candidate.borrowRecordId(),
                category,
                today,
                candidate.dueDate(),
                title,
                message,
                priority
            );
            if (created) {
            generated++;
            }
        }
        return generated;
        }

    private static String readFirstTwoLinesIfTextFile(String filePath) {
        String normalizedPath = nullToEmpty(filePath).trim();
        if (normalizedPath.isEmpty()) {
            return "First 2-page preview is not available for this book.";
        }

        String lower = normalizedPath.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".txt") || lower.endsWith(".md"))) {
            return "First 2-page preview is not available for this file format.";
        }

        Path path = Paths.get(normalizedPath);
        if (!Files.isRegularFile(path)) {
            return "First 2-page preview file is not available.";
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                lines.add(line);
            }
            if (lines.isEmpty()) {
                return "No preview text available.";
            }
            return String.join(System.lineSeparator(), lines);
        } catch (Exception ex) {
            return "First 2-page preview cannot be read.";
        }
    }

    private String booksToJson(List<Book> books) {
        List<String> items = new ArrayList<>();
        for (Book book : books) {
            String status = book.isAvailable()
                    ? "Available (" + book.getAvailableCopies() + " copy/copies)"
                    : "Unavailable";
            String publishDate = book.getPublishDate() == null ? "" : book.getPublishDate().toString();
            List<String> genreValues = new ArrayList<>();
            for (String genre : book.getGenres()) {
                genreValues.add("\"" + JsonUtil.escape(genre) + "\"");
            }
            items.add("{" +
                    "\"id\":\"" + JsonUtil.escape(book.getId()) + "\"," +
                    "\"title\":\"" + JsonUtil.escape(book.getTitle()) + "\"," +
                    "\"author\":\"" + JsonUtil.escape(book.getAuthorFullName()) + "\"," +
                "\"publishDate\":\"" + JsonUtil.escape(publishDate) + "\"," +
                    "\"summary\":\"" + JsonUtil.escape(nullToEmpty(book.getSummary())) + "\"," +
                    "\"coverImagePath\":\"" + JsonUtil.escape(nullToEmpty(book.getCoverImagePath())) + "\"," +
                    "\"genres\":[" + String.join(",", genreValues) + "]," +
                    "\"status\":\"" + JsonUtil.escape(status) + "\"," +
                    reviewStatsJson(book.getId()) + "," +
                    "\"available\":" + book.isAvailable() + "," +
                    "\"totalCopies\":" + book.getTotalCopies() + "," +
                    "\"availableCopies\":" + book.getAvailableCopies() +
                    "}");
        }
        return "[" + String.join(",", items) + "]";
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        String safe = text == null ? "" : text;
        byte[] body = safe.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseKeyValuePairs(body);
    }

    private static Map<String, String> readQuery(URI uri) {
        return parseKeyValuePairs(uri.getRawQuery());
    }

    private static Map<String, String> parseKeyValuePairs(String payload) {
        Map<String, String> values = new LinkedHashMap<>();
        if (payload == null || payload.isBlank()) {
            return values;
        }

        String[] pairs = payload.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String key = urlDecode(parts[0]);
            String value = parts.length > 1 ? urlDecode(parts[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value.trim();
    }

    private static Boolean parseAvailabilityFilter(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "availability", "");
        if (raw.isEmpty() || "all".equalsIgnoreCase(raw)) {
            return null;
        }
        if ("available".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("unavailable".equalsIgnoreCase(raw)) {
            return false;
        }
        throw new IllegalArgumentException("availability must be one of: all, available, unavailable.");
    }

    private static String parseBorrowStatus(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "status", "active");
        if ("all".equalsIgnoreCase(raw)
                || "returned".equalsIgnoreCase(raw)
                || "active".equalsIgnoreCase(raw)
                || "overdue".equalsIgnoreCase(raw)) {
            return raw.toLowerCase();
        }
        throw new IllegalArgumentException("status must be one of: all, returned, active, overdue.");
    }

    private static String parseBorrowSortBy(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "sortBy", "");
        if (raw.isEmpty() || "borrowDate".equalsIgnoreCase(raw) || "dueDate".equalsIgnoreCase(raw)) {
            return raw;
        }
        throw new IllegalArgumentException("sortBy must be one of: borrowDate, dueDate.");
    }

    private static String parseBorrowSortDir(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "sortDir", "asc");
        if ("asc".equalsIgnoreCase(raw) || "desc".equalsIgnoreCase(raw)) {
            return raw.toLowerCase();
        }
        throw new IllegalArgumentException("sortDir must be one of: asc, desc.");
    }

    private static String parseReadingHistorySortBy(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "sortBy", "borrowDate");
        if (raw.isEmpty()
                || "borrowDate".equalsIgnoreCase(raw)
                || "returnDate".equalsIgnoreCase(raw)
                || "title".equalsIgnoreCase(raw)
                || "author".equalsIgnoreCase(raw)
                || "duration".equalsIgnoreCase(raw)) {
            return raw;
        }
        throw new IllegalArgumentException("sortBy must be one of: borrowDate, returnDate, title, author, duration.");
    }

    private static String parseReadingHistorySortDir(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "sortDir", "desc");
        if ("asc".equalsIgnoreCase(raw) || "desc".equalsIgnoreCase(raw)) {
            return raw.toLowerCase();
        }
        throw new IllegalArgumentException("sortDir must be one of: asc, desc.");
    }

    private static NotificationService.NotificationReadFilter parseNotificationReadFilter(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "read", "all");
        return NotificationService.NotificationReadFilter.fromString(raw);
    }

    private static NotificationPriority parseNotificationPriorityFilter(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "priority", "all");
        if (raw.isEmpty() || "all".equalsIgnoreCase(raw)) {
            return null;
        }

        try {
            return NotificationPriority.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("priority must be one of: all, high, normal, low.");
        }
    }

    private static NotificationService.NotificationSortBy parseNotificationSortBy(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "sortBy", "createdAt");
        return NotificationService.NotificationSortBy.fromString(raw);
    }

    private static NotificationService.NotificationSortDirection parseNotificationSortDir(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "sortDir", "desc");
        return NotificationService.NotificationSortDirection.fromString(raw);
    }

    private static String parseLibrarianSubmissionStatus(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "status", "pending");
        if ("all".equalsIgnoreCase(raw)
                || "pending".equalsIgnoreCase(raw)
                || "approved".equalsIgnoreCase(raw)
                || "rejected".equalsIgnoreCase(raw)) {
            return raw.toLowerCase();
        }
        throw new IllegalArgumentException("status must be one of: all, pending, approved, rejected.");
    }

    private static String parseLibrarianSubmissionSortBy(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "sortBy", "");
        if (raw.isEmpty() || "submittedDate".equalsIgnoreCase(raw)) {
            return raw;
        }
        throw new IllegalArgumentException("sortBy must be one of: submittedDate.");
    }

    private static String parseLibrarianSubmissionSortDir(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "sortDir", "asc");
        if ("asc".equalsIgnoreCase(raw) || "desc".equalsIgnoreCase(raw)) {
            return raw.toLowerCase();
        }
        throw new IllegalArgumentException("sortDir must be one of: asc, desc.");
    }

    private static String parseManagedUserRole(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "role", "all");
        if ("all".equalsIgnoreCase(raw)
                || "student".equalsIgnoreCase(raw)
                || "staff".equalsIgnoreCase(raw)
                || "author".equalsIgnoreCase(raw)
                || "librarian".equalsIgnoreCase(raw)) {
            return raw.toLowerCase();
        }
        throw new IllegalArgumentException("role must be one of: all, student, staff, author, librarian.");
    }

    private static String parseManagedUserStatus(Map<String, String> values) {
        String raw = RequestFilters.getTrimmed(values, "status", "all");
        if ("all".equalsIgnoreCase(raw)
                || "active".equalsIgnoreCase(raw)
                || "inactive".equalsIgnoreCase(raw)) {
            return raw.toLowerCase();
        }
        throw new IllegalArgumentException("status must be one of: all, active, inactive.");
    }

    private static LocalDate parseDateFilter(Map<String, String> values, String key) {
        String raw = RequestFilters.getTrimmed(values, key, "");
        if (raw.isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid date value for " + key + ". Expected YYYY-MM-DD.");
        }
    }

    private static String urlDecode(String input) {
        return URLDecoder.decode(input, StandardCharsets.UTF_8);
    }

    private static int readingDurationMinutes(BorrowRecord record, LocalDate returnedDate) {
        LocalDate endDate = returnedDate == null ? LocalDate.now() : returnedDate;
        long daySpan = Math.max(0, endDate.toEpochDay() - record.getBorrowDate().toEpochDay());
        double minutes = daySpan * 24d * 60d;
        return (int) Math.round(minutes);
    }

    private static boolean matchesReadingHistoryFilters(ReadingHistoryEntry entry,
                                                       String keyword,
                                                       String authorFilter,
                                                       String genreFilter,
                                                       LocalDate borrowDateFrom,
                                                       LocalDate borrowDateTo,
                                                       LocalDate returnDateFrom,
                                                       LocalDate returnDateTo) {
        String normalizedKeyword = nullToEmpty(keyword).trim().toLowerCase(Locale.ROOT);
        String normalizedAuthor = nullToEmpty(authorFilter).trim().toLowerCase(Locale.ROOT);
        String normalizedGenre = nullToEmpty(genreFilter).trim().toLowerCase(Locale.ROOT);

        if (!normalizedKeyword.isEmpty()) {
            boolean matchesKeyword = containsIgnoreCase(entry.bookTitle(), normalizedKeyword)
                    || containsIgnoreCase(entry.authorFullName(), normalizedKeyword)
                    || containsIgnoreCase(entry.authorUsername(), normalizedKeyword)
                    || entry.genres().stream().anyMatch(genre -> containsIgnoreCase(genre, normalizedKeyword));
            if (!matchesKeyword) {
                return false;
            }
        }

        if (!normalizedAuthor.isEmpty()) {
            boolean matchesAuthor = containsIgnoreCase(entry.authorFullName(), normalizedAuthor)
                    || containsIgnoreCase(entry.authorUsername(), normalizedAuthor);
            if (!matchesAuthor) {
                return false;
            }
        }

        if (!normalizedGenre.isEmpty()) {
            boolean matchesGenre = entry.genres().stream().anyMatch(genre -> containsIgnoreCase(genre, normalizedGenre));
            if (!matchesGenre) {
                return false;
            }
        }

        if (borrowDateFrom != null && entry.borrowDate().isBefore(borrowDateFrom)) {
            return false;
        }
        if (borrowDateTo != null && entry.borrowDate().isAfter(borrowDateTo)) {
            return false;
        }
        if (returnDateFrom != null) {
            if (entry.returnedDate() == null || entry.returnedDate().isBefore(returnDateFrom)) {
                return false;
            }
        }
        if (returnDateTo != null) {
            if (entry.returnedDate() == null || entry.returnedDate().isAfter(returnDateTo)) {
                return false;
            }
        }

        return true;
    }

    private static Comparator<ReadingHistoryEntry> readingHistoryComparator(String sortBy, String sortDir) {
        String normalizedSortBy = sortBy == null ? "" : sortBy.trim().toLowerCase(Locale.ROOT);
        Comparator<ReadingHistoryEntry> comparator = switch (normalizedSortBy) {
            case "returndate" -> Comparator.comparing(
                    entry -> entry.returnedDate() == null ? LocalDate.MAX : entry.returnedDate()
            );
            case "title" -> Comparator.comparing(
                    entry -> nullToEmpty(entry.bookTitle()).toLowerCase(Locale.ROOT)
            );
            case "author" -> Comparator.comparing(
                    entry -> nullToEmpty(entry.authorFullName()).toLowerCase(Locale.ROOT)
            );
            case "duration" -> Comparator.comparingInt(ReadingHistoryEntry::readingDurationMinutes);
            case "", "borrowdate" -> Comparator.comparing(ReadingHistoryEntry::borrowDate);
            default -> Comparator.comparing(ReadingHistoryEntry::borrowDate);
        };

        comparator = comparator.thenComparing(ReadingHistoryEntry::recordId);
        if ("asc".equalsIgnoreCase(sortDir)) {
            return comparator;
        }
        return comparator.reversed();
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return nullToEmpty(value).toLowerCase(Locale.ROOT).contains(nullToEmpty(needle).toLowerCase(Locale.ROOT));
    }

    private String borrowsToJson(List<BorrowRecord> records) {
        LocalDate today = LocalDate.now();
        int dueSoonThresholdDays = SecurityConfig.returnReminderDueSoonDays();
        List<String> jsonItems = new ArrayList<>();
        for (BorrowRecord record : records) {
            String title = bookService.findBookById(record.getBookId())
                    .map(Book::getTitle)
                    .orElse(record.getBookId());
            boolean overdue = record.isOverdue(today);
            int daysUntilDue = (int) (record.getDueDate().toEpochDay() - today.toEpochDay());
            boolean dueSoon = !record.isReturned() && !overdue && daysUntilDue >= 0 && daysUntilDue <= dueSoonThresholdDays;
            String reminderLevel = overdue ? "overdue" : (dueSoon ? "due-soon" : "");
            String status = record.isReturned() ? "Returned" : "Borrowed";

            jsonItems.add("{" +
                    "\"recordId\":\"" + JsonUtil.escape(record.getId()) + "\"," +
                    "\"bookId\":\"" + JsonUtil.escape(record.getBookId()) + "\"," +
                    "\"bookTitle\":\"" + JsonUtil.escape(title) + "\"," +
                    "\"borrowDate\":\"" + record.getBorrowDate() + "\"," +
                    "\"dueDate\":\"" + record.getDueDate() + "\"," +
                    "\"returned\":" + record.isReturned() + "," +
                    "\"status\":\"" + status + "\"," +
                    "\"overdue\":" + overdue + "," +
                    "\"dueSoon\":" + dueSoon + "," +
                    "\"daysUntilDue\":" + daysUntilDue + "," +
                    "\"reminderLevel\":\"" + reminderLevel + "\"" +
                    "}");
        }
        return "[" + String.join(",", jsonItems) + "]";
    }

    private static String readingProgressToJson(ReadingProgress progress) {
        List<String> highlightJson = new ArrayList<>();
        for (String highlight : progress.getHighlights()) {
            highlightJson.add("\"" + JsonUtil.escape(highlight) + "\"");
        }
        return "{" +
                "\"bookId\":\"" + JsonUtil.escape(progress.getBookId()) + "\"," +
                "\"bookmark\":" + progress.getBookmarkPage() + "," +
                "\"totalReadingSeconds\":" + progress.getTotalReadingSeconds() + "," +
                "\"totalReadingMinutes\":" + progress.getTotalReadingMinutes() + "," +
                "\"highlights\":[" + String.join(",", highlightJson) + "]" +
                "}";
    }

    private static String notificationsToJson(List<NotificationItem> items) {
        List<String> values = new ArrayList<>();
        for (NotificationItem item : items) {
            String categoryKey = notificationCategoryKey(item);
            String safeMessage = sanitizeSubmissionRejectionMessage(item);
            List<String> metadataValues = new ArrayList<>();
            for (Map.Entry<String, String> metadata : item.getMetadata().entrySet()) {
                metadataValues.add("\"" + JsonUtil.escape(metadata.getKey()) + "\":\"" + JsonUtil.escape(metadata.getValue()) + "\"");
            }

            String actionJson = "null";
            if (item.getAction() != null) {
                actionJson = "{" +
                        "\"type\":\"" + JsonUtil.escape(item.getAction().getType()) + "\"," +
                        "\"label\":\"" + JsonUtil.escape(item.getAction().getLabel()) + "\"," +
                        "\"target\":\"" + JsonUtil.escape(item.getAction().getTarget()) + "\"," +
                        "\"method\":\"" + JsonUtil.escape(item.getAction().getMethod()) + "\"" +
                        "}";
            }

            values.add("{" +
                    "\"id\":\"" + JsonUtil.escape(item.getId()) + "\"," +
                    "\"title\":\"" + JsonUtil.escape(item.getTitle()) + "\"," +
                    "\"message\":\"" + JsonUtil.escape(safeMessage) + "\"," +
                    "\"category\":\"" + JsonUtil.escape(notificationCategoryLabel(categoryKey)) + "\"," +
                    "\"categoryKey\":\"" + JsonUtil.escape(categoryKey) + "\"," +
                    "\"priority\":\"" + item.getPriority() + "\"," +
                    "\"createdDate\":\"" + JsonUtil.escape(item.getCreatedAt() == null ? "" : item.getCreatedAt().toLocalDate().toString()) + "\"," +
                    "\"createdAt\":\"" + DATE_TIME_FORMATTER.format(item.getCreatedAt()) + "\"," +
                    "\"read\":" + item.isRead() + "," +
                    "\"readAt\":\"" + JsonUtil.escape(item.getReadAt() == null ? "" : DATE_TIME_FORMATTER.format(item.getReadAt())) + "\"," +
                    "\"archived\":" + item.isArchived() + "," +
                    "\"archivedAt\":\"" + JsonUtil.escape(item.getArchivedAt() == null ? "" : DATE_TIME_FORMATTER.format(item.getArchivedAt())) + "\"," +
                    "\"metadata\":{" + String.join(",", metadataValues) + "}," +
                    "\"action\":" + actionJson +
                    "}");
        }
        return "[" + String.join(",", values) + "]";
    }

    private static String sanitizeSubmissionRejectionMessage(NotificationItem item) {
        String title = nullToEmpty(item.getTitle());
        String message = nullToEmpty(item.getMessage());
        if (!"submission rejected".equalsIgnoreCase(title)) {
            return message;
        }

        int reasonIndex = message.toLowerCase(Locale.ROOT).indexOf("reason:");
        if (reasonIndex < 0) {
            return message;
        }
        return message.substring(0, reasonIndex).trim();
    }

    private static String notificationsSummaryToJson(List<NotificationItem> items) {
        int unreadCount = 0;
        for (NotificationItem item : items) {
            if (!item.isRead()) {
                unreadCount++;
            }
        }

        return "{" +
                "\"total\":" + items.size() + "," +
                "\"unreadCount\":" + unreadCount +
                "}";
    }

    private static List<NotificationItem> filterNotificationsByCategory(List<NotificationItem> items, String categoryFilter) {
        String normalizedFilter = normalizeNotificationCategoryFilter(categoryFilter);
        if ("all".equals(normalizedFilter)) {
            return items;
        }

        List<NotificationItem> filtered = new ArrayList<>();
        for (NotificationItem item : items) {
            if (normalizedFilter.equals(notificationCategoryKey(item))) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private static List<NotificationItem> filterNotificationsByCreatedDate(List<NotificationItem> items,
                                                                           LocalDate createdDateFrom,
                                                                           LocalDate createdDateTo) {
        if (createdDateFrom == null && createdDateTo == null) {
            return items;
        }

        List<NotificationItem> filtered = new ArrayList<>();
        for (NotificationItem item : items) {
            LocalDate createdDate = item.getCreatedAt() == null ? null : item.getCreatedAt().toLocalDate();
            if (createdDate == null) {
                continue;
            }
            if (createdDateFrom != null && createdDate.isBefore(createdDateFrom)) {
                continue;
            }
            if (createdDateTo != null && createdDate.isAfter(createdDateTo)) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    private static String normalizeNotificationCategoryFilter(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return "all";
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        for (String basicCategory : BASIC_NOTIFICATION_CATEGORIES) {
            if (basicCategory.equals(normalized)) {
                return normalized;
            }
        }
        return "other";
    }

    private static String notificationCategoryKey(NotificationItem item) {
        String metadataType = normalizeNotificationCategoryFilter(
                nullToEmpty(item.getMetadata().get("type")).replace('_', '-'));
        if (!"all".equals(metadataType)) {
            return metadataType;
        }

        String metadataCategory = normalizeNotificationCategoryFilter(
                nullToEmpty(item.getMetadata().get("category")).replace('_', '-'));
        if (!"all".equals(metadataCategory)) {
            return metadataCategory;
        }

        return "other";
    }

    private static String notificationCategoryLabel(String categoryKey) {
        return switch (normalizeNotificationCategoryFilter(categoryKey)) {
            case "submission" -> "Submission";
            case "review" -> "Review";
            case "account-update" -> "Account Update";
            case "auto-return" -> "Auto Return";
            case "borrow-reminder" -> "Borrow Reminder";
            case "book-deleted" -> "Book Deleted";
            case "announcement" -> "Announcement";
            default -> "Other";
        };
    }

    private String sessionSnapshotToJson(SessionSnapshot snapshot, boolean exists) {
        if (!exists || snapshot == null) {
            return "{" +
                    "\"exists\":false," +
                    "\"sessionId\":\"\"," +
                    "\"username\":\"\"," +
                    "\"role\":\"\"," +
                    "\"portalKey\":\"\"," +
                    "\"lastViewKey\":\"\"," +
                    "\"lastAction\":\"\"," +
                    "\"timestamp\":\"\"," +
                    "\"statePayload\":\"\"" +
                    "}";
        }

        return "{" +
                "\"exists\":true," +
                "\"sessionId\":\"" + JsonUtil.escape(snapshot.getSessionId()) + "\"," +
                "\"username\":\"" + JsonUtil.escape(snapshot.getUsername()) + "\"," +
                "\"role\":\"" + JsonUtil.escape(snapshot.getRole().name()) + "\"," +
                "\"portalKey\":\"" + JsonUtil.escape(snapshot.getPortalKey()) + "\"," +
                "\"lastViewKey\":\"" + JsonUtil.escape(snapshot.getLastViewKey()) + "\"," +
                "\"lastAction\":\"" + JsonUtil.escape(snapshot.getLastAction()) + "\"," +
                "\"timestamp\":\"" + JsonUtil.escape(DATE_TIME_FORMATTER.format(snapshot.getCapturedAt())) + "\"," +
                "\"statePayload\":\"" + JsonUtil.escape(snapshot.getStatePayload()) + "\"" +
                "}";
    }

    private String authorPublishedBooksToJson(List<Book> books) {
        List<String> values = new ArrayList<>();
        for (Book book : books) {
            String publishDate = book.getPublishDate() == null ? "" : book.getPublishDate().toString();
            List<String> genreValues = new ArrayList<>();
            for (String genre : book.getGenres()) {
                genreValues.add("\"" + JsonUtil.escape(genre) + "\"");
            }
            values.add("{" +
                    "\"id\":\"" + JsonUtil.escape(book.getId()) + "\"," +
                    "\"title\":\"" + JsonUtil.escape(book.getTitle()) + "\"," +
                    "\"summary\":\"" + JsonUtil.escape(nullToEmpty(book.getSummary())) + "\"," +
                    "\"description\":\"" + JsonUtil.escape(nullToEmpty(book.getSummary())) + "\"," +
                    "\"genres\":[" + String.join(",", genreValues) + "]," +
                    "\"coverImagePath\":\"" + JsonUtil.escape(nullToEmpty(book.getCoverImagePath())) + "\"," +
                    "\"publishDate\":\"" + JsonUtil.escape(publishDate) + "\"," +
                    "\"status\":\"" + (book.isApproved() ? "Approved" : "Pending") + "\"," +
                    reviewStatsJson(book.getId()) +
                    "}");
        }
        return "[" + String.join(",", values) + "]";
    }

    private String librarianPublishedBookToJson(Book book) {
        String publishDate = book.getPublishDate() == null ? "" : book.getPublishDate().toString();
        String availability = book.isAvailable()
                ? "Available (" + book.getAvailableCopies() + " copy/copies)"
                : "Unavailable";
        List<String> genreValues = new ArrayList<>();
        for (String genre : book.getGenres()) {
            genreValues.add("\"" + JsonUtil.escape(genre) + "\"");
        }

        return "{" +
                "\"id\":\"" + JsonUtil.escape(book.getId()) + "\"," +
                "\"title\":\"" + JsonUtil.escape(book.getTitle()) + "\"," +
                "\"author\":\"" + JsonUtil.escape(book.getAuthorFullName()) + "\"," +
                "\"authorUsername\":\"" + JsonUtil.escape(nullToEmpty(book.getAuthorUsername())) + "\"," +
                "\"description\":\"" + JsonUtil.escape(nullToEmpty(book.getSummary())) + "\"," +
                "\"genres\":[" + String.join(",", genreValues) + "]," +
                "\"filePath\":\"" + JsonUtil.escape(nullToEmpty(book.getFilePath())) + "\"," +
                "\"coverImagePath\":\"" + JsonUtil.escape(nullToEmpty(book.getCoverImagePath())) + "\"," +
                "\"publishDate\":\"" + JsonUtil.escape(publishDate) + "\"," +
                "\"status\":\"" + availability + "\"," +
                reviewStatsJson(book.getId()) + "," +
                "\"available\":" + book.isAvailable() + "," +
                "\"totalCopies\":" + book.getTotalCopies() + "," +
                "\"availableCopies\":" + book.getAvailableCopies() +
                "}";
    }

    private List<String> validateSupportedGenres(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            throw new IllegalArgumentException("At least one genre is required.");
        }

        List<String> supported = authorService.getSupportedGenres();
        java.util.Set<String> supportedKeys = new java.util.HashSet<>();
        for (String item : supported) {
            supportedKeys.add(item.toLowerCase(Locale.ROOT));
        }

        List<String> normalized = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String item : genres) {
            String trimmed = nullToEmpty(item).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (supportedKeys.contains(key)) {
                normalized.add(trimmed);
            } else {
                invalid.add(trimmed);
            }
        }

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Unsupported genres: " + invalid + ". Supported: " + supported);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one genre is required.");
        }
        return normalized;
    }

    private String generateBookDescriptionSuggestion(String title, String authorNames, List<String> genres) {
        String safeTitle = nullToEmpty(title).trim();
        if (safeTitle.isEmpty()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (genres == null || genres.isEmpty()) {
            throw new IllegalArgumentException("At least one genre is required.");
        }

        String safeAuthor = nullToEmpty(authorNames).trim();
        String genrePhrase;
        if (genres.size() == 1) {
            genrePhrase = genres.get(0);
        } else if (genres.size() == 2) {
            genrePhrase = genres.get(0) + " and " + genres.get(1);
        } else {
            genrePhrase = String.join(", ", genres.subList(0, genres.size() - 1)) + ", and " + genres.get(genres.size() - 1);
        }

        String byline = safeAuthor.isEmpty() ? "" : (" by " + safeAuthor);
        return "\"" + safeTitle + "\"" + byline + " is a " + genrePhrase
                + " title that delivers an engaging narrative, clear thematic direction, and reader-friendly pacing. "
                + "The work combines accessible storytelling with meaningful detail, making it suitable for both casual reading and guided study. "
                + "Recommended for library readers seeking a well-structured and thoughtfully developed book experience.";
    }

    private String reviewStatsJson(String bookId) {
        BookReviewService.RatingSummary summary = bookReviewService.getRatingSummary(bookId);
        String average = summary.reviewCount() == 0 ? "null" : String.format(Locale.US, "%.2f", summary.averageRating());
        return "\"averageRating\":" + average + ",\"reviewCount\":" + summary.reviewCount();
    }

    private String authorPublishedStatsToJson(List<Book> books, List<BorrowRecord> borrows) {
        int totalReads = 0;
        int totalBorrows = 0;
        int totalActiveBorrows = 0;
        int totalReviews = 0;
        double ratingSum = 0.0;
        int[] ratingBuckets = new int[5];
        List<String> bookStatsValues = new ArrayList<>();

        for (Book book : books) {
            int borrowCount = 0;
            int activeBorrowCount = 0;
            java.util.Set<String> uniqueReaders = new java.util.LinkedHashSet<>();
            for (BorrowRecord record : borrows) {
                if (book.getId().equals(record.getBookId())) {
                    borrowCount++;
                    uniqueReaders.add(record.getUsername());
                    if (!record.isReturned()) {
                        activeBorrowCount++;
                    }
                }
            }

            List<BookReview> reviews = bookReviewService.listReviewsForBook(book.getId());
            BookReviewService.RatingSummary ratingSummary = bookReviewService.getRatingSummary(book.getId());
            for (BookReview review : reviews) {
                int rating = Math.max(1, Math.min(5, review.getRating()));
                ratingBuckets[rating - 1]++;
            }

            int readCount = uniqueReaders.size();
            int reviewCount = ratingSummary.reviewCount();
            totalReads += readCount;
            totalBorrows += borrowCount;
            totalActiveBorrows += activeBorrowCount;
            totalReviews += reviewCount;
            ratingSum += ratingSummary.averageRating() * reviewCount;

            String average = reviewCount == 0
                    ? "null"
                    : String.format(Locale.US, "%.2f", ratingSummary.averageRating());
            String publishDate = book.getPublishDate() == null ? "" : book.getPublishDate().toString();
            List<String> genreValues = new ArrayList<>();
            for (String genre : book.getGenres()) {
                genreValues.add("\"" + JsonUtil.escape(genre) + "\"");
            }

            bookStatsValues.add("{" +
                    "\"id\":\"" + JsonUtil.escape(book.getId()) + "\"," +
                    "\"title\":\"" + JsonUtil.escape(book.getTitle()) + "\"," +
                    "\"publishDate\":\"" + JsonUtil.escape(publishDate) + "\"," +
                    "\"genres\":[" + String.join(",", genreValues) + "]," +
                    "\"readCount\":" + readCount + "," +
                    "\"borrowCount\":" + borrowCount + "," +
                    "\"activeBorrowCount\":" + activeBorrowCount + "," +
                    "\"reviewCount\":" + reviewCount + "," +
                    "\"averageRating\":" + average +
                    "}");
        }

        String overallAverage = totalReviews == 0
                ? "null"
                : String.format(Locale.US, "%.2f", ratingSum / totalReviews);

        String summary = "{" +
                "\"bookCount\":" + books.size() + "," +
                "\"totalReadCount\":" + totalReads + "," +
                "\"totalBorrowCount\":" + totalBorrows + "," +
                "\"totalActiveBorrowCount\":" + totalActiveBorrows + "," +
                "\"totalReviewCount\":" + totalReviews + "," +
                "\"overallAverageRating\":" + overallAverage +
                "}";

        String ratingBucketJson = "[" +
                "{\"label\":\"1-star\",\"count\":" + ratingBuckets[0] + "}," +
                "{\"label\":\"2-star\",\"count\":" + ratingBuckets[1] + "}," +
                "{\"label\":\"3-star\",\"count\":" + ratingBuckets[2] + "}," +
                "{\"label\":\"4-star\",\"count\":" + ratingBuckets[3] + "}," +
                "{\"label\":\"5-star\",\"count\":" + ratingBuckets[4] + "}" +
                "]";

        // Borrows timeline: daily counts across all of this author's books
        java.util.Set<String> authorBookIds = new java.util.LinkedHashSet<>();
        for (Book b : books) authorBookIds.add(b.getId());
        java.util.TreeMap<String, Integer> dailyCounts = new java.util.TreeMap<>();
        for (BorrowRecord record : borrows) {
            if (!authorBookIds.contains(record.getBookId())) continue;
            if (record.getBorrowDate() == null) continue;
            String key = record.getBorrowDate().toString();
            dailyCounts.merge(key, 1, Integer::sum);
        }
        List<String> timelineEntries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : dailyCounts.entrySet()) {
            timelineEntries.add("{\"date\":\"" + JsonUtil.escape(e.getKey()) + "\",\"count\":" + e.getValue() + "}");
        }
        String borrowsTimelineJson = "[" + String.join(",", timelineEntries) + "]";

        return "{" +
                "\"summary\":" + summary + "," +
                "\"books\":[" + String.join(",", bookStatsValues) + "]," +
                "\"ratingBuckets\":" + ratingBucketJson + "," +
                "\"borrowsTimeline\":" + borrowsTimelineJson +
                "}";
    }

    private String reviewToJson(BookReview review) {
        return reviewToJson(review, "");
    }

    private String reviewToJson(BookReview review, String viewerUsername) {
        String bookTitle = bookService.findBookById(review.getBookId())
                .map(Book::getTitle)
                .orElse(review.getBookId());
        String realReviewerFullName = authService.findUserByUsername(review.getUsername())
                .map(User::getFullName)
                .orElse(review.getUsername());
        String createdAt = review.getCreatedAt() == null ? "" : DATE_TIME_FORMATTER.format(review.getCreatedAt());
        String updatedAt = review.getUpdatedAt() == null ? "" : DATE_TIME_FORMATTER.format(review.getUpdatedAt());
        String repliedAt = review.getRepliedAt() == null ? "" : DATE_TIME_FORMATTER.format(review.getRepliedAt());
        String flaggedAt = review.getFlaggedAt() == null ? "" : DATE_TIME_FORMATTER.format(review.getFlaggedAt());

        boolean isOwnReview = viewerUsername != null
                && !viewerUsername.isEmpty()
                && viewerUsername.equals(review.getUsername());
        boolean maskIdentity = review.isAnonymous() && !isOwnReview;
        String displayUsername = maskIdentity ? "" : review.getUsername();
        String displayFullName = maskIdentity ? "Anonymous" : realReviewerFullName;

        return "{" +
                "\"reviewId\":\"" + JsonUtil.escape(review.getId()) + "\"," +
                "\"bookId\":\"" + JsonUtil.escape(review.getBookId()) + "\"," +
                "\"bookTitle\":\"" + JsonUtil.escape(bookTitle) + "\"," +
                "\"username\":\"" + JsonUtil.escape(displayUsername) + "\"," +
                "\"reviewerFullName\":\"" + JsonUtil.escape(displayFullName) + "\"," +
                "\"anonymous\":" + review.isAnonymous() + "," +
                "\"rating\":" + review.getRating() + "," +
                "\"reviewText\":\"" + JsonUtil.escape(review.getReviewText()) + "\"," +
            "\"replyText\":\"" + JsonUtil.escape(review.getReplyText()) + "\"," +
            "\"flagged\":" + review.isFlagged() + "," +
            "\"flagReason\":\"" + JsonUtil.escape(review.getFlagReason()) + "\"," +
                "\"sentiment\":\"" + JsonUtil.escape(review.getSentiment()) + "\"," +
                "\"createdAt\":\"" + JsonUtil.escape(createdAt) + "\"," +
            "\"repliedAt\":\"" + JsonUtil.escape(repliedAt) + "\"," +
            "\"flaggedAt\":\"" + JsonUtil.escape(flaggedAt) + "\"," +
                "\"updatedAt\":\"" + JsonUtil.escape(updatedAt) + "\"" +
                "}";
    }

    private String reviewsToJson(List<BookReview> reviews) {
        return reviewsToJson(reviews, "");
    }

    private String reviewsToJson(List<BookReview> reviews, String viewerUsername) {
        List<String> values = new ArrayList<>();
        for (BookReview review : reviews) {
            values.add(reviewToJson(review, viewerUsername));
        }
        return "[" + String.join(",", values) + "]";
    }

    private String myReviewsToJson(String username) {
        List<BookReview> reviews = bookReviewService.listReviewsByUser(username);
        List<String> values = new ArrayList<>();
        for (BookReview review : reviews) {
            values.add(reviewToJson(review, username));
        }
        return "[" + String.join(",", values) + "]";
    }

    private String bookRequestToJson(BookRequest2 request) {
        List<String> genreValues = new ArrayList<>();
        for (String genre : request.getGenres()) {
            genreValues.add("\"" + JsonUtil.escape(genre) + "\"");
        }

        String approvedDate = request.getApprovedDate() == null ? "" : request.getApprovedDate().toString();
        String uploadedDate = request.getUploadedDate() == null ? "" : request.getUploadedDate().toString();

        return "{" +
                "\"id\":\"" + JsonUtil.escape(request.getId()) + "\"," +
                "\"title\":\"" + JsonUtil.escape(request.getTitle()) + "\"," +
                "\"requesterUsername\":\"" + JsonUtil.escape(request.getRequesterUsername()) + "\"," +
                "\"requesterFullName\":\"" + JsonUtil.escape(request.getRequesterFullName()) + "\"," +
                "\"authorName\":\"" + JsonUtil.escape(request.getAuthorName()) + "\"," +
                "\"genres\":[" + String.join(",", genreValues) + "]," +
                "\"reason\":\"" + JsonUtil.escape(request.getReason()) + "\"," +
                "\"requestedDate\":\"" + JsonUtil.escape(request.getRequestedDate() == null ? "" : request.getRequestedDate().toString()) + "\"," +
                "\"status\":\"" + request.getStatus() + "\"," +
                "\"librarianComment\":\"" + JsonUtil.escape(nullToEmpty(request.getLibrarianComment())) + "\"," +
                "\"rejectionReason\":\"" + JsonUtil.escape(nullToEmpty(request.getRejectionReason())) + "\"," +
                "\"approvedDate\":\"" + JsonUtil.escape(approvedDate) + "\"," +
                "\"uploadedDate\":\"" + JsonUtil.escape(uploadedDate) + "\"," +
                "\"bookId\":\"" + JsonUtil.escape(nullToEmpty(request.getBookId())) + "\"," +
                "\"priority\":" + request.isPriority() +
                "}";
    }

    private String bookRequestsToJson(List<BookRequest2> requests) {
        List<String> values = new ArrayList<>();
        for (BookRequest2 request : requests) {
            values.add(bookRequestToJson(request));
        }
        return "[" + String.join(",", values) + "]";
    }

    private static final class PdfSearchStats {
        private int archiveCandidates;
        private int archiveWithPdf;
        private int googleCandidates;
        private int googleWithPdf;
        private int googleFallbackWithPdf;
    }

    private record DownloadedPdf(String filePath, String contentType) {
    }

    private record PdfSearchResult(String identifier, String title, String downloadUrl, String source, List<String> authors) {
    }

    private String pdfSearchResultsToJson(List<PdfSearchResult> results) {
        List<String> values = new ArrayList<>();
        for (PdfSearchResult result : results) {
            values.add("{" +
                    "\"identifier\":\"" + JsonUtil.escape(result.identifier()) + "\"," +
                    "\"title\":\"" + JsonUtil.escape(result.title()) + "\"," +
                    "\"downloadUrl\":\"" + JsonUtil.escape(result.downloadUrl()) + "\"," +
                    "\"source\":\"" + JsonUtil.escape(result.source()) + "\"" +
                    "}");
        }
        return "[" + String.join(",", values) + "]";
    }

    private String pdfSearchDebugToJson(PdfSearchStats stats) {
        return "{" +
                "\"archiveCandidates\":" + stats.archiveCandidates + "," +
                "\"archiveWithPdf\":" + stats.archiveWithPdf + "," +
                "\"googleCandidates\":" + stats.googleCandidates + "," +
                "\"googleWithPdf\":" + stats.googleWithPdf + "," +
                "\"googleFallbackWithPdf\":" + stats.googleFallbackWithPdf +
                "}";
    }

    private record PdfSearchPageResult(List<PdfSearchResult> results, boolean hasNext) {
    }

    private static final class PdfSearchCacheEntry {
        private final List<PdfSearchResult> candidates = new ArrayList<>();
        private List<PdfSearchResult> rankedResults = new ArrayList<>();
        private int nextSourcePage = 1;
        private boolean exhausted;
        private long updatedAtMs;
    }

    private PdfSearchPageResult searchPublicDomainPdfSources(String sessionId,
                                                             String title,
                                                             String authorName,
                                                             int limit,
                                                             int page,
                                                             String searchMode,
                                                             PdfSearchStats stats)
            throws IOException, InterruptedException {
        cleanupExpiredPdfSearchCache();

        String cacheKey = buildPdfSearchCacheKey(sessionId, title, authorName, searchMode);
        PdfSearchCacheEntry cacheEntry = pdfSearchCache.computeIfAbsent(cacheKey, key -> new PdfSearchCacheEntry());
        int requestedCount = Math.max(1, page * limit + 1);
        int prefetchCount = page <= 1 ? Math.max(requestedCount, limit * PDF_SEARCH_PREFETCH_PAGES) : requestedCount;

        synchronized (cacheEntry) {
            long now = System.currentTimeMillis();
            if (isPdfSearchCacheExpired(cacheEntry, now)) {
                resetPdfSearchCacheEntry(cacheEntry);
            }

            ensurePdfSearchCache(cacheEntry, sessionId, title, authorName, searchMode, prefetchCount, stats);

            int startIndex = Math.max(0, (page - 1) * limit);
            List<PdfSearchResult> pageResults = slicePdfSearchResults(cacheEntry.rankedResults, startIndex, limit);
            boolean hasNext = cacheEntry.rankedResults.size() > page * limit;
            cacheEntry.updatedAtMs = now;
            return new PdfSearchPageResult(pageResults, hasNext);
        }
    }

    private void ensurePdfSearchCache(PdfSearchCacheEntry cacheEntry,
                                      String sessionId,
                                      String title,
                                      String authorName,
                                      String searchMode,
                                      int targetCount,
                                      PdfSearchStats stats)
            throws IOException, InterruptedException {
        while (!cacheEntry.exhausted
                && cacheEntry.rankedResults.size() < targetCount
                && cacheEntry.nextSourcePage <= PDF_SEARCH_MAX_SOURCE_PAGES) {
            List<PdfSearchResult> fetchedCandidates = fetchPdfSearchCandidates(title, authorName, cacheEntry.nextSourcePage, searchMode, stats);
            if (fetchedCandidates.isEmpty()) {
                cacheEntry.exhausted = true;
                break;
            }

            mergePdfResults(cacheEntry.candidates, fetchedCandidates, PDF_SEARCH_CACHE_MAX_RESULTS);
            cacheEntry.rankedResults = rankPdfSearchResults(cacheEntry.candidates, title, authorName, searchMode);
            cacheEntry.nextSourcePage += 1;

            if (cacheEntry.rankedResults.size() >= PDF_SEARCH_CACHE_MAX_RESULTS) {
                break;
            }
        }

        if (cacheEntry.nextSourcePage > PDF_SEARCH_MAX_SOURCE_PAGES) {
            cacheEntry.exhausted = true;
        }
    }

    private List<PdfSearchResult> fetchPdfSearchCandidates(String title,
                                                          String authorName,
                                                          int sourcePage,
                                                          String searchMode,
                                                          PdfSearchStats stats)
            throws IOException, InterruptedException {
        List<PdfSearchResult> results = new ArrayList<>();
        try {
            mergePdfResults(results, searchArchivePdfSources(title, authorName, PDF_SEARCH_LIMIT, sourcePage, searchMode, stats), PDF_SEARCH_CACHE_MAX_RESULTS);
        } catch (Exception e) {
            System.err.println("[PDF FETCH] Archive search failed: " + e.getMessage());
        }
        try {
            mergePdfResults(results, searchGoogleBooksPdfSources(title, authorName, PDF_SEARCH_LIMIT, sourcePage, searchMode, stats), PDF_SEARCH_CACHE_MAX_RESULTS);
        } catch (Exception e) {
            System.err.println("[PDF FETCH] Google Books search failed: " + e.getMessage());
        }
        return results;
    }

    private List<PdfSearchResult> rankPdfSearchResults(List<PdfSearchResult> candidates,
                                                       String title,
                                                       String authorName,
                                                       String searchMode) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<PdfSearchResult> filtered = new ArrayList<>();
        for (PdfSearchResult candidate : candidates) {
            if (isExactPdfSearchMode(searchMode)) {
                if (matchesStrictPdfQuery(candidate, title, authorName)) {
                    filtered.add(candidate);
                }
            } else {
                int score = scorePdfResult(candidate, normalizeSearchTerm(title), normalizeSearchTerm(authorName));
                if (score >= 18) {
                    filtered.add(candidate);
                }
            }
        }

        if (filtered.isEmpty()) {
            return filtered;
        }

        if (isExactPdfSearchMode(searchMode)) {
            filtered.sort((left, right) -> {
                int leftScore = scorePdfResult(left, normalizeSearchTerm(title), normalizeSearchTerm(authorName));
                int rightScore = scorePdfResult(right, normalizeSearchTerm(title), normalizeSearchTerm(authorName));
                if (leftScore != rightScore) {
                    return Integer.compare(rightScore, leftScore);
                }
                return left.title().compareToIgnoreCase(right.title());
            });
            return filtered;
        }

        List<String> rerankedIds = rerankPdfResultsWithLocalModel(filtered, title, authorName, searchMode);
        if (!rerankedIds.isEmpty()) {
            Map<String, Integer> order = new LinkedHashMap<>();
            for (int index = 0; index < rerankedIds.size(); index += 1) {
                order.put(rerankedIds.get(index), index);
            }
            filtered.sort((left, right) -> {
                Integer leftIndex = order.get(left.identifier());
                Integer rightIndex = order.get(right.identifier());
                if (leftIndex != null || rightIndex != null) {
                    if (leftIndex == null) {
                        return 1;
                    }
                    if (rightIndex == null) {
                        return -1;
                    }
                    if (!leftIndex.equals(rightIndex)) {
                        return Integer.compare(leftIndex, rightIndex);
                    }
                }

                int leftScore = scorePdfResult(left, normalizeSearchTerm(title), normalizeSearchTerm(authorName));
                int rightScore = scorePdfResult(right, normalizeSearchTerm(title), normalizeSearchTerm(authorName));
                if (leftScore != rightScore) {
                    return Integer.compare(rightScore, leftScore);
                }
                return left.title().compareToIgnoreCase(right.title());
            });
        } else {
            filtered.sort((left, right) -> {
                int leftScore = scorePdfResult(left, normalizeSearchTerm(title), normalizeSearchTerm(authorName));
                int rightScore = scorePdfResult(right, normalizeSearchTerm(title), normalizeSearchTerm(authorName));
                if (leftScore != rightScore) {
                    return Integer.compare(rightScore, leftScore);
                }
                return left.title().compareToIgnoreCase(right.title());
            });
        }

        return filtered;
    }

    private List<String> rerankPdfResultsWithLocalModel(List<PdfSearchResult> candidates,
                                                        String title,
                                                        String authorName,
                                                        String searchMode) {
        String provider = nullToEmpty(System.getenv(PDF_RERANKER_PROVIDER_ENV)).trim().toLowerCase(Locale.ROOT);
        if (provider.isBlank() || provider.equals("none") || provider.equals("off") || provider.equals("heuristic")) {
            return List.of();
        }

        try {
            if (provider.equals("ollama")) {
                return rerankPdfResultsWithOllama(candidates, title, authorName, searchMode);
            }
            if (provider.equals("openai")) {
                return rerankPdfResultsWithOpenAiCompatibleApi(candidates, title, authorName, searchMode);
            }
        } catch (Exception ignored) {
            // Fall back to heuristic ranking when the local model endpoint is unavailable.
        }

        return List.of();
    }

    private List<String> rerankPdfResultsWithOllama(List<PdfSearchResult> candidates,
                                                    String title,
                                                    String authorName,
                                                    String searchMode) throws IOException, InterruptedException {
        String baseUrl = nullToEmpty(System.getenv(PDF_RERANKER_URL_ENV)).trim();
        if (baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }
        String model = nullToEmpty(System.getenv(PDF_RERANKER_MODEL_ENV)).trim();
        if (model.isBlank()) {
            model = "llama3.1";
        }

        String prompt = buildPdfRerankPrompt(title, authorName, searchMode, candidates);
        String payload = "{" +
                "\"model\":\"" + JsonUtil.escape(model) + "\"," +
                "\"prompt\":\"" + JsonUtil.escape(prompt) + "\"," +
                "\"stream\":false," +
                "\"format\":\"json\"," +
                "\"options\":{" +
                "\"temperature\":0" +
                "}" +
                "}";
        String response = httpPostJson(baseUrl + "/api/generate", payload, Map.of("Content-Type", "application/json"));
            String modelJson = extractJsonStringField(response, "response");
            String rankingJson = modelJson.isBlank() ? response : modelJson;
        return extractJsonStringArray(rankingJson, "rankedIds");
    }

    private List<String> rerankPdfResultsWithOpenAiCompatibleApi(List<PdfSearchResult> candidates,
                                                                 String title,
                                                                 String authorName,
                                                                 String searchMode) throws IOException, InterruptedException {
        String endpoint = nullToEmpty(System.getenv(PDF_RERANKER_URL_ENV)).trim();
        if (endpoint.isBlank()) {
            return List.of();
        }
        String model = nullToEmpty(System.getenv(PDF_RERANKER_MODEL_ENV)).trim();
        if (model.isBlank()) {
            model = "local-model";
        }

        String prompt = buildPdfRerankPrompt(title, authorName, searchMode, candidates);
        String payload = "{" +
                "\"model\":\"" + JsonUtil.escape(model) + "\"," +
                "\"temperature\":0," +
                "\"messages\":[{" +
                "\"role\":\"system\",\"content\":\"You rank librarian PDF search results. Return only JSON.\"},{" +
                "\"role\":\"user\",\"content\":\"" + JsonUtil.escape(prompt) + "\"}" +
                "]}";
        String response = httpPostJson(endpoint, payload, Map.of("Content-Type", "application/json"));
        List<String> rankedIds = new ArrayList<>();
        for (String choice : extractJsonObjectsFromArray(response, "choices")) {
            String message = extractJsonBlock(choice, "message");
            String parsed = extractJsonStringField(message, "content");
            rankedIds = extractJsonStringArray(parsed, "rankedIds");
            if (!rankedIds.isEmpty()) {
                break;
            }
        }
        return rankedIds;
    }

    private String buildPdfRerankPrompt(String title, String authorName, String searchMode, List<PdfSearchResult> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Rank PDF search candidates for a librarian.\n");
        prompt.append("Return only JSON in the form {\"rankedIds\":[...]} .\n");
        prompt.append("Exclude unrelated items entirely.\n");
        prompt.append("Search mode: ").append(searchMode).append("\n");
        prompt.append("Query title: ").append(title == null ? "" : title.trim()).append("\n");
        prompt.append("Query author: ").append(authorName == null ? "" : authorName.trim()).append("\n");
        prompt.append("Candidates:\n");
        for (PdfSearchResult candidate : candidates) {
            prompt.append("- id: ").append(candidate.identifier())
                    .append(" | title: ").append(candidate.title())
                    .append(" | authors: ").append(String.join(", ", candidate.authors()))
                    .append(" | source: ").append(candidate.source())
                    .append('\n');
        }
        prompt.append("Rank by title/author fit first, then overall semantic closeness.");
        return prompt.toString();
    }

    private boolean matchesStrictPdfQuery(PdfSearchResult candidate, String title, String authorName) {
        // OR logic: match if title matches OR author matches (or both)
        boolean titleMatches = matchesStrictSearchText(candidate.title(), title);
        if (titleMatches) {
            return true;
        }

        if (authorName != null && !authorName.isBlank()) {
            String authorText = String.join(" ", candidate.authors());
            if (matchesStrictSearchText(authorText, authorName)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesStrictSearchText(String candidateText, String queryText) {
        String normalizedCandidate = normalizeSearchTerm(candidateText);
        String normalizedQuery = normalizeSearchTerm(queryText);
        if (normalizedQuery.isBlank()) {
            return true;
        }
        if (normalizedCandidate.equals(normalizedQuery)) {
            return true;
        }
        if (normalizedCandidate.startsWith(normalizedQuery + " ")) {
            return true;
        }
        if (normalizedCandidate.contains(" " + normalizedQuery + " ")) {
            return true;
        }
        if (normalizedCandidate.endsWith(" " + normalizedQuery)) {
            return true;
        }
        return normalizedCandidate.contains(normalizedQuery);
    }

    private void cleanupExpiredPdfSearchCache() {
        long now = System.currentTimeMillis();
        long cutoff = now - (PDF_SEARCH_CACHE_MAX_AGE_MINUTES * 60L * 1000L);
        pdfSearchCache.entrySet().removeIf(entry -> entry.getValue().updatedAtMs > 0 && entry.getValue().updatedAtMs < cutoff);
    }

    private boolean isPdfSearchCacheExpired(PdfSearchCacheEntry cacheEntry, long now) {
        long cutoff = now - (PDF_SEARCH_CACHE_MAX_AGE_MINUTES * 60L * 1000L);
        return cacheEntry.updatedAtMs > 0 && cacheEntry.updatedAtMs < cutoff;
    }

    private void resetPdfSearchCacheEntry(PdfSearchCacheEntry cacheEntry) {
        cacheEntry.candidates.clear();
        cacheEntry.rankedResults = new ArrayList<>();
        cacheEntry.nextSourcePage = 1;
        cacheEntry.exhausted = false;
        cacheEntry.updatedAtMs = 0L;
    }

    private List<PdfSearchResult> slicePdfSearchResults(List<PdfSearchResult> results, int startIndex, int limit) {
        if (results == null || results.isEmpty() || limit <= 0 || startIndex >= results.size()) {
            return List.of();
        }
        int endIndex = Math.min(results.size(), startIndex + limit);
        return new ArrayList<>(results.subList(startIndex, endIndex));
    }

    private String buildPdfSearchCacheKey(String sessionId, String title, String authorName, String searchMode) {
        return normalizeSearchTerm(nullToEmpty(sessionId)) + "|"
                + normalizeSearchTerm(title) + "|"
                + normalizeSearchTerm(authorName) + "|"
                + normalizePdfSearchMode(searchMode);
    }

    private String httpPostJson(String url, String body, Map<String, String> headers) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Request failed. Status: " + response.statusCode());
        }
        return response.body();
    }

    private List<PdfSearchResult> searchPublicDomainPdfSources(String title, String authorName, int limit, int page, String searchMode, PdfSearchStats stats)
            throws IOException, InterruptedException {
        int perSourceLimit = Math.max(1, Math.min(limit, 5));
        List<PdfSearchResult> results = new ArrayList<>();
        try {
            List<PdfSearchResult> archiveResults = searchArchivePdfSources(title, authorName, perSourceLimit, page, searchMode, stats);
            mergePdfResults(results, archiveResults, limit);
        } catch (Exception e) {
            System.err.println("[PDF SEARCH] Archive search failed: " + e.getMessage());
        }

        try {
            List<PdfSearchResult> googleResults = searchGoogleBooksPdfSources(title, authorName, perSourceLimit, page, searchMode, stats);
            mergePdfResults(results, googleResults, limit);
        } catch (Exception e) {
            System.err.println("[PDF SEARCH] Google Books search failed: " + e.getMessage());
        }

        sortPdfResultsByRelevance(results, title, authorName);

        return results;
    }

    private void sortPdfResultsByRelevance(List<PdfSearchResult> results, String title, String authorName) {
        if (results == null || results.size() < 2) {
            return;
        }

        String normalizedTitle = normalizeSearchTerm(title);
        String normalizedAuthor = normalizeSearchTerm(authorName);
        results.sort((left, right) -> {
            int leftScore = scorePdfResult(left, normalizedTitle, normalizedAuthor);
            int rightScore = scorePdfResult(right, normalizedTitle, normalizedAuthor);
            if (leftScore != rightScore) {
                return Integer.compare(rightScore, leftScore);
            }
            return left.title().compareToIgnoreCase(right.title());
        });
    }

    private int scorePdfResult(PdfSearchResult result, String normalizedTitle, String normalizedAuthor) {
        int score = 0;
        String normalizedResultTitle = normalizeSearchTerm(result.title());
        String normalizedSource = normalizeSearchTerm(result.source());
        String normalizedResultAuthors = normalizeSearchTerm(String.join(" ", result.authors()));
        if (!normalizedTitle.isBlank()) {
            if (normalizedResultTitle.equals(normalizedTitle)) {
                score += 100;
            } else if (normalizedResultTitle.contains(normalizedTitle)) {
                score += 70;
            } else if (normalizedTitle.contains(normalizedResultTitle) && !normalizedResultTitle.isBlank()) {
                score += 50;
            }
            if (!normalizedResultAuthors.isBlank()
                    && (normalizedResultAuthors.contains(normalizedTitle) || normalizedTitle.contains(normalizedResultAuthors))) {
                score += 25;
            }
        }
        if (!normalizedAuthor.isBlank()) {
            if (normalizedResultTitle.contains(normalizedAuthor)) {
                score += 30;
            }
            if (!normalizedResultAuthors.isBlank()
                    && (normalizedResultAuthors.contains(normalizedAuthor) || normalizedAuthor.contains(normalizedResultAuthors))) {
                score += 40;
            }
        }
        if (normalizedSource.contains("google")) {
            score += 5;
        }
        return score;
    }

    private String normalizeSearchTerm(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private List<PdfSearchResult> searchArchivePdfSources(String title, String authorName, int limit, int page, String searchMode, PdfSearchStats stats)
            throws IOException, InterruptedException {
        String query = buildArchiveSearchQuery(title, authorName, searchMode);
        List<PdfSearchResult> results = searchArchiveByQuery(query, limit, page, stats);
        
        // Fallback to relaxed search if exact/main search returns no results
        if (results.isEmpty() && isExactPdfSearchMode(searchMode)) {
            String relaxedQuery = buildArchiveSearchQueryRelaxed(title, authorName);
            results = searchArchiveByQuery(relaxedQuery, limit, page, stats);
        }
        
        return results;
    }

    private List<PdfSearchResult> searchArchiveByQuery(String query, int limit, int page, PdfSearchStats stats)
            throws IOException, InterruptedException {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String searchUrl = "https://archive.org/advancedsearch.php?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&fl[]=identifier&fl[]=title&rows=" + limit
                + "&page=" + Math.max(1, page)
                + "&output=json";
        String searchPayload = httpGetArchive(searchUrl);
        List<PdfSearchResult> candidates = parseArchiveSearchResults(searchPayload, limit);
        stats.archiveCandidates += candidates.size();
        List<PdfSearchResult> results = new ArrayList<>();
        for (PdfSearchResult candidate : candidates) {
            String pdfUrl = resolveArchivePdfUrl(candidate.identifier());
            if (!pdfUrl.isBlank()) {
                results.add(new PdfSearchResult("archive:" + candidate.identifier(), candidate.title(), pdfUrl, "Internet Archive", List.of()));
            }
        }
        stats.archiveWithPdf += results.size();
        return results;
    }

    private List<PdfSearchResult> searchGoogleBooksPdfSources(String title, String authorName, int limit, int page, String searchMode, PdfSearchStats stats)
            throws IOException, InterruptedException {
        String apiKey = nullToEmpty(System.getenv("GOOGLE_BOOKS_API_KEY")).trim();
        if (apiKey.isBlank()) {
            return List.of();
        }

        String query = buildGoogleBooksQuery(title, authorName, searchMode);
        if (query.isBlank()) {
            return List.of();
        }

        List<PdfSearchResult> results = searchGoogleBooksByQuery(query, apiKey, limit, title, authorName, page, stats);
        // No auto-fallback to partial: if no results in exact mode, return empty
        return results;
    }

    private List<PdfSearchResult> searchGoogleBooksByQuery(String query, String apiKey, int limit, String title, String authorName, int page, PdfSearchStats stats)
            throws IOException, InterruptedException {
        String searchUrl = "https://www.googleapis.com/books/v1/volumes?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&maxResults=" + Math.min(Math.max(limit, 1), 40)
                + "&startIndex=" + Math.max(0, (Math.max(1, page) - 1) * Math.max(1, limit))
                + "&filter=free-ebooks"
                + "&orderBy=relevance"
                + "&printType=books"
                + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String payload = httpGet(searchUrl);
        List<PdfSearchResult> results = parseGoogleBooksPdfResults(payload, limit);
        sortPdfResultsByRelevance(results, title, authorName);
        stats.googleWithPdf += results.size();

        if (!results.isEmpty()) {
            return results;
        }

        List<GoogleBookCandidate> candidates = parseGoogleBooksCandidates(payload, limit);
        stats.googleCandidates += candidates.size();
        List<PdfSearchResult> fallbackResults = new ArrayList<>();
        for (GoogleBookCandidate candidate : candidates) {
            if (fallbackResults.size() >= limit) {
                break;
            }
            PdfSearchResult fallback = fetchGoogleBookPdfById(candidate.id(), candidate.title(), apiKey);
            if (fallback != null) {
                fallbackResults.add(fallback);
            }
        }
        sortPdfResultsByRelevance(fallbackResults, title, authorName);
        stats.googleFallbackWithPdf += fallbackResults.size();
        return fallbackResults;
    }

    private void mergePdfResults(List<PdfSearchResult> target, List<PdfSearchResult> additions, int limit) {
        if (target.size() >= limit || additions == null || additions.isEmpty()) {
            return;
        }

        for (PdfSearchResult result : additions) {
            boolean exists = target.stream().anyMatch(item -> item.downloadUrl().equalsIgnoreCase(result.downloadUrl()));
            if (!exists) {
                target.add(result);
            }
            if (target.size() >= limit) {
                return;
            }
        }
    }

    private String buildArchiveSearchQuery(String title, String authorName, String searchMode) {
        List<String> keywords = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            keywords.add(title.trim());
        }
        if (authorName != null && !authorName.isBlank()) {
            keywords.add(authorName.trim());
        }

        if (keywords.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (isExactPdfSearchMode(searchMode) || keywords.size() == 1) {
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) {
                    sb.append(" AND ");
                }
                sb.append('"').append(keywords.get(i)).append('"');
            }
        } else {
            sb.append("(");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) {
                    sb.append(" OR ");
                }
                sb.append('"').append(keywords.get(i)).append('"');
            }
            sb.append(")");
        }
        return sb.toString();
    }

    private String buildArchiveSearchQueryRelaxed(String title, String authorName) {
        List<String> keywords = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            keywords.add(title.trim());
        }
        if (authorName != null && !authorName.isBlank()) {
            keywords.add(authorName.trim());
        }

        if (keywords.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            sb.append('"').append(keywords.get(i)).append('"');
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildOrQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] tokens = raw.trim().split("\\s+");
        StringJoiner joiner = new StringJoiner(" OR ");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                joiner.add(trimmed);
            }
        }
        return joiner.toString();
    }

    private String buildGoogleBooksQuery(String title, String authorName, String searchMode) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append("intitle:");
            if (isExactPdfSearchMode(searchMode)) {
                sb.append('"').append(buildExactQuery(title)).append('"');
            } else {
                sb.append(title.trim());
            }
        }
        if (authorName != null && !authorName.isBlank()) {
            if (sb.length() > 0) {
                sb.append("+");
            }
            sb.append("inauthor:");
            if (isExactPdfSearchMode(searchMode)) {
                sb.append('"').append(buildExactQuery(authorName)).append('"');
            } else {
                sb.append(authorName.trim());
            }
        }
        return sb.toString();
    }

    private String buildGoogleBooksQueryRelaxed(String title, String authorName) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.trim());
        }
        if (authorName != null && !authorName.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(authorName.trim());
        }
        return sb.toString();
    }

    private boolean isExactPdfSearchMode(String searchMode) {
        return "exact".equalsIgnoreCase(nullToEmpty(searchMode).trim());
    }

    private String normalizePdfSearchMode(String searchMode) {
        return isExactPdfSearchMode(searchMode) ? "exact" : "partial";
    }

    private String buildExactQuery(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replace("\\", " ").replace("\"", " ").replaceAll("\\s+", " ").trim();
    }

    private record GoogleBookCandidate(String id, String title) {
    }

    private List<GoogleBookCandidate> parseGoogleBooksCandidates(String json, int limit) {
        List<GoogleBookCandidate> results = new ArrayList<>();
        for (String chunk : extractJsonObjectsFromArray(json, "items")) {
            if (results.size() >= limit) {
                break;
            }
            String id = extractJsonField(chunk, "id");
            if (id.isBlank()) {
                continue;
            }
            String title = extractJsonField(chunk, "title");
            results.add(new GoogleBookCandidate(id, title));
        }
        return results;
    }

    private PdfSearchResult fetchGoogleBookPdfById(String id, String fallbackTitle, String apiKey)
            throws IOException, InterruptedException {
        if (id == null || id.isBlank()) {
            return null;
        }
        String url = "https://www.googleapis.com/books/v1/volumes/"
                + URLEncoder.encode(id, StandardCharsets.UTF_8)
                + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String payload = httpGet(url);
            String accessInfoBlock = extractJsonBlock(payload, "accessInfo");
            String pdfBlock = extractJsonBlock(accessInfoBlock, "pdf");
        if (pdfBlock.isBlank()) {
            return null;
        }
        boolean available = pdfBlock.contains("\"isAvailable\":true");
        String downloadLink = extractJsonField(pdfBlock, "downloadLink");
        if (downloadLink.isBlank()) {
            downloadLink = extractJsonField(pdfBlock, "acsTokenLink");
        }
        if (!available || downloadLink.isBlank()) {
            return null;
        }
        String title = extractJsonField(payload, "title");
        String safeTitle = title.isBlank() ? (fallbackTitle == null ? "Google Books PDF" : fallbackTitle) : title;
        List<String> authors = extractJsonStringArray(payload, "authors");
        return new PdfSearchResult("google:" + id, safeTitle, unescapeJsonString(downloadLink), "Google Books", authors);
    }

    private List<PdfSearchResult> parseGoogleBooksPdfResults(String json, int limit) {
        List<PdfSearchResult> results = new ArrayList<>();
        for (String chunk : extractJsonObjectsFromArray(json, "items")) {
            if (results.size() >= limit) {
                break;
            }
            String id = extractJsonField(chunk, "id");
            String title = extractJsonField(chunk, "title");
            String accessInfoBlock = extractJsonBlock(chunk, "accessInfo");
            String pdfBlock = extractJsonBlock(accessInfoBlock, "pdf");
            if (pdfBlock.isBlank()) {
                continue;
            }
            boolean available = pdfBlock.contains("\"isAvailable\":true");
            String downloadLink = extractJsonField(pdfBlock, "downloadLink");
            if (downloadLink.isBlank()) {
                downloadLink = extractJsonField(pdfBlock, "acsTokenLink");
            }
            if (!available || downloadLink.isBlank()) {
                continue;
            }
            String safeTitle = title.isBlank() ? "Google Books PDF" : title;
            String identifier = id.isBlank() ? "google-books" : "google:" + id;
            List<String> authors = extractJsonStringArray(chunk, "authors");
            results.add(new PdfSearchResult(identifier, safeTitle, unescapeJsonString(downloadLink), "Google Books", authors));
        }

        return results;
    }

    private List<String> extractJsonStringArray(String json, String fieldName) {
        List<String> values = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return values;
        }

        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return values;
        }

        String arrayBody = matcher.group(1);
        Pattern valuePattern = Pattern.compile("\\\"((?:\\\\.|[^\\\\\"])*)\\\"");
        Matcher valueMatcher = valuePattern.matcher(arrayBody);
        while (valueMatcher.find()) {
            values.add(unescapeJsonString(valueMatcher.group(1)));
        }
        return values;
    }

    private String extractJsonBlock(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return "{" + matcher.group(1) + "}";
        }
        return "";
    }

    private List<PdfSearchResult> parseArchiveSearchResults(String json, int limit) {
        List<PdfSearchResult> results = new ArrayList<>();
        for (String doc : extractJsonObjectsFromArray(json, "docs")) {
            if (results.size() >= limit) {
                break;
            }
            String identifier = extractJsonField(doc, "identifier");
            String title = extractJsonField(doc, "title");
            if (identifier.isBlank()) {
                continue;
            }
            results.add(new PdfSearchResult(identifier, title, "", "Internet Archive", List.of()));
        }
        return results;
    }

    private List<String> extractJsonObjectsFromArray(String json, String arrayFieldName) {
        List<String> objects = new ArrayList<>();
        if (json == null || json.isBlank() || arrayFieldName == null || arrayFieldName.isBlank()) {
            return objects;
        }

        String searchToken = "\"" + arrayFieldName + "\"";
        int fieldIndex = json.indexOf(searchToken);
        if (fieldIndex < 0) {
            return objects;
        }

        int arrayStart = json.indexOf('[', fieldIndex);
        if (arrayStart < 0) {
            return objects;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int objectStart = -1;
        for (int i = arrayStart + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
                continue;
            }
            if (ch == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && objectStart >= 0) {
                        objects.add(json.substring(objectStart, i + 1));
                        objectStart = -1;
                    }
                }
                continue;
            }
            if (ch == ']' && depth == 0) {
                break;
            }
        }
        return objects;
    }

    private String resolveArchivePdfUrl(String identifier) throws IOException, InterruptedException {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        String metadataUrl = "https://archive.org/metadata/" + URLEncoder.encode(identifier, StandardCharsets.UTF_8);
        String metadataJson = httpGet(metadataUrl);
        if (metadataJson.isBlank()) {
            return "";
        }
        
        // Look for PDF files in the files array - simpler pattern
        // Pattern to extract PDF file names from JSON
        Pattern filePattern = Pattern.compile("\\\"name\\\":\\\"([^\\\"]+?\\.pdf)\\\"");
        Matcher matcher = filePattern.matcher(metadataJson);
        
        while (matcher.find()) {
            String fileName = unescapeJsonString(matcher.group(1));
            if (!fileName.isBlank()) {
                // Return first valid PDF found
                return "https://archive.org/download/" + URLEncoder.encode(identifier, StandardCharsets.UTF_8) + "/" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private DownloadedPdf downloadRequestedPdf(String pdfUrl, String requestId, String title)
            throws IOException, InterruptedException {
        if (pdfUrl == null || pdfUrl.isBlank()) {
            throw new IllegalArgumentException("PDF URL is required.");
        }
        URI uri = URI.create(pdfUrl.trim());
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only http/https PDF links are supported.");
        }

        Files.createDirectories(REQUESTED_BOOK_DIR);
        Path requestDir = REQUESTED_BOOK_DIR.resolve(sanitizeFileName(requestId));
        Files.createDirectories(requestDir);

        String safeTitle = sanitizeFileName(title);
        String fileName = safeTitle.isBlank() ? "requested-book" : safeTitle;
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            fileName = fileName + ".pdf";
        }
        Path targetPath = requestDir.resolve(fileName);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", "LibraryBookRequestBot/1.0")
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to download PDF. Status: " + response.statusCode());
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        boolean looksLikePdf = contentType.toLowerCase(Locale.ROOT).contains("pdf")
                || pdfUrl.toLowerCase(Locale.ROOT).contains(".pdf");
        if (!looksLikePdf) {
            throw new IllegalArgumentException("URL does not appear to be a PDF.");
        }

        long contentLength = response.headers().firstValue("Content-Length")
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ignored) {
                        return -1L;
                    }
                })
                .orElse(-1L);
        if (contentLength > SecurityConfig.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("PDF is too large. Max size is " + SecurityConfig.MAX_FILE_SIZE_BYTES + " bytes.");
        }

        try (InputStream inputStream = response.body()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        long size = Files.size(targetPath);
        if (size > SecurityConfig.MAX_FILE_SIZE_BYTES) {
            Files.deleteIfExists(targetPath);
            throw new IllegalArgumentException("PDF is too large. Max size is " + SecurityConfig.MAX_FILE_SIZE_BYTES + " bytes.");
        }

        if (!isValidPdfFile(targetPath)) {
            Files.deleteIfExists(targetPath);
            throw new IllegalArgumentException("Downloaded file is not a valid PDF.");
        }

        String resolvedContentType = contentType.isBlank()
                ? detectContentType(fileName.toLowerCase(Locale.ROOT))
                : contentType;
        return new DownloadedPdf(targetPath.toString(), resolvedContentType);
    }

    private void proxyDownloadPdf(HttpExchange exchange, String pdfUrl, String fileName)
            throws IOException, InterruptedException {
        URI uri = URI.create(pdfUrl.trim());
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only http/https PDF links are supported.");
        }

        String safeName = sanitizeFileName(fileName);
        if (safeName.isBlank()) {
            safeName = "download.pdf";
        } else if (!safeName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            safeName = safeName + ".pdf";
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/pdf,application/octet-stream,*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://archive.org/")
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String message = switch (status) {
                case 403 -> "Archive denied the download request (403).";
                case 502 -> "Archive returned a bad gateway (502).";
                case 503 -> "Archive is temporarily unavailable (503).";
                default -> "Failed to download PDF. Status: " + status;
            };
            throw new IOException(message);
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("application/pdf");
        String contentLengthHeader = response.headers().firstValue("Content-Length").orElse(null);
        long contentLength = -1L;
        if (contentLengthHeader != null) {
            try {
                contentLength = Long.parseLong(contentLengthHeader);
            } catch (NumberFormatException ignored) {
                contentLength = -1L;
            }
        }

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + safeName.replace("\"", "") + "\"");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, contentLength >= 0 ? contentLength : 0);
        try (InputStream inputStream = response.body(); OutputStream outputStream = exchange.getResponseBody()) {
            inputStream.transferTo(outputStream);
        } finally {
            exchange.close();
        }
    }

    private boolean isValidPdfFile(Path file) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] header = new byte[4];
            int read = inputStream.read(header);
            return read == 4
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F';
        } catch (IOException e) {
            return false;
        }
    }

    private String sanitizeFileName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return normalized;
    }

    private String generateBookRequestSummary(String title,
                                              String authorName,
                                              List<String> genres,
                                              String reason,
                                              String content,
                                              int summaryWordLimit) {
        String fallback = generateBookDescriptionSuggestion(title, authorName, genres);

        try {
            String summary = callInferenceSummary(title, authorName, genres, reason, content, summaryWordLimit);
            return summary.isBlank() ? fallback : summary;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String generateAuthorSubmissionSummary(String authorName,
                                                   String title,
                                                   List<String> genres,
                                                   String note,
                                                   String content,
                                                   int summaryWordLimit) {
        String summary = generateBookRequestSummary(title, authorName, genres, note, content, summaryWordLimit);
        return summary == null ? "" : summary.trim();
    }

    private String callInferenceSummary(String title,
                                        String authorName,
                                        List<String> genres,
                                        String reason,
                                        String content,
                                        int summaryWordLimit) throws IOException, InterruptedException {
        int cappedLimit = parseSummaryWordLimit(String.valueOf(summaryWordLimit));
        String genreText = genres == null || genres.isEmpty() ? "" : String.join(", ", genres);
        String extractedContent = sanitizeText(nullToEmpty(content).trim());
        String contentForPrompt = extractedContent.isBlank() ? "" : limitWords(extractedContent, cappedLimit);
        if (!extractedContent.isBlank()) {
            String preview = limitWords(extractedContent, cappedLimit);
            System.out.println("[AI] Extracted text preview (first 2 pages, " + cappedLimit + " words max): " + preview);
        }
        int sentenceCount = summaryWordLimitToSentenceCount(cappedLimit);
        int topicCount = summaryWordLimitToTopicCount(cappedLimit);
        String topics = extractKeyPhrases(contentForPrompt, topicCount);
        String draftSummary = buildRuleBasedSummary(title, genreText, topics, sentenceCount);
        String prompt = "<|im_start|>system\n"
            + "You are a helpful library catalog assistant.<|im_end|>\n"
            + "<|im_start|>user\n"
            + "Write " + sentenceCount + " sentence" + (sentenceCount == 1 ? "" : "s")
            + " and keep it under " + cappedLimit + " words. "
            + "Return only the summary.\n\n"
            + "Draft summary: " + draftSummary + "\n"
            + "Title: " + title + "\n"
            + (genreText.isBlank() ? "" : "Genres: " + genreText + "\n")
            + (topics.isBlank() ? "" : "Topics: " + topics + "\n")
            + "<|im_end|>\n"
            + "<|im_start|>assistant\n";
        System.out.println("[AI] Prompt debug: " + prompt);
        String modelPath = resolveGgufModelPath();
        ModelParameters modelParameters = new ModelParameters().setModel(modelPath);
        float temperature = summaryWordLimitToTemperature(cappedLimit);
        InferenceParameters inferParams = new InferenceParameters(prompt)
            .setTemperature(temperature)
            .setTopP(0.9f)
            .setTopK(20)
            .setRepeatPenalty(1.1f)
            .setStopStrings("<|im_end|>", "User:", "Assistant:")
            .setPenalizeNl(true);
        int timeoutMs = summaryWordLimitToTimeoutMs(cappedLimit);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            try (LlamaModel model = new LlamaModel(modelParameters)) {
                return model.complete(inferParams);
            }
        });

        try {
            String summary = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (summary == null) {
                return limitWordsToSentence(draftSummary, cappedLimit);
            }
            return limitWordsToSentence(summary.trim(), cappedLimit);
        } catch (TimeoutException e) {
            future.cancel(true);
            return limitWordsToSentence(draftSummary, cappedLimit);
        } catch (ExecutionException e) {
            return limitWordsToSentence(draftSummary, cappedLimit);
        } finally {
            executor.shutdownNow();
        }
    }

    private String resolveGgufModelPath() throws IOException {
        String configured = nullToEmpty(System.getenv(GGUF_MODEL_PATH_ENV)).trim();
        if (!configured.isBlank()) {
            return configured;
        }

        Path defaultPath = Paths.get(System.getProperty("user.dir"), "Library", "SmolLM2-135M-Instruct-Q3_K_XL.gguf");
        if (Files.exists(defaultPath)) {
            return defaultPath.toString();
        }

        throw new IOException("GGUF model not found. Set " + GGUF_MODEL_PATH_ENV + " to the model path.");
    }

    private static String limitWords(String text, int maxWords) {
        if (text == null || text.isBlank() || maxWords <= 0) {
            return "";
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length <= maxWords) {
            return text.trim();
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 0; i < maxWords; i += 1) {
            joiner.add(parts[i]);
        }
        return joiner + "...";
    }

    private static int parseSummaryWordLimit(String raw) {
        String level = nullToEmpty(raw).trim().toLowerCase(Locale.ROOT);
        if (level.matches("\\d+")) {
            try {
                return Integer.parseInt(level);
            } catch (NumberFormatException e) {
                return 100;
            }
        }
        return switch (level) {
            case "short" -> 50;
            case "medium" -> 100;
            case "detail", "detailed" -> 150;
            default -> 100;
        };
    }

    private static int summaryWordLimitToTimeoutMs(int wordLimit) {
        if (wordLimit <= 50) {
            return 5000;
        }
        if (wordLimit >= 150) {
            return 12000;
        }
        return 8000;
    }

    private static float summaryWordLimitToTemperature(int wordLimit) {
        if (wordLimit <= 50) {
            return 0.9f;
        }
        if (wordLimit >= 150) {
            return 0.5f;
        }
        return 0.7f;
    }

    private static int summaryWordLimitToSentenceCount(int wordLimit) {
        if (wordLimit <= 50) {
            return 1;
        }
        if (wordLimit >= 150) {
            return 3;
        }
        return 2;
    }

    private static int summaryWordLimitToTopicCount(int wordLimit) {
        if (wordLimit <= 50) {
            return 6;
        }
        if (wordLimit >= 150) {
            return 18;
        }
        return 10;
    }

    private static int summaryWordLimitToMaxTokens(int wordLimit) {
        if (wordLimit <= 20) {
            return 80;
        }
        if (wordLimit >= 70) {
            return 100;
        }
        return 90;
    }

    private static String limitWordsToSentence(String text, int maxWords) {
        if (text == null || text.isBlank() || maxWords <= 0) {
            return "";
        }
        String trimmed = text.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length <= maxWords) {
            return trimmed;
        }

        int wordCount = 0;
        int scanIndex = 0;
        for (String part : parts) {
            int nextIndex = trimmed.indexOf(part, scanIndex);
            if (nextIndex < 0) {
                break;
            }
            scanIndex = nextIndex + part.length();
            wordCount += 1;
            if (wordCount >= maxWords) {
                int periodIndex = trimmed.indexOf('.', scanIndex);
                if (periodIndex >= 0) {
                    return trimmed.substring(0, periodIndex + 1).trim();
                }
                return String.join(" ", java.util.Arrays.copyOfRange(parts, 0, maxWords)).trim();
            }
        }

        return String.join(" ", java.util.Arrays.copyOfRange(parts, 0, maxWords)).trim();
    }

    private static String buildRuleBasedSummary(String title, String genreText, String content, int sentenceCount) {
        String safeTitle = nullToEmpty(title).trim();
        String safeGenres = nullToEmpty(genreText).trim();
        List<String> topics = extractTopics(content, 6);

        String genreLabel = safeGenres.isBlank() ? "library" : safeGenres;
        if (topics.isEmpty()) {
            if (safeTitle.isBlank()) {
                return sentenceCount <= 1
                    ? "This " + genreLabel + " resource highlights key themes in the subject."
                    : "This " + genreLabel + " resource highlights key themes in the subject. It offers a concise overview.";
            }
            return sentenceCount <= 1
                ? "This " + genreLabel + " resource covers " + safeTitle + "."
                : "This " + genreLabel + " resource covers " + safeTitle + ". It frames the topic for quick understanding.";
        }

        String topicPhrase = buildTopicPhrase(topics);
        if (sentenceCount <= 1) {
            return "Covers " + topicPhrase + " in the context of " + genreLabel + ".";
        }
        if (sentenceCount == 2) {
            String titleClause = safeTitle.isBlank() ? "" : " It connects to " + safeTitle + ".";
            return "Covers " + topicPhrase + " in the context of " + genreLabel + "." + titleClause;
        }
        String titleClause = safeTitle.isBlank() ? "" : " It connects to " + safeTitle + ".";
        return "Covers " + topicPhrase + " in the context of " + genreLabel + "."
            + titleClause + " It highlights practical and conceptual takeaways.";
    }

    private static List<String> extractTopics(String content, int maxTopics) {
        List<String> topics = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return topics;
        }

        String normalized = content.replace(";", ",").replace(" and ", ",");
        String[] parts = normalized.split(",");
        for (String part : parts) {
            String topic = part.trim();
            if (topic.isEmpty()) {
                continue;
            }
            if (!topics.contains(topic)) {
                topics.add(topic);
            }
            if (topics.size() >= maxTopics) {
                break;
            }
        }
        return topics;
    }

    private static String extractKeyPhrases(String text, int maxTerms) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = sanitizeText(text)
            .replaceAll("[^A-Za-z\\s-]", " ")
            .replaceAll("\\b\\d+\\b", " ")
            .replaceAll("(?i)\\b(the|this|are|for|of|in|on|is|a|an|to|be|can|that|most|which|them|two|with|from|into|by|as|at|it|its|their|they|we|you|your|our|ours)\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (cleaned.isBlank()) {
            return "";
        }

        String[] tokens = cleaned.split(" ");
        List<String> unique = new ArrayList<>();
        for (String token : tokens) {
            String term = token.trim();
            if (term.isEmpty()) {
                continue;
            }
            String lower = term.toLowerCase(Locale.ROOT);
            if (unique.stream().noneMatch((existing) -> existing.equalsIgnoreCase(lower))) {
                unique.add(term);
            }
            if (unique.size() >= maxTerms) {
                break;
            }
        }

        return String.join(", ", unique);
    }

    private static String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("[^\\x20-\\x7E]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String buildTopicPhrase(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return "core topics";
        }
        if (topics.size() == 1) {
            return topics.get(0);
        }
        if (topics.size() == 2) {
            return topics.get(0) + " and " + topics.get(1);
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < topics.size() - 1; i += 1) {
            joiner.add(topics.get(i));
        }
        return joiner + ", and " + topics.get(topics.size() - 1);
    }

    private void notifyLibrariansForBookRequest(BookRequest2 request, User requester) {
        String requesterLabel = requester == null
            ? "A user"
            : requester.getFullName() + " (" + requester.getUsername() + ")";
        String message = requesterLabel + " submitted a new book request for \"" + request.getTitle() + "\".";
        for (User librarian : librarianService.listUsersForManagement("", "librarian", "active")) {
            if (librarian.getRole() != Role.LIBRARIAN) {
                continue;
            }
            notificationService.addNotification(
                    librarian.getUsername(),
                    "New Book Request",
                    message,
                    NotificationPriority.NORMAL,
                    null,
                    Map.of("type", "submission", "requestId", request.getId())
            );
        }
    }

    private String extractJsonField(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*\\\"(.*?)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractJsonStringField(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJsonString(matcher.group(1));
        }
        return "";
    }

    private String unescapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\\\\"", "\"")
                .replace("\\\\n", "\n")
                .replace("\\\\r", "\r")
                .replace("\\\\t", "\t")
                .replace("\\\\/", "/")
                .replace("\\\\\\\\", "\\");
    }

    private String httpGet(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", "LibraryBookRequestBot/1.0")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Request failed. Status: " + response.statusCode());
        }
        return response.body();
    }

    private String httpGetArchive(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        // Use a more realistic browser User-Agent for Archive.org to avoid 403 blocks
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://archive.org/")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 403) {
            throw new IOException("Access Forbidden (403) from Internet Archive. You may be rate-limited or the service may be temporarily blocking requests.");
        }
        if (status < 200 || status >= 300) {
            throw new IOException("Internet Archive request failed. Status: " + status);
        }
        return response.body();
    }

    private static String bulkDeleteResultToJson(AuthorService2.BulkDeleteResult result) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"deletedIds\":[");
        for (int i = 0; i < result.deletedIds().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(JsonUtil.escape(result.deletedIds().get(i))).append("\"");
        }
        sb.append("],\"skipped\":[");
        int idx = 0;
        for (Map.Entry<String, String> entry : result.skippedIdToReason().entrySet()) {
            if (idx++ > 0) sb.append(",");
            sb.append("{\"id\":\"").append(JsonUtil.escape(entry.getKey())).append("\",")
              .append("\"reason\":\"").append(JsonUtil.escape(entry.getValue())).append("\"}");
        }
        sb.append("],\"deletedCount\":").append(result.deletedIds().size());
        sb.append(",\"skippedCount\":").append(result.skippedIdToReason().size());
        sb.append("}");
        return sb.toString();
    }

    private static String authorSubmissionsToJson(List<BookSubmission2> submissions) {
        List<String> values = new ArrayList<>();
        for (BookSubmission2 submission : submissions) {
            List<String> genreValues = new ArrayList<>();
            for (String genre : submission.getGenres()) {
                genreValues.add("\"" + JsonUtil.escape(genre) + "\"");
            }

            values.add("{" +
                    "\"id\":\"" + JsonUtil.escape(submission.getId()) + "\"," +
                    "\"title\":\"" + JsonUtil.escape(submission.getTitle()) + "\"," +
                    "\"genres\":[" + String.join(",", genreValues) + "]," +
                    "\"description\":\"" + JsonUtil.escape(submission.getDescription()) + "\"," +
                    "\"fileName\":\"" + JsonUtil.escape(submission.getFileName()) + "\"," +
                    "\"coverImagePath\":\"" + JsonUtil.escape(submission.getCoverImagePath()) + "\"," +
                    "\"submittedDate\":\"" + submission.getSubmittedDate() + "\"," +
                    "\"status\":\"" + submission.getStatus() + "\"," +
                    "\"librarianComment\":\"" + JsonUtil.escape(nullToEmpty(submission.getLibrarianComment())) + "\"," +
                    "\"rejectionReason\":\"" + JsonUtil.escape(nullToEmpty(submission.getRejectionReason())) + "\"" +
                    "}");
        }
        return "[" + String.join(",", values) + "]";
    }

    private static String librarianSubmissionsToJson(List<BookSubmission2> submissions) {
        List<String> values = new ArrayList<>();
        for (BookSubmission2 submission : submissions) {
            List<String> genres = submission.getGenres();
            List<String> genreValues = new ArrayList<>();
            for (String genre : genres) {
                genreValues.add("\"" + JsonUtil.escape(genre) + "\"");
            }
            if (genreValues.isEmpty()) {
                genreValues.add("\"Unspecified\"");
            }
                String safeComment = nullToEmpty(submission.getLibrarianComment()).isBlank()
                    ? "No comment provided."
                    : submission.getLibrarianComment();
                String safeReason = nullToEmpty(submission.getRejectionReason()).isBlank()
                    ? "Unspecified"
                    : submission.getRejectionReason();
            String genreCsv = genres.isEmpty() ? "Unspecified" : String.join(", ", genres);
            values.add("{" +
                    "\"id\":\"" + JsonUtil.escape(submission.getId()) + "\"," +
                    "\"title\":\"" + JsonUtil.escape(submission.getTitle()) + "\"," +
                    "\"authorFullName\":\"" + JsonUtil.escape(submission.getAuthorFullName()) + "\"," +
                    "\"authorUsername\":\"" + JsonUtil.escape(submission.getAuthorUsername()) + "\"," +
                    "\"genres\":[" + String.join(",", genreValues) + "]," +
                    "\"genre\":\"" + JsonUtil.escape(genreCsv) + "\"," +
                    "\"fileName\":\"" + JsonUtil.escape(submission.getFileName()) + "\"," +
                    "\"submittedDate\":\"" + submission.getSubmittedDate() + "\"," +
                    "\"status\":\"" + submission.getStatus() + "\"," +
                    "\"librarianComment\":\"" + JsonUtil.escape(safeComment) + "\"," +
                    "\"comment\":\"" + JsonUtil.escape(safeComment) + "\"," +
                    "\"rejectionReason\":\"" + JsonUtil.escape(safeReason) + "\"," +
                    "\"reason\":\"" + JsonUtil.escape(safeReason) + "\"" +
                    "}");
        }
        return "[" + String.join(",", values) + "]";
    }

    private static String filePreviewToJson(AuthorService2.FilePreview preview) {
        String fileUrl = "";
        if ("submission".equals(preview.sourceType())) {
            fileUrl = "/api/author/submission/file?submissionId=" + JsonUtil.escape(preview.itemId());
        } else if ("published".equals(preview.sourceType())) {
            fileUrl = "/api/author/published-book/file?bookId=" + JsonUtil.escape(preview.itemId());
        }

        return "{" +
                "\"itemId\":\"" + JsonUtil.escape(preview.itemId()) + "\"," +
                "\"sourceType\":\"" + JsonUtil.escape(preview.sourceType()) + "\"," +
                "\"filePath\":\"" + JsonUtil.escape(preview.filePath()) + "\"," +
                "\"sizeBytes\":" + preview.sizeBytes() + "," +
                "\"previewType\":\"" + JsonUtil.escape(preview.previewType()) + "\"," +
                "\"fileUrl\":\"" + JsonUtil.escape(fileUrl) + "\"," +
                "\"previewText\":\"" + JsonUtil.escape(preview.previewText()) + "\"" +
                "}";
    }

    private static String resolvePreviewTypeFromPath(String filePath) {
        String lower = nullToEmpty(filePath).trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return "text";
        }
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".docx")) {
            return "docx";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
            return "image";
        }
        return "binary";
    }

    private static String profilePhotoUrl(User user) {
        String photoPath = user == null ? "" : nullToEmpty(user.getProfilePhotoPath()).trim();
        return photoPath.isBlank() ? "" : "/api/profile/photo";
    }

    private String storeProfilePhoto(String username, Role role, UploadedFile uploadedPhoto) throws IOException {
        if (uploadedPhoto == null || uploadedPhoto.path() == null) {
            return "";
        }

        fileService.validateCoverImageFile(uploadedPhoto.path().toString());
        Files.createDirectories(PROFILE_PHOTO_DIR);

        String originalName = nullToEmpty(uploadedPhoto.originalFileName()).trim().toLowerCase(Locale.ROOT);
        String extension = ".png";
        if (originalName.endsWith(".jpg") || originalName.endsWith(".jpeg")) {
            extension = ".jpg";
        } else if (originalName.endsWith(".png")) {
            extension = ".png";
        }

        String safeUsername = nullToEmpty(username).replaceAll("[^A-Za-z0-9._-]", "_");
        String safeRole = role == null ? "user" : role.name().toLowerCase(Locale.ROOT);
        String fileName = safeUsername + "-" + safeRole + "-photo" + extension;
        Path destination = PROFILE_PHOTO_DIR.resolve(fileName);
        Files.copy(uploadedPhoto.path(), destination, StandardCopyOption.REPLACE_EXISTING);
        return destination.toAbsolutePath().toString();
    }

    private String managedUsersToJson(List<User> users, List<BorrowRecord> borrows, String actingLibrarianUsername) {
        List<String> values = new ArrayList<>();
        for (User user : users) {
            int totalBorrowCount = 0;
            int activeBorrowCount = 0;
            for (BorrowRecord record : borrows) {
                if (record.getUsername().equals(user.getUsername())) {
                    totalBorrowCount++;
                    if (!record.isReturned()) {
                        activeBorrowCount++;
                    }
                }
            }

            List<String> activityItems = getRecentActivitiesForUser(user.getUsername(), 5);
            List<String> activityJson = new ArrayList<>();
            for (String activity : activityItems) {
                activityJson.add("\"" + JsonUtil.escape(activity) + "\"");
            }

            String createdAt = user.getCreatedAt() == null ? "" : DATE_TIME_FORMATTER.format(user.getCreatedAt());
            String lastLoginAt = user.getLastLoginAt() == null ? "" : DATE_TIME_FORMATTER.format(user.getLastLoginAt());

            values.add("{" +
                    "\"username\":\"" + JsonUtil.escape(user.getUsername()) + "\"," +
                    "\"fullName\":\"" + JsonUtil.escape(user.getFullName()) + "\"," +
                    "\"role\":\"" + user.getRole().name() + "\"," +
                    "\"active\":" + user.isActive() + "," +
                    "\"createdAt\":\"" + JsonUtil.escape(createdAt) + "\"," +
                    "\"lastLoginAt\":\"" + JsonUtil.escape(lastLoginAt) + "\"," +
                    "\"activeBorrowCount\":" + activeBorrowCount + "," +
                    "\"totalBorrowCount\":" + totalBorrowCount + "," +
                    "\"isCurrentLibrarian\":" + user.getUsername().equals(actingLibrarianUsername) + "," +
                    "\"recentActivity\":[" + String.join(",", activityJson) + "]" +
                    "}");
        }
        return "[" + String.join(",", values) + "]";
    }

    private List<String> getRecentActivitiesForUser(String username, int limit) {
        List<String> existing = userActivityLogs.getOrDefault(username, List.of());
        if (existing.isEmpty()) {
            return List.of();
        }

        int size = existing.size();
        int start = Math.max(0, size - Math.max(1, limit));
        List<String> recent = new ArrayList<>(existing.subList(start, size));
        java.util.Collections.reverse(recent);
        return recent;
    }

    private void appendUserActivity(String username, String message) {
        String normalizedUsername = nullToEmpty(username).trim();
        String normalizedMessage = nullToEmpty(message).trim();
        if (normalizedUsername.isEmpty() || normalizedMessage.isEmpty()) {
            return;
        }

        String entry = DATE_TIME_FORMATTER.format(LocalDateTime.now()) + " - " + normalizedMessage;
        userActivityLogs.compute(normalizedUsername, (key, existing) -> {
            List<String> values = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            values.add(entry);
            int maxEntries = 50;
            if (values.size() > maxEntries) {
                values = new ArrayList<>(values.subList(values.size() - maxEntries, values.size()));
            }
            return values;
        });
    }

    private void invalidateSessionsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        List<String> sessionIdsToRemove = new ArrayList<>();
        for (Map.Entry<String, User> entry : sessions.entrySet()) {
            if (username.equals(entry.getValue().getUsername())) {
                sessionIdsToRemove.add(entry.getKey());
            }
        }

        for (String sessionId : sessionIdsToRemove) {
            sessionSnapshotService.clearSnapshotForSession(sessionId);
            sessions.remove(sessionId);
            sessionLastActiveAtMs.remove(sessionId);
        }
        if (!sessionIdsToRemove.isEmpty()) {
            refreshSessionSnapshot();
        }
    }

    private static String detectContentType(String fileNameLowerCase) {
        if (fileNameLowerCase.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (fileNameLowerCase.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (fileNameLowerCase.endsWith(".txt") || fileNameLowerCase.endsWith(".md")) {
            return "text/plain; charset=utf-8";
        }
        if (fileNameLowerCase.endsWith(".jpg") || fileNameLowerCase.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileNameLowerCase.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    private static MultipartData readMultipartForm(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("boundary=")) {
            throw new IllegalArgumentException("Missing multipart boundary.");
        }

        String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length()).trim();
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }

        String delimiter = "--" + boundary;
        byte[] rawBody = exchange.getRequestBody().readAllBytes();
        String payload = new String(rawBody, StandardCharsets.ISO_8859_1);

        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, UploadedFile> uploadedFiles = new LinkedHashMap<>();

        String[] sections = payload.split(java.util.regex.Pattern.quote(delimiter));
        for (String section : sections) {
            String part = section;
            if (part.isBlank() || part.equals("--") || part.equals("--\r\n")) {
                continue;
            }
            if (part.startsWith("\r\n")) {
                part = part.substring(2);
            }
            if (part.endsWith("--")) {
                part = part.substring(0, part.length() - 2);
            }
            if (part.endsWith("\r\n")) {
                part = part.substring(0, part.length() - 2);
            }

            int splitIdx = part.indexOf("\r\n\r\n");
            if (splitIdx <= 0) {
                continue;
            }

            String headers = part.substring(0, splitIdx);
            String body = part.substring(splitIdx + 4);

            String dispositionLine = null;
            for (String headerLine : headers.split("\r\n")) {
                if (headerLine.toLowerCase().startsWith("content-disposition")) {
                    dispositionLine = headerLine;
                    break;
                }
            }
            if (dispositionLine == null) {
                continue;
            }

            String name = extractDispositionToken(dispositionLine, "name");
            String fileName = extractDispositionToken(dispositionLine, "filename");
            if (name == null || name.isBlank()) {
                continue;
            }

            if (fileName != null && !fileName.isBlank()) {
                byte[] bytes = body.getBytes(StandardCharsets.ISO_8859_1);
                Path tempFile = saveUploadedTempFile(fileName, bytes);
                uploadedFiles.put(name, new UploadedFile(fileName, tempFile));
            } else {
                fields.put(name, new String(body.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8).trim());
            }
        }

        return new MultipartData(fields, uploadedFiles);
    }

    private static String extractDispositionToken(String disposition, String tokenName) {
        String pattern = tokenName + "=\"";
        int start = disposition.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        int valueStart = start + pattern.length();
        int valueEnd = disposition.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return null;
        }
        return disposition.substring(valueStart, valueEnd);
    }

    private static Path saveUploadedTempFile(String fileName, byte[] bytes) throws IOException {
        String cleanName = Path.of(fileName).getFileName().toString();
        String suffix = ".tmp";
        int dot = cleanName.lastIndexOf('.');
        if (dot >= 0 && dot < cleanName.length() - 1) {
            suffix = cleanName.substring(dot);
        }

        Path temp = Files.createTempFile("library-upload-", suffix);
        Files.write(temp, bytes);
        temp.toFile().deleteOnExit();
        return temp;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // Slice 6: append to in-memory book version ledger if value changed; cap last 50.
    private void recordBookVersionIfChanged(String bookId,
                                            String editorUsername,
                                            String fieldName,
                                            String oldValue,
                                            String newValue) {
        String oldV = nullToEmpty(oldValue);
        String newV = nullToEmpty(newValue);
        if (oldV.equals(newV)) {
            return;
        }
        String ts = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        BookVersion entry = new BookVersion(ts, editorUsername, fieldName, oldV, newV);
        bookVersions.compute(bookId, (k, list) -> {
            List<BookVersion> updated = list == null ? new ArrayList<>() : new ArrayList<>(list);
            updated.add(entry);
            if (updated.size() > MAX_VERSION_HISTORY) {
                updated = new ArrayList<>(updated.subList(updated.size() - MAX_VERSION_HISTORY, updated.size()));
            }
            return updated;
        });
    }

    private static boolean parseBooleanFlag(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.equals("true") || trimmed.equals("1") || trimmed.equals("on") || trimmed.equals("yes");
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuote) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record MultipartData(Map<String, String> fields, Map<String, UploadedFile> uploadedFiles) {
        UploadedFile uploadedFile() {
            if (uploadedFiles == null || uploadedFiles.isEmpty()) {
                return null;
            }
            return uploadedFiles.values().iterator().next();
        }

        UploadedFile uploadedFile(String fieldName) {
            if (uploadedFiles == null || fieldName == null || fieldName.isBlank()) {
                return null;
            }
            return uploadedFiles.get(fieldName);
        }
    }

    private record UploadedFile(String originalFileName, Path path) {
    }

    private static class ApiAuthException extends RuntimeException {
        ApiAuthException(String message) {
            super(message);
        }
    }
}
