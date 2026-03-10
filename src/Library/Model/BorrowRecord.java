package Library.Model;

import java.time.LocalDate;
import java.util.UUID;

public class BorrowRecord {
    private final String id;
    private final String username;
    private final String bookId;
    private final LocalDate borrowDate;
    private final LocalDate dueDate;
    private boolean returned;

    public BorrowRecord(String username, String bookId, LocalDate borrowDate, LocalDate dueDate) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.bookId = bookId;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.returned = false;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getBookId() { return bookId; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public boolean isReturned() { return returned; }

    public void markReturned() { this.returned = true; }
}