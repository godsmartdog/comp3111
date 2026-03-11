package Library.Security;

import java.util.List;

public final class SecurityConfig {
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int MAX_BORROW_LIMIT = 5;
    public static final int DEFAULT_BORROW_DAYS = 14;
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".txt", ".doc", ".docx", ".md");

    private SecurityConfig() {
    }
}
