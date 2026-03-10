//imported in BookRepository
package Library.Model;

import java.time.LocalDate;
import java.util.UUID;

public class Book {
    private final String id;
    private String title;
    private String authorFullName;
    private LocalDate publishDate; // approved date by librarian
    private boolean approved;
    private boolean available;
    private String summary;

    public Book(String title, String authorFullName, String summary) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.authorFullName = authorFullName;
        this.summary = summary;
        this.approved = false;
        this.available = false;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorFullName() { return authorFullName; }
    public LocalDate getPublishDate() { return publishDate; }
    public boolean isApproved() { return approved; }
    public boolean isAvailable() { return available; }
    public String getSummary() { return summary; }

    public void approve(LocalDate publishDate) {
        this.approved = true;
        this.publishDate = publishDate;
        this.available = true;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
