// Import the system library used for extension handling
package Library.Exception;

// We use inheritance to perform specialization
public class ValidationException extends RuntimeException {
    // First instance of function overloading to throw exception message, String argument defined elsewhere
    public ValidationException(String message) { super(message); } // conversion constructor
}

//It should be used as a general exception, just add in in catch, wont get wrong
