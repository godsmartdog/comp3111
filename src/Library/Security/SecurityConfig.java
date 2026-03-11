package Library.Security;

import java.util.List;
//defualt para for password length, number of book can be borrow by that account, borrow days, file size approved, and allowed format
public final class SecurityConfig {
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int MAX_BORROW_LIMIT = 5;
    public static final int DEFAULT_BORROW_DAYS = 14;
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".txt", ".doc", ".docx", ".md");
// make this class private access, no one can SecurityConfig config = new SecurityConfig(); do this accidently, just see this as avoid waste of memory
    private SecurityConfig() {
    }
}
