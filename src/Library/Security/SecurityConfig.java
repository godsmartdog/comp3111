package Library.Security;

import java.util.List;
//defualt para for password length, number of book can be borrow by that account, borrow days, file size approved, and allowed format
public final class SecurityConfig {
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int MAX_BORROW_LIMIT = 5;
    public static final int MAX_BORROW_DAYS = 14;
    public static final int DEFAULT_BORROW_DAYS = MAX_BORROW_DAYS;
    public static final int DEFAULT_RETURN_REMINDER_DUE_SOON_DAYS = 3;
    // 2 hours — long enough for demo walkthroughs and for clock-aware
    // manual testing of due-date / auto-return / reader-expiry features.
    // Override via JVM property -Dlibrary.sessionIdleTimeoutMs.
    public static final long DEFAULT_SESSION_IDLE_TIMEOUT_MS = 2L * 60L * 60L * 1000L;
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_COVER_IMAGE_SIZE_BYTES = 2L * 1024 * 1024;
    public static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".txt", ".doc", ".docx", ".md", ".jpg", ".jpeg", ".png");
    public static final List<String> ALLOWED_COVER_IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png");
    // make this class private access, no one can SecurityConfig config = new SecurityConfig(); do this accidently,
    //just see this as avoid waste of memory
    private SecurityConfig() {
    }

    public static long sessionIdleTimeoutMs() {
        String raw = System.getProperty("library.sessionIdleTimeoutMs");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SESSION_IDLE_TIMEOUT_MS;
        }

        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0 ? parsed : DEFAULT_SESSION_IDLE_TIMEOUT_MS;
        } catch (NumberFormatException ignored) {
            return DEFAULT_SESSION_IDLE_TIMEOUT_MS;
        }
    }

    public static int returnReminderDueSoonDays() {
        String raw = System.getProperty("library.returnReminderDueSoonDays");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_RETURN_REMINDER_DUE_SOON_DAYS;
        }

        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed >= 0 ? parsed : DEFAULT_RETURN_REMINDER_DUE_SOON_DAYS;
        } catch (NumberFormatException ignored) {
            return DEFAULT_RETURN_REMINDER_DUE_SOON_DAYS;
        }
    }
}
