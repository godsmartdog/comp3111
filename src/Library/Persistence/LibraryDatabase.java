package Library.Persistence;

import Library.Repository.MemoryAuthorProfileRepository2;
import Library.Repository.MemoryBookDraftRepository2;
import Library.Repository.MemoryBookRepository;
import Library.Repository.MemoryBookRequestRepository2;
import Library.Repository.MemoryBookReviewRepository;
import Library.Repository.MemoryBookSubmissionRepository2;
import Library.Repository.MemoryBorrowRepository;
import Library.Repository.MemoryLibrarianProfileRepository3;
import Library.Repository.MemoryNotificationRepository;
import Library.Repository.MemoryReadingProgressRepository;
import Library.Repository.MemorySessionSnapshotRepository;
import Library.Repository.MemoryUserRepository;
import java.io.Serializable;

public class LibraryDatabase implements Serializable {
    private static final long serialVersionUID = 1L;

    public MemoryUserRepository userRepository;
    public MemoryBookRepository bookRepository;
    public MemoryBorrowRepository borrowRepository;
    public MemoryBookReviewRepository bookReviewRepository;
    public MemoryBookRequestRepository2 bookRequestRepository;
    public MemoryAuthorProfileRepository2 authorProfileRepository;
    public MemoryBookSubmissionRepository2 submissionRepository;
    public MemoryBookDraftRepository2 draftRepository;
    public MemoryLibrarianProfileRepository3 librarianProfileRepository;
    public MemoryReadingProgressRepository readingProgressRepository;
    public MemoryNotificationRepository notificationRepository;
    public MemorySessionSnapshotRepository sessionSnapshotRepository;

    public LibraryDatabase() {
        this.userRepository = new MemoryUserRepository();
        this.bookRepository = new MemoryBookRepository();
        this.borrowRepository = new MemoryBorrowRepository();
        this.bookReviewRepository = new MemoryBookReviewRepository();
        this.bookRequestRepository = new MemoryBookRequestRepository2();
        this.authorProfileRepository = new MemoryAuthorProfileRepository2();
        this.submissionRepository = new MemoryBookSubmissionRepository2();
        this.draftRepository = new MemoryBookDraftRepository2();
        this.librarianProfileRepository = new MemoryLibrarianProfileRepository3();
        this.readingProgressRepository = new MemoryReadingProgressRepository();
        this.notificationRepository = new MemoryNotificationRepository();
        this.sessionSnapshotRepository = new MemorySessionSnapshotRepository();
    }
}