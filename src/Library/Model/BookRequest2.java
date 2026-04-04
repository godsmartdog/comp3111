package Library.Model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BookRequest2 {
    private final String id;
    private final String title;
    private final String requesterUsername;
    private final String requesterFullName;
    private final String authorName;
    private final List<String> genres;
    private final String reason;
    private final LocalDate requestedDate;

    private BookRequestStatus status;
    private String librarianComment;
    private String rejectionReason;
    private LocalDate approvedDate;
    private LocalDate uploadedDate;
    private String bookId;

    public BookRequest2(String title,
                        String requesterUsername,
                        String requesterFullName,
                        String authorName,
                        List<String> genres,
                        String reason) {
        this(UUID.randomUUID().toString(), title, requesterUsername, requesterFullName, authorName, genres, reason, LocalDate.now());
    }

    public BookRequest2(String id,
                        String title,
                        String requesterUsername,
                        String requesterFullName,
                        String authorName,
                        List<String> genres,
                        String reason,
                        LocalDate requestedDate) {
        this.id = id;
        this.title = title;
        this.requesterUsername = requesterUsername;
        this.requesterFullName = requesterFullName;
        this.authorName = authorName;
        this.genres = genres == null ? new ArrayList<>() : new ArrayList<>(genres);
        this.reason = reason;
        this.requestedDate = requestedDate;
        this.status = BookRequestStatus.PENDING;
        this.librarianComment = "";
        this.rejectionReason = "";
        this.bookId = "";
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getRequesterUsername() { return requesterUsername; }
    public String getRequesterFullName() { return requesterFullName; }
    public String getAuthorName() { return authorName; }
    public List<String> getGenres() { return new ArrayList<>(genres); }
    public String getReason() { return reason; }
    public LocalDate getRequestedDate() { return requestedDate; }
    public BookRequestStatus getStatus() { return status; }
    public String getLibrarianComment() { return librarianComment; }
    public String getRejectionReason() { return rejectionReason; }
    public LocalDate getApprovedDate() { return approvedDate; }
    public LocalDate getUploadedDate() { return uploadedDate; }
    public String getBookId() { return bookId; }

    public void approve(String comment) {
        this.status = BookRequestStatus.APPROVED;
        this.librarianComment = comment == null ? "" : comment;
        this.rejectionReason = "";
        this.approvedDate = LocalDate.now();
    }

    public void reject(String comment, String rejectionReason) {
        this.status = BookRequestStatus.REJECTED;
        this.librarianComment = comment == null ? "" : comment;
        this.rejectionReason = rejectionReason == null ? "" : rejectionReason;
        this.approvedDate = null;
        this.uploadedDate = null;
        this.bookId = "";
    }

    public void markUploaded(String comment, String bookId) {
        this.status = BookRequestStatus.UPLOADED;
        this.librarianComment = comment == null ? "" : comment;
        this.rejectionReason = "";
        if (this.approvedDate == null) {
            this.approvedDate = LocalDate.now();
        }
        this.uploadedDate = LocalDate.now();
        this.bookId = bookId == null ? "" : bookId;
    }
}