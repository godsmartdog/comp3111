package Library.Js;

import Library.Exception.AuthenticationException;
import Library.Exception.BusinessException;
import Library.Exception.ValidationException;
import Library.Model.Book;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.Role;
import Library.Model.User;
import Library.Service.AuthService;
import Library.Service.AuthorDraftService;
import Library.Service.AuthorService2;
import Library.Service.BookService;
import Library.Service.BorrowService;
import Library.Service.FileService;
import Library.Service.LibrarianService3;
import Library.Service.RecommendationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// REST API server that exposes the library service layer over HTTP,
// replacing the JavaFX UI with a JavaScript-based web frontend.
public class LibraryApiServer {

    private final AuthService authService;
    private final BookService bookService;
    private final BorrowService borrowService;
    private final RecommendationService recommendationService;
    private final AuthorService2 authorService;
    private final AuthorDraftService authorDraftService;
    private final FileService fileService;
    private final LibrarianService3 librarianService;

    // In-memory session store: sessionToken -> User
    private final Map<String, User> sessions = new ConcurrentHashMap<>();

    public LibraryApiServer(AuthService authService,
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
    }

    // Start the HTTP server on the given port and register all API routes
    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Static web files
        server.createContext("/", this::handleStatic);

        // Auth endpoints
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/auth/register", this::handleRegister);
        server.createContext("/api/auth/logout", this::handleLogout);
        server.createContext("/api/auth/session", this::handleSession);

        // Book endpoints
        server.createContext("/api/books", this::handleBooks);
        server.createContext("/api/borrow", this::handleBorrow);
        server.createContext("/api/recommendations", this::handleRecommendations);
        server.createContext("/api/active-borrows", this::handleActiveBorrows);

        // Author endpoints
        server.createContext("/api/drafts", this::handleDrafts);
        server.createContext("/api/genres", this::handleGenres);
        server.createContext("/api/submissions/publish", this::handlePublish);
        server.createContext("/api/submissions/preview", this::handlePreview);

        // Librarian endpoints
        server.createContext("/api/submissions/pending", this::handlePendingSubmissions);
        server.createContext("/api/submissions/approve", this::handleApprove);
        server.createContext("/api/submissions/reject", this::handleReject);
        server.createContext("/api/submissions/bulk-approve", this::handleBulkApprove);
        server.createContext("/api/submissions/bulk-reject", this::handleBulkReject);

