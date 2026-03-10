// Import the system library used for extension handling
package Library.Exception;

// We use inheritance to perform specialization
public class NotFoundException extends RuntimeException {

    // function overloading to throw exception message, String argument defined elsewhere
    public NotFoundException(String message) { super(message); } // conversion constructor
}
