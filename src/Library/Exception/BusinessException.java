// Import the system library used for extension handling

package Library.Exception;

// We use inheritance to perform specialization
public class BusinessException extends RuntimeException {
// First instance of function overloading to throw exception message, String argument defined elsewhere
    public BusinessException(String message) {
        super(message);
    }
// Second instance of function overloading, now with Throwable argument for detailed error message with tracable cause
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
