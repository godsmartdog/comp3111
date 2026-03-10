package Library.Exception;

public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
//used in UI to handle error
