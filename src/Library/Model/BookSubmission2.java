//this file is for define The status of the submit of Book , function approve(String comment) and reject(String comment) will be used for "submission status"
//imported in BookSubmissionRepository2
package Library.Model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BookSubmission2 {

    private final String id;
    private final String title;
    private final String authorUsername;
    private final String authorFullName;
    private final List<String> genres;
    private final String description;
    private final String fileName;
    private final LocalDate submittedDate;

    private SubmissionState status;
    private String librarianComment;
    private LocalDate approvedDate;

    public BookSubmission2(String title, String authorUsername, String authorFullName,
                           List<String> genres, String description, String fileName) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.authorUsername = authorUsername;
        this.authorFullName = authorFullName;
        this.genres = new ArrayList<>(genres);
        this.description = description;
        this.fileName = fileName;
        this.submittedDate = LocalDate.now();
        this.status = SubmissionState.PENDING;
    }

    public BookSubmission2(String id, String title, String authorUsername, String authorFullName,
                           List<String> genres, String description, String fileName, LocalDate submittedDate) {
        this.id = id;
        this.title = title;
        this.authorUsername = authorUsername;
        this.authorFullName = authorFullName;
        this.genres = new ArrayList<>(genres); // 防御性拷贝，避免外部修改
        this.description = description;
        this.fileName = fileName;
        this.submittedDate = submittedDate;
        this.status = SubmissionState.PENDING; // 补上初始化，避免 null
    }
//get data
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorFullName() { return authorFullName; }
    public List<String> getGenres() { return new ArrayList<>(genres); }
    public String getDescription() { return description; }
    public String getFileName() { return fileName; }
    public LocalDate getSubmittedDate() { return submittedDate; }
    public SubmissionState getStatus() { return status; }
    public String getLibrarianComment() { return librarianComment; }
    public LocalDate getApprovedDate() { return approvedDate; }

    public void approve(String comment) {// function to change status
        this.status = SubmissionState.APPROVED;
        this.librarianComment = comment;
        this.approvedDate = LocalDate.now();
    }

    public void reject(String comment) {// function to change status
        this.status = SubmissionState.REJECTED;
        this.librarianComment = comment;
    }
}
