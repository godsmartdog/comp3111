package Library.Exception; // Import the system library used for extension handling

// We use inheritance to perform specialization
public class AuthenticationException extends RuntimeException {

    // First instance of function overloading to throw exception message, String argument defined elsewhere
    public AuthenticationException(String message) { 
        super(message); // conversion constructor
    }

    // Second instance of function overloading, now with Throwable argument for detailed error message with tracable cause
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause); // conversion constructor
    }
}

//used in UI to handle error
