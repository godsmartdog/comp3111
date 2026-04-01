package Library.Model; // imported in BookRepository for interface

import java.time.LocalDate; // local system date for reference (just the date such as YYYYMMDD)
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // universial unique object identifier

// Class for book - already held by librarian - past book submission accepted
public class Book {

    // Member variables
    private final String id; // immutable 
    private String title;
    private final String authorUsername;
    private final String authorFullName;  // immutable
    private List<String> genres;
    private LocalDate publishDate; // approved date by librarian
    private boolean approved;
    private boolean available;
    private String summary;
    private String filePath;
    private String contentType;

    // Constructor
    public Book(String title, String authorFullName, String summary) {
        this(title, "", authorFullName, List.of(), summary);
    }

    public Book(String title, String authorUsername, String authorFullName, String summary) {
        this(title, authorUsername, authorFullName, List.of(), summary);
    }

    public Book(String title, String authorUsername, String authorFullName, List<String> genres, String summary) {
        this.id = UUID.randomUUID().toString(); // randomly generate a unique ID for future reference and tracking
        this.title = title;
        this.authorUsername = authorUsername == null ? "" : authorUsername;
        this.authorFullName = authorFullName;
        this.genres = genres == null ? new ArrayList<>() : new ArrayList<>(genres);
        this.summary = summary;
        this.approved = false; // initially not permitted to publish before review
        this.available = false; // initially not available to borrow (not on the shelves before official approval)
        this.filePath = "";
        this.contentType = "";
    }

    // Accessor
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorFullName() { return authorFullName; }
    public LocalDate getPublishDate() { return publishDate; }
    public boolean isApproved() { return approved; }
    public boolean isAvailable() { return available; }
    public List<String> getGenres() { return new ArrayList<>(genres); }
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

    public void updateMetadata(String title, List<String> genres, String summary) {
        this.title = title;
        this.genres = genres == null ? new ArrayList<>() : new ArrayList<>(genres);
        this.summary = summary;
    }

    public void setFileMetadata(String filePath, String contentType) {
        this.filePath = filePath == null ? "" : filePath;
        this.contentType = contentType == null ? "" : contentType;
    }
}
