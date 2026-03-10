package Library.Model; // imported in BookRepository for interface

import java.time.LocalDate; // local system time for reference
import java.util.UUID; // universial unique object identifier

// Class for book
public class Book {

    // Member variables
    private final String id; // immutable 
    private String title;
    private String authorFullName;
    private LocalDate publishDate; // approved date by librarian
    private boolean approved;
    private boolean available;
    private String summary;

    // Constructor
    public Book(String title, String authorFullName, String summary) {
        this.id = UUID.randomUUID().toString(); // randomly generate a unique ID for future reference and tracking
        this.title = title;
        this.authorFullName = authorFullName;
        this.summary = summary;
        this.approved = false; //
        this.available = false;
    }

    // Accessor
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorFullName() { return authorFullName; }
    public LocalDate getPublishDate() { return publishDate; }
    public boolean isApproved() { return approved; }
    public boolean isAvailable() { return available; }
    public String getSummary() { return summary; }

    // Mutator - change boolean to true to list permitted/ legally published books
    public void approve(LocalDate publishDate) {
        this.approved = true;
        this.publishDate = publishDate;
        this.available = true;
    }

    // Mutator - indicate availability for someone to borrow
    public void setAvailable(boolean available) {
        this.available = available;
    }
}
