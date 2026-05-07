package Library.Model; // imported in BookRepository for interface

import java.io.Serializable;

// Stores the information of librarians who consider and permits books
public class LibrarianProfile3 implements Serializable {
    private static final long serialVersionUID = 1L;

    // Member variables
    private final String username;
    private final String employeeId;

    // Constructor
    public LibrarianProfile3 (String username, String employeeId) {
        this.username = username;
        this.employeeId = employeeId;
    }

    // Accessor
    public String getUsername() { return username; }
    public String getEmployeeId() { return employeeId; }
}

