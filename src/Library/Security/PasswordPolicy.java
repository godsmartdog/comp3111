package Library.Security;

import Library.Exception.ValidationException;

public class PasswordPolicy {
    // Common standard: >= 8, includes upper/lower/digit/special, no spaces
    //just add more if for further more polcy require, rmb add new policy in comment above
    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new ValidationException("Password cannot be empty.");
        }
        if (password.length() < 8 || password.length() > 64) {
            throw new ValidationException("Password must be between 8 and 64 characters.");
        }
        if (password.contains(" ")) {
            throw new ValidationException("Password cannot contain spaces.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new ValidationException("Password must contain at least one uppercase letter.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new ValidationException("Password must contain at least one lowercase letter.");
        }
        if (!password.matches(".*\\d.*")) {
            throw new ValidationException("Password must contain at least one digit.");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new ValidationException("Password must contain at least one special character.");
        }
    }
}
