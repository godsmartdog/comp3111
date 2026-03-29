package Library.Ui;

import Library.Model.Book;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.ReadingProgress;
import Library.Model.Role;
import Library.Model.User;
import Library.Repository.MemoryReadingProgressRepository;
import Library.Service.AuthService;
import Library.Service.AuthorDraftService;
import Library.Service.AuthorService2;
import Library.Service.BookService;
import Library.Service.BorrowService;
import Library.Service.FileService;
import Library.Service.LibrarianService3;
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

    private final AuthService authService;
    private final BookService bookService;
    private final BorrowService borrowService;
    private final RecommendationService recommendationService;
    private final AuthorService2 authorService;
    private final AuthorDraftService authorDraftService;
    private final FileService fileService;
    private final LibrarianService3 librarianService;
    private final ReadingProgressService readingProgressService;

    private final Map<String, User> sessions = new ConcurrentHashMap<>();

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
        this.readingProgressService = new ReadingProgressService(new MemoryReadingProgressRepository());
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
            }
            sendText(exchange, 200, "Logged out.");
        });

        server.createContext("/api/books", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.STUDENT, Role.STAFF);
                Map<String, String> query = readQuery(exchange.getRequestURI());
                String keyword = query.getOrDefault("keyword", "").trim();

                List<Book> books = keyword.isEmpty()
                        ? bookService.listApprovedBooksWithAvailability()
                        : bookService.searchApprovedBooks(keyword);

                sendJson(exchange, 200, booksToJson(books));
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
                int days = Integer.parseInt(form.getOrDefault("days", "14"));

                BorrowRecord record = borrowService.borrowBook(user.getUsername(), bookId, days);
                sendText(exchange, 200, "Borrowed successfully. Due date: " + record.getDueDate());
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
                int limit = Integer.parseInt(query.getOrDefault("limit", "5"));
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
                List<BorrowRecord> records = borrowService.listActiveBorrowsByUser(user.getUsername());

                List<String> jsonItems = new ArrayList<>();
                for (BorrowRecord record : records) {
                    String title = bookService.findBookById(record.getBookId())
                            .map(Book::getTitle)
                            .orElse(record.getBookId());
                    jsonItems.add("{" +
                            "\"recordId\":\"" + JsonUtil.escape(record.getId()) + "\"," +
                            "\"bookId\":\"" + JsonUtil.escape(record.getBookId()) + "\"," +
                            "\"bookTitle\":\"" + JsonUtil.escape(title) + "\"," +
                            "\"dueDate\":\"" + record.getDueDate() + "\"," +
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

                borrowService.listActiveBorrowsByUser(user.getUsername()).stream()
                        .filter(record -> record.getBookId().equals(bookId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Book is not currently borrowed by this user."));

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

                borrowService.listActiveBorrowsByUser(user.getUsername()).stream()
                        .filter(record -> record.getBookId().equals(bookId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Book is not currently borrowed by this user."));

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
                    ReadingProgress progress = readingProgressService.getProgress(user.getUsername(), bookId);
                    sendJson(exchange, 200, readingProgressToJson(progress));
                    return;
                }

                Map<String, String> form = readForm(exchange);
                String bookId = required(form, "bookId");
                int bookmark = Integer.parseInt(form.getOrDefault("bookmark", "1"));
                List<String> highlights = parseHighlights(form.getOrDefault("highlights", ""));
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
                List<String> genres = splitCsv(form.getOrDefault("genres", ""));
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

        server.createContext("/api/author/preview", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                requireRole(exchange, Role.AUTHOR);
                Map<String, String> form = readForm(exchange);
                String title = required(form, "title");
                List<String> genres = splitCsv(form.getOrDefault("genres", ""));
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
                List<String> genres = splitCsv(form.getOrDefault("genres", ""));
                String description = required(form, "description");
                String filePath = form.getOrDefault("filePath", "").trim();

                String submissionFileName;
                if (uploadedFile != null) {
                    fileService.validateSubmissionFile(uploadedFile.path().toString());
                    submissionFileName = uploadedFile.originalFileName();
                } else {
                    filePath = required(form, "filePath");
                    fileService.validateSubmissionFile(filePath);
                    submissionFileName = Path.of(filePath).getFileName().toString();
                }

                BookSubmission2 submission = authorService.publishBook(user.getUsername(), title, genres, description, submissionFileName);
                sendText(exchange, 200, "Submission created: " + submission.getId());
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
                List<BookSubmission2> items = librarianService.getPendingSubmissions();
                List<String> jsonItems = new ArrayList<>();
                for (BookSubmission2 submission : items) {
                    jsonItems.add("{" +
                            "\"id\":\"" + JsonUtil.escape(submission.getId()) + "\"," +
                            "\"title\":\"" + JsonUtil.escape(submission.getTitle()) + "\"," +
                            "\"authorFullName\":\"" + JsonUtil.escape(submission.getAuthorFullName()) + "\"," +
                            "\"fileName\":\"" + JsonUtil.escape(submission.getFileName()) + "\"," +
                            "\"submittedDate\":\"" + submission.getSubmittedDate() + "\"" +
                            "}");
                }
                sendJson(exchange, 200, "[" + String.join(",", jsonItems) + "]");
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

                if ("approve".equals(action)) {
                    librarianService.approveSubmission(submissionId, comment);
                    sendText(exchange, 200, "Submission approved.");
                } else if ("reject".equals(action)) {
                    librarianService.rejectSubmission(submissionId, comment);
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

        for (Role role : allowedRoles) {
            if (user.getRole() == role) {
                return user;
            }
        }

        throw new ApiAuthException("Permission denied for role " + user.getRole() + ".");
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

    private static String urlDecode(String input) {
        return URLDecoder.decode(input, StandardCharsets.UTF_8);
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        String[] parts = raw.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static List<String> parseHighlights(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (String line : raw.split("\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
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
