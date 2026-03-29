package Library.Model; // imported in BookRepository for interface

import java.time.LocalDate; // local system date for reference (just the date such as YYYYMMDD)
import java.util.UUID; // universial unique object identifier

// Class for book - already held by librarian - past book submission accepted
public class Book {

    // Member variables
    private final String id; // immutable 
    private final String title;  // immutable
    private final String authorFullName;  // immutable
    private LocalDate publishDate; // approved date by librarian
    private boolean approved;
    private boolean available;
    private String summary;
    private String filePath;
    private String contentType;

    // Constructor
    public Book(String title, String authorFullName, String summary) {
        this.id = UUID.randomUUID().toString(); // randomly generate a unique ID for future reference and tracking
        this.title = title;
        this.authorFullName = authorFullName;
        this.summary = summary;
        this.approved = false; // initially not permitted to publish before review
        this.available = false; // initially not available to borrow (not on the shelves before official approval)
        this.filePath = "";
        this.contentType = "";
    }

    // Accessor
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorFullName() { return authorFullName; }
    public LocalDate getPublishDate() { return publishDate; }
    public boolean isApproved() { return approved; }
    public boolean isAvailable() { return available; }
    public String getSummary() { return summary; }
    public String getFilePath() { return filePath; }
    public String getContentType() { return contentType; }

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

    public void setFileMetadata(String filePath, String contentType) {
        this.filePath = filePath == null ? "" : filePath;
        this.contentType = contentType == null ? "" : contentType;
    }
}
