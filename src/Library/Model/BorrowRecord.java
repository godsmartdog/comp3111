//imported in BorrowRepository
//dfine the class for Borrow Record
//markReturned() set true mean the book available now
package Library.Model;

import java.time.LocalDate; // local system date for reference (just the date such as YYYYMMDD)
import java.util.UUID; // universial unique object identifier

// BorrowRecord indicate flow of books between library and borrowers
public class BorrowRecord {

    // Member variables
    private final String id;
    private final String username;
    private final String bookId;
    private final LocalDate borrowDate;
    private final LocalDate dueDate;
    private boolean returned;
    private LocalDate returnedDate;
    private boolean autoReturned;

    // Constructor
    public BorrowRecord(String username, String bookId, LocalDate borrowDate, LocalDate dueDate) {
        this.id = UUID.randomUUID().toString(); // randomly generate a unique ID for future reference and tracking
        this.username = username;
        this.bookId = bookId;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.returned = false; // when library lend the book - book is held by borrower - library awaiting return
        this.returnedDate = null;
        this.autoReturned = false;
    }

    // Accessor
    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getBookId() { return bookId; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public boolean isReturned() { return returned; }
    public LocalDate getReturnedDate() { return returnedDate; }
    public boolean isAutoReturned() { return autoReturned; }
    public boolean isOverdue(LocalDate date) {
        return !returned && dueDate.isBefore(date);
    }

    // Mutator - library received the book - since borrower returned the book
    public void markReturned() {
        markReturned(LocalDate.now(), false);
    }

    public void markReturned(LocalDate date, boolean autoReturned) {
        this.returned = true;
        this.returnedDate = date;
        this.autoReturned = autoReturned;
    }
}
