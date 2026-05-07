package Library.Model; // imported in BookRepository for interface

import java.io.Serializable;
import java.time.LocalDate; // local system date for reference (just the date such as YYYYMMDD)
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // universial unique object identifier

// Class for book - already held by librarian - past book submission accepted
public class Book implements Serializable {
    private static final long serialVersionUID = 1L;

    // Member variables
    private final String id; // immutable 
    private String title;
    private final String authorUsername;
    private final String authorFullName;  // immutable
    private List<String> genres;
    private LocalDate publishDate; // approved date by librarian
    private boolean approved;
    private int totalCopies;
    private int availableCopies;
    private String summary;
    private String filePath;
    private String contentType;
    private String coverImagePath;

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
        this.totalCopies = 1;
        this.availableCopies = 0;
        this.filePath = "";
        this.contentType = "";
        this.coverImagePath = "";
    }

    // Accessor
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorFullName() { return authorFullName; }
    public LocalDate getPublishDate() { return publishDate; }
    public boolean isApproved() { return approved; }
    public boolean isAvailable() { return approved && availableCopies > 0; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }
    public int getBorrowedCopies() { return totalCopies - availableCopies; }
    public List<String> getGenres() { return new ArrayList<>(genres); }
    public String getSummary() { return summary; }
    public String getFilePath() { return filePath; }
    public String getContentType() { return contentType; }
    public String getCoverImagePath() { return coverImagePath; }

    // Mutator - change boolean to true to list permitted/ legally published books
    public void approve(LocalDate publishDate) {
        this.approved = true;
        this.publishDate = publishDate;
        this.availableCopies = totalCopies;
    }

    // Mutator - indicate availability for someone to borrow
    public void setAvailable(boolean available) {
        this.availableCopies = available ? Math.max(1, totalCopies) : 0;
    }

    public void setTotalCopies(int totalCopies) {
        if (totalCopies <= 0) {
            throw new IllegalArgumentException("totalCopies must be greater than 0.");
        }

        int borrowed = getBorrowedCopies();
        if (totalCopies < borrowed) {
            throw new IllegalArgumentException("Cannot set copies below currently borrowed count: " + borrowed + ".");
        }

        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies - borrowed;
    }

    public void markBorrowedCopy() {
        if (!isAvailable()) {
            throw new IllegalStateException("No copies available.");
        }
        this.availableCopies -= 1;
    }

    public void markReturnedCopy() {
        if (availableCopies < totalCopies) {
            this.availableCopies += 1;
        }
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

    public void setCoverImagePath(String coverImagePath) {
        this.coverImagePath = coverImagePath == null ? "" : coverImagePath;
    }
}
