package Library.Exception;

public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}

//It should be used as a general exception, just add in in catch, wont get wrong
