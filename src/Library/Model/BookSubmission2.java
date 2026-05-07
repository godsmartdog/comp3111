//this file is for define The status of the submit of Book , function approve(String comment) and reject(String comment) will be used for "submission status"
//imported in BookSubmissionRepository2

package Library.Model;

import java.io.Serializable;
import java.time.LocalDate; // local system date for reference (just the day such as YYYYMMDD)
import java.util.ArrayList; // generic class (similar to C++ template class "array")
import java.util.List; // generic class (similar to C++ template class "list")
import java.util.UUID; // universial unique object identifier

// BookSubmission is a finished draft, but still before publishing a book
public class BookSubmission2 implements Serializable {
    private static final long serialVersionUID = 1L;

    // Member variables
    private final String id;
    private final String title;
    private final String authorUsername;
    private final String authorFullName;
    private final List<String> genres;
    private final String description;
    private final String fileName;
    private final String coverImagePath;
    private final LocalDate submittedDate;

    private SubmissionState status;
    private String librarianComment;
    private String rejectionReason;
    private LocalDate approvedDate;

    // Constructor - first function overloading with currrent system date
    public BookSubmission2(String title, String authorUsername, String authorFullName,
                           List<String> genres, String description, String fileName) {
        this(title, authorUsername, authorFullName, genres, description, fileName, "");
    }

    public BookSubmission2(String title, String authorUsername, String authorFullName,
                           List<String> genres, String description, String fileName, String coverImagePath) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.authorUsername = authorUsername;
        this.authorFullName = authorFullName;
        this.genres = new ArrayList<>(genres); // Liskov polymorphic substution principle - init RHS ArrayList<>() prevent null pointer exception
        this.description = description;
        this.fileName = fileName;
        this.coverImagePath = coverImagePath == null ? "" : coverImagePath;
        this.submittedDate = LocalDate.now();
        this.status = SubmissionState.PENDING;
        this.rejectionReason = "";
    }

    // Constructor - second function overloading with user defined and given system date
    public BookSubmission2(String id, String title, String authorUsername, String authorFullName,
                           List<String> genres, String description, String fileName, LocalDate submittedDate) {
        this(id, title, authorUsername, authorFullName, genres, description, fileName, "", submittedDate);
    }

    public BookSubmission2(String id, String title, String authorUsername, String authorFullName,
                           List<String> genres, String description, String fileName, String coverImagePath, LocalDate submittedDate) {
        this.id = id;
        this.title = title;
        this.authorUsername = authorUsername;
        this.authorFullName = authorFullName;
        this.genres = new ArrayList<>(genres); // Liskov polymorphic substution principle - init RHS ArrayList<>() prevent null pointer exception // Defensive duplication prevent extenral modification on the variable
        this.description = description;
        this.fileName = fileName;
        this.coverImagePath = coverImagePath == null ? "" : coverImagePath;
        this.submittedDate = submittedDate;
        this.status = SubmissionState.PENDING;// Initialization prevent null exception with use of PENDING
        this.rejectionReason = "";
    }
    
    // Accessor
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorFullName() { return authorFullName; }
    public List<String> getGenres() { return new ArrayList<>(genres); }
    public String getDescription() { return description; }
    public String getFileName() { return fileName; }
    public String getCoverImagePath() { return coverImagePath; }
    public LocalDate getSubmittedDate() { return submittedDate; }
    public SubmissionState getStatus() { return status; }
    public String getLibrarianComment() { return librarianComment; }
    public String getRejectionReason() { return rejectionReason; }
    public LocalDate getApprovedDate() { return approvedDate; }

    // Mutator - change status to APPROVED to list considered books - (still not published - only publish afterwards)
    public void approve(String comment) {
        this.status = SubmissionState.APPROVED;
        this.librarianComment = comment;
        this.rejectionReason = "";
        this.approvedDate = LocalDate.now();
    }

    // Mutator - change status to REJECTED - (did NOT pass consideration stage - still before publish stage)
    public void reject(String comment) {
        reject(comment, "");
    }

    public void reject(String comment, String rejectionReason) {
        this.status = SubmissionState.REJECTED;
        this.librarianComment = comment;
        this.rejectionReason = rejectionReason == null ? "" : rejectionReason;
        this.approvedDate = null;
    }
}
