package Library.Ui;

import Library.Model.Book;
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
import Library.Repository.MemoryReadingProgressRepository;
import Library.Repository.MemorySessionSnapshotRepository;
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
import Library.Service.SessionSnapshotService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LibraryApiHandlers {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String SESSION_HEADER = "X-Session-Id";
    private static final String CRASH_TEST_HEADER = "X-Crash-Test-Hook";
    private static final String[] BASIC_NOTIFICATION_CATEGORIES = {
            "submission",
            "account-update",
            "borrow-reminder",
            "book-deleted",
            "announcement"
    };
    // Local/dev internal testing token only.
    // TODO: Externalize this to environment/config before any production deployment.
    private static final String CRASH_TEST_TOKEN = "enable";
    private static final Path PROFILE_PHOTO_DIR = Paths.get(System.getProperty("user.dir"), "profile-photos");

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
    private final SessionSnapshotService sessionSnapshotService;

    private final Map<String, User> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionLastActiveAtMs = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userActivityLogs = new ConcurrentHashMap<>();
    private volatile SessionSnapshotSchema latestSessionSnapshot;

    public LibraryApiHandlers(AuthService authService,
                              BookService bookService,
                              BorrowService borrowService,
                              RecommendationService recommendationService,
                              AuthorService2 authorService,
                              AuthorDraftService authorDraftService,
                              FileService fileService,
                              LibrarianService3 librarianService) {
                    this(
                        authService,
                        bookService,
                        borrowService,
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
        this.recommendationService = recommendationService;
        this.authorService = authorService;
        this.authorDraftService = authorDraftService;
        this.fileService = fileService;
        this.librarianService = librarianService;
        this.notificationService = notificationService == null
            ? new NotificationService(new MemoryNotificationRepository())
            : notificationService;
        this.readingProgressService = readingProgressService == null
            ? new ReadingProgressService(new MemoryReadingProgressRepository())
            : readingProgressService;
        this.sessionSnapshotService = sessionSnapshotService == null
            ? new SessionSnapshotService(new MemorySessionSnapshotRepository())
            : sessionSnapshotService;
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
                        "\"coverImageUrl\":\"" + JsonUtil.escape(coverImageUrl) + "\"" +
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
                List<BorrowRecord> records = borrowService.listBorrowsByUser(user.getUsername());

                List<String> jsonItems = new ArrayList<>();
                for (BorrowRecord record : records) {
                    String title = bookService.findBookById(record.getBookId())
                            .map(Book::getTitle)
                            .orElse(record.getBookId());
                    String returnedDate = record.getReturnedDate() == null ? "" : record.getReturnedDate().toString();
                    jsonItems.add("{" +
                            "\"recordId\":\"" + JsonUtil.escape(record.getId()) + "\"," +
                            "\"bookId\":\"" + JsonUtil.escape(record.getBookId()) + "\"," +
                            "\"bookTitle\":\"" + JsonUtil.escape(title) + "\"," +
                            "\"borrowDate\":\"" + record.getBorrowDate() + "\"," +
                            "\"dueDate\":\"" + record.getDueDate() + "\"," +
                            "\"returned\":" + record.isReturned() + "," +
                            "\"returnedDate\":\"" + JsonUtil.escape(returnedDate) + "\"," +
                            "\"overdue\":" + record.isOverdue(java.time.LocalDate.now()) +
                            "}");
                }
                sendJson(exchange, 200, "[" + String.join(",", jsonItems) + "]");
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
                String description = required(form, "description");
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
                sendText(exchange, 200, "Submission created successfully.");
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
                List<Book> items = bookService.listApprovedBooksForLibrarian();
                List<String> jsonItems = new ArrayList<>();
                for (Book book : items) {
                    String publishDate = book.getPublishDate() == null ? "" : book.getPublishDate().toString();
                    String availability = book.isAvailable()
                        ? "Available (" + book.getAvailableCopies() + " copy/copies)"
                        : "Unavailable";
                    jsonItems.add("{" +
                            "\"id\":\"" + JsonUtil.escape(book.getId()) + "\"," +
                            "\"title\":\"" + JsonUtil.escape(book.getTitle()) + "\"," +
                            "\"author\":\"" + JsonUtil.escape(book.getAuthorFullName()) + "\"," +
                            "\"publishDate\":\"" + JsonUtil.escape(publishDate) + "\"," +
                            "\"status\":\"" + availability + "\"," +
                        "\"available\":" + book.isAvailable() + "," +
                        "\"totalCopies\":" + book.getTotalCopies() + "," +
                        "\"availableCopies\":" + book.getAvailableCopies() +
                            "}");
                }
                sendJson(exchange, 200, "[" + String.join(",", jsonItems) + "]");
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
            case "account-update" -> "Account Update";
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
                    "\"coverImagePath\":\"" + JsonUtil.escape(nullToEmpty(book.getCoverImagePath())) + "\"," +
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
