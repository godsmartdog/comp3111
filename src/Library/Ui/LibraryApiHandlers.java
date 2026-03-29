package Library.Ui;

import Library.Model.Book;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.NotificationItem;
import Library.Model.ReadingProgress;
import Library.Model.Role;
import Library.Model.User;
import Library.Repository.MemoryNotificationRepository;
import Library.Repository.MemoryReadingProgressRepository;
import Library.Security.SecurityConfig;
import Library.Service.AuthService;
import Library.Service.AuthorDraftService;
import Library.Service.AuthorService2;
import Library.Service.BookService;
import Library.Service.BorrowService;
import Library.Service.FileService;
import Library.Service.LibrarianService3;
import Library.Service.NotificationService;
import Library.Service.ReadingProgressService;
import Library.Service.RecommendationService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LibraryApiHandlers {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String SESSION_HEADER = "X-Session-Id";
    private static final String CRASH_TEST_HEADER = "X-Crash-Test-Hook";
    // Local/dev internal testing token only.
    // TODO: Externalize this to environment/config before any production deployment.
    private static final String CRASH_TEST_TOKEN = "enable";

    private final AuthService authService;
    private final BookService bookService;
    private final BorrowService borrowService;
    private final RecommendationService recommendationService;
    private final AuthorService2 authorService;
    private final AuthorDraftService authorDraftService;
    private final FileService fileService;
    private final LibrarianService3 librarianService;
    private final NotificationService notificationService;
    private final ReadingProgressService readingProgressService;

    private final Map<String, User> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionLastActiveAtMs = new ConcurrentHashMap<>();
    private volatile SessionSnapshotSchema latestSessionSnapshot;

    public LibraryApiHandlers(AuthService authService,
                              BookService bookService,
                              BorrowService borrowService,
                              RecommendationService recommendationService,
                              AuthorService2 authorService,
                              AuthorDraftService authorDraftService,
                              FileService fileService,
                              LibrarianService3 librarianService) {
        this.authService = authService;
        this.bookService = bookService;
        this.borrowService = borrowService;
        this.recommendationService = recommendationService;
        this.authorService = authorService;
        this.authorDraftService = authorDraftService;
        this.fileService = fileService;
        this.librarianService = librarianService;
        this.notificationService = new NotificationService(new MemoryNotificationRepository());
        this.readingProgressService = new ReadingProgressService(new MemoryReadingProgressRepository());
        this.latestSessionSnapshot = SessionSnapshotSchema.empty();
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
                sessions.remove(sessionId.trim());
                sessionLastActiveAtMs.remove(sessionId.trim());
                refreshSessionSnapshot();
            }
            sendText(exchange, 200, "Logged out.");
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

        server.createContext("/api/profile", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String payload = "{" +
                            "\"username\":\"" + JsonUtil.escape(user.getUsername()) + "\"," +
                            "\"fullName\":\"" + JsonUtil.escape(user.getFullName()) + "\"," +
                            "\"role\":\"" + user.getRole() + "\"" +
                            "}";
                    sendJson(exchange, 200, payload);
                    return;
                }

                Map<String, String> form = readForm(exchange);
                String fullName = required(form, "fullName");
                String newPassword = form.getOrDefault("password", "");
                String currentPassword = form.getOrDefault("currentPassword", "");
                User updated = authService.updateStudentOrStaffProfile(user.getUsername(), fullName, newPassword, currentPassword);

                String sessionId = nullToEmpty(exchange.getRequestHeaders().getFirst(SESSION_HEADER)).trim();
                if (!sessionId.isEmpty()) {
                    sessions.put(sessionId, updated);
                    sessionLastActiveAtMs.put(sessionId, Instant.now().toEpochMilli());
                    refreshSessionSnapshot();
                }

                notificationService.addNotification(
                        user.getUsername(),
                        "Profile Updated",
                        "Your profile details were updated successfully."
                );

                sendText(exchange, 200, "Profile updated successfully.");
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
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String scopeRaw = RequestFilters.getTrimmed(query, "scope", "active");
                NotificationService.NotificationScope scope = NotificationService.NotificationScope.fromString(scopeRaw);
                List<NotificationItem> items = notificationService.listByUser(user.getUsername(), scope);
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
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
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
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
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
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
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
                User user = requireRole(exchange, Role.STUDENT, Role.STAFF);
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
                notificationService.addNotification(
                        user.getUsername(),
                        "Book Borrowed",
                        "You borrowed this book. Due date: " + record.getDueDate()
                );
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
                notificationService.addNotification(
                        user.getUsername(),
                        "Books Borrowed",
                        "You borrowed " + records.size() + " books. Due date: " + sample.getDueDate()
                );

                sendText(exchange, 200, "Borrowed " + records.size() + " books successfully. Due date: " + sample.getDueDate());
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
                Map<String, String> query = readQuery(exchange.getRequestURI());
                int limit = RequestFilters.parseIntInRange(query, "limit", 5, 1, 50);
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
                notificationService.addNotification(
                        user.getUsername(),
                        "Book Returned",
                        "You returned a book. Due date was: " + record.getDueDate()
                );
                sendText(exchange, 200, "Returned successfully. Due date was: " + record.getDueDate());
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
                boolean hasPdf = !filePath.isEmpty() && filePath.toLowerCase().endsWith(".pdf") && Files.isRegularFile(Paths.get(filePath));
                if (hasPdf) {
                    sendJson(exchange, 200, "{" +
                            "\"type\":\"pdf\"," +
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
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
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
                        "Your published book metadata was updated: " + updated.getTitle()
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

                authorService.deleteOwnedPublishedBook(user.getUsername(), bookId);
                notificationService.addNotification(
                        user.getUsername(),
                        "Published Book Deleted",
                        "Your published book was removed from the catalog."
                );
                sendText(exchange, 200, "Published book deleted: " + bookId);
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
                            "\"bio\":\"" + JsonUtil.escape(profile.bio()) + "\"" +
                            "}";
                    sendJson(exchange, 200, payload);
                    return;
                }

                Map<String, String> form = readForm(exchange);
                String fullName = required(form, "fullName");
                String bio = required(form, "bio");
                String password = form.getOrDefault("password", "");

                authorService.updateAuthorProfile(user.getUsername(), user.getUsername(), fullName, bio, password);
                notificationService.addNotification(
                        user.getUsername(),
                        "Author Profile Updated",
                        "Your author profile has been updated successfully."
                );
                sendText(exchange, 200, "Author profile updated successfully.");
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

                if (contentType.startsWith("multipart/form-data")) {
                    MultipartData multipartData = readMultipartForm(exchange);
                    form = multipartData.fields();
                    uploadedFile = multipartData.uploadedFile();
                } else {
                    form = readForm(exchange);
                }

                String title = required(form, "title");
                List<String> genres = RequestFilters.parseCsv(form, "genres");
                String description = required(form, "description");
                String filePath = form.getOrDefault("filePath", "").trim();

                String submissionFileReference;
                if (uploadedFile != null) {
                    fileService.validateSubmissionFile(uploadedFile.path().toString());
                    submissionFileReference = uploadedFile.path().toString();
                } else {
                    filePath = required(form, "filePath");
                    fileService.validateSubmissionFile(filePath);
                    submissionFileReference = filePath;
                }

                BookSubmission2 submission = authorService.publishBook(user.getUsername(), title, genres, description, submissionFileReference);
                notificationService.addNotification(
                        user.getUsername(),
                        "Submission Created",
                        "Your book submission is now pending librarian review."
                );
                sendText(exchange, 200, "Submission created: " + submission.getId());
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
                        "Your pending submission was updated: " + updated.getTitle()
                );
                sendText(exchange, 200, "Submission updated: " + updated.getId());
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
                        "Your pending submission was deleted."
                );
                sendText(exchange, 200, "Submission deleted: " + submissionId);
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
                List<Book> items = bookService.listApprovedBooksForLibrarian();
                List<String> jsonItems = new ArrayList<>();
                for (Book book : items) {
                    String publishDate = book.getPublishDate() == null ? "" : book.getPublishDate().toString();
                    String availability = book.isAvailable() ? "Available" : "Unavailable";
                    jsonItems.add("{" +
                            "\"id\":\"" + JsonUtil.escape(book.getId()) + "\"," +
                            "\"title\":\"" + JsonUtil.escape(book.getTitle()) + "\"," +
                            "\"author\":\"" + JsonUtil.escape(book.getAuthorFullName()) + "\"," +
                            "\"publishDate\":\"" + JsonUtil.escape(publishDate) + "\"," +
                            "\"status\":\"" + availability + "\"," +
                            "\"available\":" + book.isAvailable() +
                            "}");
                }
                sendJson(exchange, 200, "[" + String.join(",", jsonItems) + "]");
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
                            "\"employeeId\":\"" + JsonUtil.escape(profile.employeeId()) + "\"" +
                            "}";
                    sendJson(exchange, 200, payload);
                    return;
                }

                Map<String, String> form = readForm(exchange);
                String fullName = required(form, "fullName");
                String employeeId = required(form, "employeeId");
                String password = form.getOrDefault("password", "");
                String currentPassword = form.getOrDefault("currentPassword", "");

                LibrarianService3.LibrarianProfileSnapshot updated = librarianService.updateLibrarianProfile(
                        user.getUsername(),
                        user.getUsername(),
                        fullName,
                        employeeId,
                    password,
                    currentPassword
                );

                notificationService.addNotification(
                    user.getUsername(),
                    "Librarian Profile Updated",
                    "Your librarian profile has been updated successfully."
                );

                String sessionId = nullToEmpty(exchange.getRequestHeaders().getFirst(SESSION_HEADER)).trim();
                if (!sessionId.isEmpty()) {
                    user.updateFullName(updated.fullName());
                    sessions.put(sessionId, user);
                    sessionLastActiveAtMs.put(sessionId, Instant.now().toEpochMilli());
                    refreshSessionSnapshot();
                }

                sendText(exchange, 200, "Librarian profile updated successfully.");
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
                String comment = form.getOrDefault("comment", "");
                String reason = form.getOrDefault("reason", "");

                if ("approve".equals(action)) {
                    librarianService.approveSubmission(submissionId, comment);
                    sendText(exchange, 200, "Submission approved.");
                } else if ("reject".equals(action)) {
                    BookSubmission2 rejected = librarianService.rejectSubmission(submissionId, comment, reason);
                    String notificationMessage = rejected.getRejectionReason().isBlank()
                            ? "Your submission \"" + rejected.getTitle() + "\" was rejected by a librarian."
                            : "Your submission \"" + rejected.getTitle() + "\" was rejected. Reason: " + rejected.getRejectionReason();
                    notificationService.addNotification(
                            rejected.getAuthorUsername(),
                            "Submission Rejected",
                            notificationMessage
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
            sessions.remove(sessionId);
            sessionLastActiveAtMs.remove(sessionId);
            refreshSessionSnapshot();
            throw new ApiAuthException("Session expired due to inactivity. Please login again.");
        }

        sessionLastActiveAtMs.put(sessionId, now);

        for (Role role : allowedRoles) {
            if (user.getRole() == role) {
                return user;
            }
        }

        throw new ApiAuthException("Permission denied for role " + user.getRole() + ".");
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

    private String booksToJson(List<Book> books) {
        List<String> items = new ArrayList<>();
        for (Book book : books) {
            items.add("{" +
                    "\"id\":\"" + JsonUtil.escape(book.getId()) + "\"," +
                    "\"title\":\"" + JsonUtil.escape(book.getTitle()) + "\"," +
                    "\"author\":\"" + JsonUtil.escape(book.getAuthorFullName()) + "\"," +
                    "\"summary\":\"" + JsonUtil.escape(nullToEmpty(book.getSummary())) + "\"," +
                    "\"status\":\"" + (book.isAvailable() ? "Available" : "Unavailable") + "\"," +
                    "\"available\":" + book.isAvailable() +
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

    private String borrowsToJson(List<BorrowRecord> records) {
        LocalDate today = LocalDate.now();
        List<String> jsonItems = new ArrayList<>();
        for (BorrowRecord record : records) {
            String title = bookService.findBookById(record.getBookId())
                    .map(Book::getTitle)
                    .orElse(record.getBookId());
            boolean overdue = record.isOverdue(today);
            String status = record.isReturned() ? "Returned" : "Borrowed";

            jsonItems.add("{" +
                    "\"recordId\":\"" + JsonUtil.escape(record.getId()) + "\"," +
                    "\"bookId\":\"" + JsonUtil.escape(record.getBookId()) + "\"," +
                    "\"bookTitle\":\"" + JsonUtil.escape(title) + "\"," +
                    "\"borrowDate\":\"" + record.getBorrowDate() + "\"," +
                    "\"dueDate\":\"" + record.getDueDate() + "\"," +
                    "\"returned\":" + record.isReturned() + "," +
                    "\"status\":\"" + status + "\"," +
                    "\"overdue\":" + overdue +
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
                "\"highlights\":[" + String.join(",", highlightJson) + "]" +
                "}";
    }

    private static String notificationsToJson(List<NotificationItem> items) {
        List<String> values = new ArrayList<>();
        for (NotificationItem item : items) {
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
                    "\"message\":\"" + JsonUtil.escape(item.getMessage()) + "\"," +
                    "\"priority\":\"" + item.getPriority() + "\"," +
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

    private static String authorPublishedBooksToJson(List<Book> books) {
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
                    "\"publishDate\":\"" + JsonUtil.escape(publishDate) + "\"," +
                    "\"status\":\"" + (book.isApproved() ? "Approved" : "Pending") + "\"" +
                    "}");
        }
        return "[" + String.join(",", values) + "]";
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
            values.add("{" +
                    "\"id\":\"" + JsonUtil.escape(submission.getId()) + "\"," +
                    "\"title\":\"" + JsonUtil.escape(submission.getTitle()) + "\"," +
                    "\"authorFullName\":\"" + JsonUtil.escape(submission.getAuthorFullName()) + "\"," +
                    "\"authorUsername\":\"" + JsonUtil.escape(submission.getAuthorUsername()) + "\"," +
                    "\"fileName\":\"" + JsonUtil.escape(submission.getFileName()) + "\"," +
                    "\"submittedDate\":\"" + submission.getSubmittedDate() + "\"," +
                    "\"status\":\"" + submission.getStatus() + "\"" +
                    "}");
        }
        return "[" + String.join(",", values) + "]";
    }

    private static String filePreviewToJson(AuthorService2.FilePreview preview) {
        return "{" +
                "\"itemId\":\"" + JsonUtil.escape(preview.itemId()) + "\"," +
                "\"sourceType\":\"" + JsonUtil.escape(preview.sourceType()) + "\"," +
                "\"filePath\":\"" + JsonUtil.escape(preview.filePath()) + "\"," +
                "\"sizeBytes\":" + preview.sizeBytes() + "," +
                "\"previewText\":\"" + JsonUtil.escape(preview.previewText()) + "\"" +
                "}";
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
        UploadedFile uploadedFile = null;

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
                uploadedFile = new UploadedFile(fileName, tempFile);
            } else {
                fields.put(name, new String(body.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8).trim());
            }
        }

        return new MultipartData(fields, uploadedFile);
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

    private record MultipartData(Map<String, String> fields, UploadedFile uploadedFile) {
    }

    private record UploadedFile(String originalFileName, Path path) {
    }

    private static class ApiAuthException extends RuntimeException {
        ApiAuthException(String message) {
            super(message);
        }
    }
}
