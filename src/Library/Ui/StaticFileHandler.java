package Library.Ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class StaticFileHandler implements HttpHandler {
    private final Path root;
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=UTF-8");
        MIME_TYPES.put("css", "text/css; charset=UTF-8");
        MIME_TYPES.put("js", "application/javascript; charset=UTF-8");
        MIME_TYPES.put("json", "application/json; charset=UTF-8");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
    }

    public StaticFileHandler(Path root) {
        this.root = root;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        boolean isGet = "GET".equalsIgnoreCase(exchange.getRequestMethod());
        boolean isHead = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        if (!isGet && !isHead) {
            sendText(exchange, 405, "Method not allowed.");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath == null || requestPath.equals("/")) {
            requestPath = "/index.html";
        }

        Path target = root.resolve(requestPath.substring(1)).normalize();
        if (!target.startsWith(root) || !Files.exists(target) || Files.isDirectory(target)) {
            sendText(exchange, 404, "Not found.");
            return;
        }

        byte[] body = Files.readAllBytes(target);
        exchange.getResponseHeaders().set("Content-Type", guessMimeType(target));
        exchange.sendResponseHeaders(200, isHead ? -1 : body.length);
        if (!isHead) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private static String guessMimeType(Path path) {
        String name = path.getFileName().toString();
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "application/octet-stream";
        }
        String ext = name.substring(idx + 1).toLowerCase();
        return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    private static void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        String safe = text == null ? "" : text;
        byte[] body = safe.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