        server.start();
        return server;
    }

    // Serve static files from the web/ directory next to this class on the classpath
    private void handleStatic(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.equals("/") || requestPath.equals("/index.html")) {
            requestPath = "/index.html";
        }

        // Locate web resources relative to this source file's package
        String resourcePath = "/Library/Js/web" + requestPath;
        InputStream resource = getClass().getResourceAsStream(resourcePath);

        if (resource == null) {
            // Try to serve from the filesystem (development mode)
            Path fsPath = Path.of("src/Library/Js/web" + requestPath);
            if (Files.exists(fsPath)) {
                byte[] bytes = Files.readAllBytes(fsPath);
                String contentType = guessContentType(requestPath);
                sendResponse(exchange, 200, contentType, bytes);
                return;
            }
            send404(exchange);
            return;
        }

        byte[] bytes = resource.readAllBytes();
        String contentType = guessContentType(requestPath);
        sendResponse(exchange, 200, contentType, bytes);
    }

    private String guessContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        return "application/octet-stream";
    }

    // POST /api/auth/login  body: {"username":"...","password":"...","role":"STUDENT|AUTHOR|LIBRARIAN"}
    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        Map<String, String> body = parseJsonBody(exchange);
        try {
            String username = body.getOrDefault("username", "");
            String password = body.getOrDefault("password", "");
            String roleStr = body.getOrDefault("role", "STUDENT");
            Role role = Role.valueOf(roleStr.toUpperCase());

            User user = switch (role) {
                case STUDENT, STAFF -> authService.loginStudentOrStaff(username, password, role);
                case AUTHOR -> authorService.loginAuthor(username, password);
                case LIBRARIAN -> librarianService.loginLibrarian(username, password);
            };

            String token = UUID.randomUUID().toString();
            sessions.put(token, user);
            String json = userToJson(user, token);
            sendJson(exchange, 200, json);
        } catch (AuthenticationException | IllegalArgumentException e) {
            sendJson(exchange, 401, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson(e.getMessage()));
        }
    }

    // POST /api/auth/register  body: {"username":"...","fullName":"...","password":"...","role":"...","extraDetail":"..."}
    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        Map<String, String> body = parseJsonBody(exchange);
        try {
            String username = body.getOrDefault("username", "");
            String fullName = body.getOrDefault("fullName", "");
            String password = body.getOrDefault("password", "");
            String roleStr = body.getOrDefault("role", "STUDENT");
            String extraDetail = body.getOrDefault("extraDetail", "");
            Role role = Role.valueOf(roleStr.toUpperCase());

            User user = switch (role) {
                case STUDENT, STAFF -> authService.registerStudentOrStaff(username, fullName, password, role);
                case AUTHOR -> authorService.registerAuthor(username, fullName, password, extraDetail);
                case LIBRARIAN -> librarianService.registerLibrarian(username, fullName, password, extraDetail);
            };

            String token = UUID.randomUUID().toString();
            sessions.put(token, user);
            sendJson(exchange, 200, userToJson(user, token));
        } catch (ValidationException | BusinessException | IllegalArgumentException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson(e.getMessage()));
        }
    }

    // POST /api/auth/logout  header: X-Session-Token
    private void handleLogout(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        String token = exchange.getRequestHeaders().getFirst("X-Session-Token");
        if (token != null) sessions.remove(token);
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    // GET /api/auth/session  header: X-Session-Token
    private void handleSession(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        User user = getSessionUser(exchange);
        if (user == null) {
            sendJson(exchange, 401, errorJson("Not logged in"));
            return;
        }
        sendJson(exchange, 200, userToJson(user, exchange.getRequestHeaders().getFirst("X-Session-Token")));
    }

    // GET /api/books?keyword=...
    private void handleBooks(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        String query = exchange.getRequestURI().getQuery();
        String keyword = extractQueryParam(query, "keyword");
        List<Book> books = (keyword == null || keyword.isBlank())
                ? bookService.listApprovedBooksWithAvailability()
                : bookService.searchApprovedBooks(keyword);
        String json = "[" + books.stream().map(this::bookToJson).collect(Collectors.joining(",")) + "]";
        sendJson(exchange, 200, json);
    }

    // POST /api/borrow  header: X-Session-Token  body: {"bookId":"...","days":14}
    private void handleBorrow(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        User user = getSessionUser(exchange);
        if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
        Map<String, String> body = parseJsonBody(exchange);
        try {
            String bookId = body.getOrDefault("bookId", "");
            int days = Integer.parseInt(body.getOrDefault("days", "14"));
            BorrowRecord record = borrowService.borrowBook(user.getUsername(), bookId, days);
            sendJson(exchange, 200, borrowRecordToJson(record));
        } catch (BusinessException | ValidationException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson(e.getMessage()));
        }
    }

    // GET /api/recommendations
    private void handleRecommendations(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        List<Book> books = recommendationService.recommendTopPopular(5);
        String json = "[" + books.stream().map(this::bookToJson).collect(Collectors.joining(",")) + "]";
        sendJson(exchange, 200, json);
    }

    // GET /api/active-borrows  header: X-Session-Token
    private void handleActiveBorrows(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        User user = getSessionUser(exchange);
        if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
        List<BorrowRecord> records = borrowService.listActiveBorrowsByUser(user.getUsername());
        String json = "[" + records.stream().map(this::borrowRecordToJson).collect(Collectors.joining(",")) + "]";
        sendJson(exchange, 200, json);
    }

    // GET /api/drafts  header: X-Session-Token
    // POST /api/drafts/save  header: X-Session-Token  body: {...}
    // POST /api/drafts/clear?title=...  header: X-Session-Token
    private void handleDrafts(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/save")) {
            if (!requireMethod(exchange, "POST")) return;
            User user = getSessionUser(exchange);
            if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
            Map<String, String> body = parseJsonBody(exchange);
            try {
                String title = body.getOrDefault("title", "");
                List<String> genres = parseJsonStringArray(body.getOrDefault("genres", "[]"));
                String description = body.getOrDefault("description", "");
                String filePath = body.getOrDefault("filePath", "");
                BookDraft2 draft = authorDraftService.autoSave(user.getUsername(), title, genres, description, filePath);
                sendJson(exchange, 200, draftToJson(draft));
            } catch (Exception e) {
                sendJson(exchange, 500, errorJson(e.getMessage()));
            }
        } else if (path.endsWith("/clear")) {
            if (!requireMethod(exchange, "POST")) return;
            User user = getSessionUser(exchange);
            if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
            String query = exchange.getRequestURI().getQuery();
            String title = extractQueryParam(query, "title");
            authorDraftService.clearDraft(user.getUsername(), title == null ? "" : title);
            sendJson(exchange, 200, "{\"ok\":true}");
        } else {
            if (!requireMethod(exchange, "GET")) return;
            User user = getSessionUser(exchange);
            if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
            List<BookDraft2> drafts = authorDraftService.loadDrafts(user.getUsername());
            String json = "[" + drafts.stream().map(this::draftToJson).collect(Collectors.joining(",")) + "]";
            sendJson(exchange, 200, json);
        }
    }

    // GET /api/genres
    private void handleGenres(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        List<String> genres = authorService.getSupportedGenres();
        String json = "[" + genres.stream().map(g -> "\"" + escapeJson(g) + "\"").collect(Collectors.joining(",")) + "]";
        sendJson(exchange, 200, json);
    }

    // POST /api/submissions/publish  header: X-Session-Token  body: {...}
    private void handlePublish(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        User user = getSessionUser(exchange);
        if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
        Map<String, String> body = parseJsonBody(exchange);
        try {
            String title = body.getOrDefault("title", "");
            List<String> genres = parseJsonStringArray(body.getOrDefault("genres", "[]"));
            String description = body.getOrDefault("description", "");
            String filePath = body.getOrDefault("filePath", "");
            BookSubmission2 submission = authorService.publishBook(user.getUsername(), title, genres, description, filePath);
            sendJson(exchange, 200, submissionToJson(submission));
        } catch (ValidationException | BusinessException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson(e.getMessage()));
        }
    }

    // POST /api/submissions/preview  header: X-Session-Token  body: {"title":"...","genres":[...],"description":"..."}
    private void handlePreview(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        User user = getSessionUser(exchange);
        if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
        Map<String, String> body = parseJsonBody(exchange);
        try {
            String title = body.getOrDefault("title", "");
            List<String> genres = parseJsonStringArray(body.getOrDefault("genres", "[]"));
            String description = body.getOrDefault("description", "");
            String preview = authorService.previewBook(title, genres, description);
            sendJson(exchange, 200, "{\"preview\":" + jsonString(preview) + "}");
        } catch (Exception e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        }
    }

    // GET /api/submissions/pending  header: X-Session-Token
    private void handlePendingSubmissions(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        User user = getSessionUser(exchange);
        if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
        List<BookSubmission2> submissions = librarianService.getPendingSubmissions();
        String json = "[" + submissions.stream().map(this::submissionToJson).collect(Collectors.joining(",")) + "]";
        sendJson(exchange, 200, json);
    }

    // POST /api/submissions/approve  header: X-Session-Token  body: {"submissionId":"...","comment":"..."}
    private void handleApprove(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        User user = getSessionUser(exchange);
        if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
        Map<String, String> body = parseJsonBody(exchange);
        try {
            librarianService.approveSubmission(body.getOrDefault("submissionId", ""), body.getOrDefault("comment", ""));
            sendJson(exchange, 200, "{\"ok\":true}");
        } catch (Exception e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        }
    }

    // POST /api/submissions/reject  header: X-Session-Token  body: {"submissionId":"...","comment":"..."}
    private void handleReject(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        User user = getSessionUser(exchange);
        if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
        Map<String, String> body = parseJsonBody(exchange);
        try {
            librarianService.rejectSubmission(body.getOrDefault("submissionId", ""), body.getOrDefault("comment", ""));
            sendJson(exchange, 200, "{\"ok\":true}");
        } catch (Exception e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        }
    }

    // POST /api/submissions/bulk-approve  header: X-Session-Token  body: {"ids":[...],"comment":"..."}
    private void handleBulkApprove(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        User user = getSessionUser(exchange);
        if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
        Map<String, String> body = parseJsonBody(exchange);
        try {
            List<String> ids = parseJsonStringArray(body.getOrDefault("ids", "[]"));
            librarianService.bulkApprove(ids, body.getOrDefault("comment", ""));
            sendJson(exchange, 200, "{\"ok\":true}");
        } catch (Exception e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        }
    }

    // POST /api/submissions/bulk-reject  header: X-Session-Token  body: {"ids":[...],"comment":"..."}
    private void handleBulkReject(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        User user = getSessionUser(exchange);
        if (user == null) { sendJson(exchange, 401, errorJson("Not logged in")); return; }
        Map<String, String> body = parseJsonBody(exchange);
        try {
            List<String> ids = parseJsonStringArray(body.getOrDefault("ids", "[]"));
            librarianService.bulkReject(ids, body.getOrDefault("comment", ""));
            sendJson(exchange, 200, "{\"ok\":true}");
        } catch (Exception e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        }
    }

    // ---- Helpers ----

    private User getSessionUser(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-Session-Token");
        if (token == null) return null;
        return sessions.get(token);
    }

    private boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return false;
        }
        return true;
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendResponse(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void send404(HttpExchange exchange) throws IOException {
        byte[] body = "Not Found".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(404, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    // Minimal JSON body parser (handles flat string/number values and string arrays for genres/ids)
    private Map<String, String> parseJsonBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseSimpleJson(body);
    }

    // Parse flat JSON object {"key":"value",...} into a Map.
    // Arrays stored as their raw JSON string (e.g. genres, ids).
    static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null || json.isBlank()) return result;
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        int i = 0;
        while (i < json.length()) {
            // skip whitespace and commas
            while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) i++;
            if (i >= json.length()) break;

            // read key
            if (json.charAt(i) != '"') break;
            int keyStart = i + 1;
            int keyEnd = json.indexOf('"', keyStart);
            if (keyEnd < 0) break;
            String key = json.substring(keyStart, keyEnd);
            i = keyEnd + 1;

            // skip ':'
            while (i < json.length() && (json.charAt(i) == ':' || Character.isWhitespace(json.charAt(i)))) i++;
            if (i >= json.length()) break;

            String value;
            char c = json.charAt(i);
            if (c == '"') {
                // String value
                int vStart = i + 1;
                int vEnd = findStringEnd(json, vStart);
                value = unescapeJson(json.substring(vStart, vEnd));
                i = vEnd + 1;
            } else if (c == '[') {
                // Array value — capture raw JSON, tracking whether we are inside a string
                int depth = 0;
                int vStart = i;
                boolean inString = false;
                while (i < json.length()) {
                    char ch = json.charAt(i);
                    if (inString) {
                        if (ch == '\\') { i++; } // skip escaped char
                        else if (ch == '"') { inString = false; }
                    } else {
                        if (ch == '"') { inString = true; }
                        else if (ch == '[') { depth++; }
                        else if (ch == ']') {
                            depth--;
                            if (depth == 0) { i++; break; }
                        }
                    }
                    i++;
                }
                value = json.substring(vStart, i);
            } else {
                // Number or boolean
                int vStart = i;
                while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                value = json.substring(vStart, i).trim();
            }
            result.put(key, value);
        }
        return result;
    }

    private static int findStringEnd(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '\\') {
                i++; // skip escaped char; bounds are safe since the loop condition re-checks
                if (i >= json.length()) break;
                continue;
            }
            if (json.charAt(i) == '"') return i;
        }
        return json.length();
    }

    // Parse a simple JSON string array like ["a","b","c"]
    static List<String> parseJsonStringArray(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        return Arrays.stream(json.split(","))
                .map(s -> s.trim())
                .filter(s -> s.startsWith("\""))
                .map(s -> s.substring(1, s.endsWith("\"") ? s.length() - 1 : s.length()))
                .map(LibraryApiServer::unescapeJson)
                .collect(Collectors.toList());
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    private String extractQueryParam(String query, String name) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String errorJson(String message) {
        return "{\"error\":" + jsonString(message) + "}";
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ---- JSON serialisers ----

    private String userToJson(User user, String token) {
        return "{\"username\":" + jsonString(user.getUsername())
                + ",\"fullName\":" + jsonString(user.getFullName())
                + ",\"role\":" + jsonString(user.getRole().name())
                + ",\"token\":" + jsonString(token) + "}";
    }

    private String bookToJson(Book book) {
        return "{\"id\":" + jsonString(book.getId())
                + ",\"title\":" + jsonString(book.getTitle())
                + ",\"authorFullName\":" + jsonString(book.getAuthorFullName())
                + ",\"available\":" + book.isAvailable()
                + ",\"approved\":" + book.isApproved()
                + ",\"publishDate\":" + (book.getPublishDate() == null ? "null" : jsonString(book.getPublishDate().toString()))
                + ",\"summary\":" + jsonString(book.getSummary()) + "}";
    }

    private String borrowRecordToJson(BorrowRecord record) {
        return "{\"id\":" + jsonString(record.getId())
                + ",\"bookId\":" + jsonString(record.getBookId())
                + ",\"borrowDate\":" + jsonString(record.getBorrowDate().toString())
                + ",\"dueDate\":" + jsonString(record.getDueDate().toString())
                + ",\"returned\":" + record.isReturned() + "}";
    }

    private String draftToJson(BookDraft2 draft) {
        String genres = "[" + draft.getGenres().stream().map(this::jsonString).collect(Collectors.joining(",")) + "]";
        return "{\"authorUsername\":" + jsonString(draft.getAuthorUsername())
                + ",\"title\":" + jsonString(draft.getTitle())
                + ",\"genres\":" + genres
                + ",\"description\":" + jsonString(draft.getDescription())
                + ",\"filePath\":" + jsonString(draft.getFilePath())
                + ",\"lastSavedAt\":" + (draft.getLastSavedAt() == null ? "null" : jsonString(draft.getLastSavedAt().toString()))
                + "}";
    }

    private String submissionToJson(BookSubmission2 sub) {
        String genres = "[" + sub.getGenres().stream().map(this::jsonString).collect(Collectors.joining(",")) + "]";
        return "{\"id\":" + jsonString(sub.getId())
                + ",\"title\":" + jsonString(sub.getTitle())
                + ",\"authorUsername\":" + jsonString(sub.getAuthorUsername())
                + ",\"authorFullName\":" + jsonString(sub.getAuthorFullName())
                + ",\"genres\":" + genres
                + ",\"description\":" + jsonString(sub.getDescription())
                + ",\"fileName\":" + jsonString(sub.getFileName())
                + ",\"submittedDate\":" + jsonString(sub.getSubmittedDate().toString())
                + ",\"status\":" + jsonString(sub.getStatus().name())
                + ",\"librarianComment\":" + jsonString(sub.getLibrarianComment())
                + "}";
    }
}
