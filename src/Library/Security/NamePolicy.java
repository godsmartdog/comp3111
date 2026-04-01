package Library.Security;

import Library.Exception.ValidationException;

import java.util.Set;

public final class NamePolicy {
    private static final Set<String> BANNED_TERMS = Set.of(
            "fuck", "shit", "bitch", "cunt", "dick", "pussy", "cock", "whore", "slut"
    );

    private NamePolicy() {
    }

    public static String validateUsername(String username) {
        String normalized = requireNonBlank(username, "Username cannot be empty.");
        validateNoBannedTerms(normalized, "Username contains inappropriate language.");
        return normalized;
    }

    public static String validateFullName(String fullName) {
        return requireNonBlank(fullName, "Full Name cannot be empty.");
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private static void validateNoBannedTerms(String value, String message) {
        String lowerValue = value.toLowerCase();
        for (String term : BANNED_TERMS) {
            if (lowerValue.contains(term)) {
                throw new ValidationException(message);
            }
        }
    }
}