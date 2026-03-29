package Library.Test;

import Library.Exception.BusinessException;
import Library.Exception.AuthenticationException;
import Library.Exception.ValidationException;
import Library.Model.Book;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.NotificationItem;
import Library.Model.ReadingProgress;
import Library.Model.Role;
import Library.Model.User;
import Library.Model.AuthorProfile2;
import Library.Repository.MemoryNotificationRepository;
import Library.Repository.MemoryAuthorProfileRepository2;
import Library.Repository.MemoryBookDraftRepository2;
import Library.Repository.MemoryBookRepository;
import Library.Repository.MemoryBookSubmissionRepository2;
import Library.Repository.MemoryBorrowRepository;
import Library.Repository.MemoryLibrarianProfileRepository3;
import Library.Repository.MemoryReadingProgressRepository;
import Library.Repository.MemoryUserRepository;
import Library.Security.SessionManager;
import Library.Service.AuthService;
import Library.Service.AuthorDraftService;
import Library.Service.AuthorService2;
import Library.Service.BookService;
import Library.Service.BorrowService;
import Library.Service.FileService;
import Library.Service.LibrarianService3;
import Library.Service.NotificationService;
import Library.Service.ReadingProgressService;
import Library.Service.RecommendationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public final class LibraryIntegrationTest {
    public static void main(String[] args) throws Exception {
        TestRunner runner = new TestRunner();
        runner.run("student/staff borrow and return flow", LibraryIntegrationTest::testStudentBorrowAndReturnFlow);
        runner.run("borrow duration cannot exceed fourteen days", LibraryIntegrationTest::testBorrowDurationLimit);
        runner.run("borrow limit and recommendation ranking", LibraryIntegrationTest::testBorrowLimitAndRecommendations);
        runner.run("author draft publish and librarian approval", LibraryIntegrationTest::testAuthorDraftPublishAndApproval);
        runner.run("author publish accepts normalized genres", LibraryIntegrationTest::testAuthorPublishAcceptsNormalizedGenres);
        runner.run("librarian reject and bulk approve flow", LibraryIntegrationTest::testRejectAndBulkApproveFlow);
        runner.run("file preview reads uploaded text", LibraryIntegrationTest::testFilePreview);
        runner.run("auto return overdue borrows", LibraryIntegrationTest::testAutoReturnOverdueBorrows);
        runner.run("reading progress persistence", LibraryIntegrationTest::testReadingProgressPersistence);
        runner.run("non-borrowed book progress access is denied", LibraryIntegrationTest::testProgressAccessRequiresActiveBorrow);
        runner.run("approved book keeps file metadata", LibraryIntegrationTest::testApprovedBookRetainsFileMetadata);
        runner.run("personal notifications can be listed and marked read", LibraryIntegrationTest::testNotificationListAndMarkRead);
        runner.run("cannot mark another user's notification", LibraryIntegrationTest::testNotificationOwnershipValidation);
        runner.run("author can view own published books", LibraryIntegrationTest::testAuthorPublishedBooksOwnOnly);
        runner.run("author published books enforce ownership boundary", LibraryIntegrationTest::testAuthorPublishedBooksOwnershipBoundary);
        runner.run("author profile update success", LibraryIntegrationTest::testAuthorProfileUpdateSuccess);
        runner.run("author profile update validation", LibraryIntegrationTest::testAuthorProfileUpdateValidation);
        runner.run("author profile ownership boundary", LibraryIntegrationTest::testAuthorProfileOwnershipBoundary);
        runner.finish();
    }

    private static void testStudentBorrowAndReturnFlow() {
        TestContext context = new TestContext();
        Book cleanCode = context.addApprovedBook("Clean Code", "Robert Martin", "A handbook of agile software craftsmanship.");

        context.authService.registerStudentOrStaff("alice", "Alice Chan", "Password1!", Role.STUDENT);
        context.authService.loginStudentOrStaff("alice", "Password1!", Role.STUDENT);

        assertEquals(1, context.bookService.listApprovedBooksWithAvailability().size(), "approved books should be listed");

        BorrowRecord record = context.borrowService.borrowBook("alice", cleanCode.getId(), 7);
        assertEquals(cleanCode.getId(), record.getBookId(), "borrowed record should point to the selected book");
        assertFalse(cleanCode.isAvailable(), "borrowed book should become unavailable");
        assertEquals(1, context.borrowService.listActiveBorrowsByUser("alice").size(), "active borrow should be tracked");

        context.borrowService.returnBook("alice", cleanCode.getId());
        assertTrue(record.isReturned(), "returning should mark the record as returned");
        assertTrue(cleanCode.isAvailable(), "returned book should become available again");
    }

    private static void testBorrowLimitAndRecommendations() {
        TestContext context = new TestContext();
        Book bookA = context.addApprovedBook("Algorithms", "Ada Lovelace", "Algorithms overview.");
        Book bookB = context.addApprovedBook("Distributed Systems", "Leslie Lamport", "Distributed systems guide.");
        Book bookC = context.addApprovedBook("Networking", "Radia Perlman", "Networking concepts.");
        Book bookD = context.addApprovedBook("Databases", "Jim Gray", "Database internals.");
        Book bookE = context.addApprovedBook("Compilers", "Grace Hopper", "Compiler construction.");
        Book bookF = context.addApprovedBook("Operating Systems", "Andrew Tanenbaum", "Operating systems design.");

        context.authService.registerStudentOrStaff("bob", "Bob Lee", "Password1!", Role.STUDENT);
        context.authService.loginStudentOrStaff("bob", "Password1!", Role.STUDENT);

        context.borrowService.borrowBook("bob", bookA.getId(), 14);
        context.borrowService.borrowBook("bob", bookB.getId(), 14);
        context.borrowService.borrowBook("bob", bookC.getId(), 14);
        context.borrowService.borrowBook("bob", bookD.getId(), 14);
        context.borrowService.borrowBook("bob", bookE.getId(), 14);

        expectThrows(BusinessException.class,
                () -> context.borrowService.borrowBook("bob", bookF.getId(), 14),
                "Borrow limit reached");

        context.borrowRepository.save(new BorrowRecord("user-1", bookA.getId(), LocalDate.now(), LocalDate.now().plusDays(7)));
        context.borrowRepository.save(new BorrowRecord("user-2", bookA.getId(), LocalDate.now(), LocalDate.now().plusDays(7)));
        context.borrowRepository.save(new BorrowRecord("user-3", bookB.getId(), LocalDate.now(), LocalDate.now().plusDays(7)));

        List<Book> recommendations = context.recommendationService.recommendTopPopular(3);
        assertEquals(3, recommendations.size(), "top 3 recommendations should be returned");
        assertEquals(bookA.getId(), recommendations.get(0).getId(), "most frequently borrowed book should rank first");
        assertEquals(bookB.getId(), recommendations.get(1).getId(), "second most frequently borrowed book should rank second");
    }

    private static void testBorrowDurationLimit() {
        TestContext context = new TestContext();
        Book cleanArchitecture = context.addApprovedBook("Clean Architecture", "Robert Martin", "Architecture guide.");

        context.authService.registerStudentOrStaff("duration-user", "Duration User", "Password1!", Role.STUDENT);

        expectThrows(BusinessException.class,
                () -> context.borrowService.borrowBook("duration-user", cleanArchitecture.getId(), 15),
                "Borrow duration must be between 1 and 14 days");
    }

    private static void testAuthorDraftPublishAndApproval() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("author-submit", ".txt", List.of("Chapter 1", "Chapter 2"));

        context.authorService.registerAuthor("author1", "Amy Writer", "Password1!", "Writes tech books.");
        context.authorService.loginAuthor("author1", "Password1!");

        BookDraft2 draft = context.authorDraftService.autoSave(
                "author1",
                "Pragmatic Java",
                List.of("Technology", "Education"),
                "A practical guide to Java development.",
                manuscript.toString()
        );
        assertEquals("Pragmatic Java", draft.getTitle(), "draft title should be saved");
        assertTrue(context.authorDraftService.loadDraft("author1", "Pragmatic Java").isPresent(), "draft should be loadable");

        context.fileService.validateSubmissionFile(manuscript.toString());
        BookSubmission2 submission = context.authorService.publishBook(
                "author1",
                "Pragmatic Java",
                List.of("Technology", "Education"),
                "A practical guide to Java development.",
                manuscript.toString()
        );
        context.authorDraftService.clearDraft("author1", "Pragmatic Java");
        assertTrue(context.authorDraftService.loadDraft("author1", "Pragmatic Java").isEmpty(), "draft should be cleared after submission");

        context.librarianService.registerLibrarian("lib1", "Lina Wong", "Password1!", "EMP-001");
        context.librarianService.loginLibrarian("lib1", "Password1!");
        assertEquals(1, context.librarianService.getPendingSubmissions().size(), "submission should be pending before review");

        context.librarianService.approveSubmission(submission.getId(), "Looks good.");
        assertEquals(0, context.librarianService.getPendingSubmissions().size(), "approved submission should leave pending queue");

        List<Book> approvedBooks = context.bookService.searchApprovedBooks("Pragmatic Java");
        assertEquals(1, approvedBooks.size(), "approved submission should create a published book");
        assertTrue(approvedBooks.get(0).isApproved(), "published book should be marked approved");

        Files.deleteIfExists(manuscript);
    }

    private static void testAuthorPublishAcceptsNormalizedGenres() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("author-normalized", ".txt", List.of("Normalized genre input"));

        context.authorService.registerAuthor("author-normalized", "Normalized Author", "Password1!", "Bio");

        BookSubmission2 submission = context.authorService.publishBook(
                " author-normalized ",
                "  Input Friendly Book  ",
                List.of(" technology ", "EDUCATION", "technology"),
                "  Accept mixed-case genres from UI.  ",
                manuscript.toString()
        );

        assertEquals("Input Friendly Book", submission.getTitle(), "title should be trimmed before saving submission");
        assertEquals(List.of("Technology", "Education"), submission.getGenres(), "genres should be normalized and deduplicated");
        assertEquals("Accept mixed-case genres from UI.", submission.getDescription(), "description should be trimmed");

        Files.deleteIfExists(manuscript);
    }

    private static void testRejectAndBulkApproveFlow() throws Exception {
        TestContext context = new TestContext();
        Path fileOne = createTempTextFile("submission-one", ".txt", List.of("One"));
        Path fileTwo = createTempTextFile("submission-two", ".txt", List.of("Two"));
        Path fileThree = createTempTextFile("submission-three", ".txt", List.of("Three"));

        context.authorService.registerAuthor("author2", "Ben Author", "Password1!", "Sci-fi writer.");
        BookSubmission2 rejected = context.authorService.publishBook("author2", "Rejected Title", List.of("Fiction"), "Rejected manuscript.", fileOne.toString());
        BookSubmission2 approvedOne = context.authorService.publishBook("author2", "Approved Title 1", List.of("Science"), "Approved manuscript 1.", fileTwo.toString());
        BookSubmission2 approvedTwo = context.authorService.publishBook("author2", "Approved Title 2", List.of("History"), "Approved manuscript 2.", fileThree.toString());

        context.librarianService.registerLibrarian("lib2", "Mark Librarian", "Password1!", "EMP-002");
        context.librarianService.loginLibrarian("lib2", "Password1!");
        context.librarianService.rejectSubmission(rejected.getId(), "Insufficient quality.");
        context.librarianService.bulkApprove(List.of(approvedOne.getId(), approvedTwo.getId()), "Approved in batch.");

        assertEquals(0, context.librarianService.getPendingSubmissions().size(), "all processed submissions should leave pending queue");
        assertEquals(2, context.bookService.listApprovedBooksWithAvailability().size(), "bulk approved submissions should create books");

        Files.deleteIfExists(fileOne);
        Files.deleteIfExists(fileTwo);
        Files.deleteIfExists(fileThree);
    }

    private static void testFilePreview() throws Exception {
        TestContext context = new TestContext();
        Path previewFile = createTempTextFile("preview", ".md", List.of("# Demo", "This is a preview test."));

        String preview = context.fileService.getPreviewDetails(previewFile.toString());
        assertTrue(preview.contains("File size:"), "preview should include file size");
        assertTrue(preview.contains("This is a preview test."), "preview should include file content for text formats");

        expectThrows(ValidationException.class,
                () -> context.fileService.validateSubmissionFile(previewFile.resolveSibling("missing.txt").toString()),
                "does not exist");

        Files.deleteIfExists(previewFile);
    }

    private static void testAutoReturnOverdueBorrows() {
        TestContext context = new TestContext();
        Book book = context.addApprovedBook("Borrowed Yesterday", "Tester", "Overdue book sample.");
        book.setAvailable(false);

        BorrowRecord overdue = new BorrowRecord("overdue-user", book.getId(), LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        context.borrowRepository.save(overdue);

        List<BorrowRecord> active = context.borrowService.listActiveBorrowsByUser("overdue-user");
        assertEquals(0, active.size(), "overdue borrow should be auto-returned and hidden from active list");
        assertTrue(overdue.isReturned(), "overdue record should be marked returned");
        assertTrue(overdue.isAutoReturned(), "overdue record should be flagged as auto-returned");
        assertTrue(book.isAvailable(), "book should become available after auto-return");
    }

    private static void testReadingProgressPersistence() {
        TestContext context = new TestContext();
        context.readingProgressService.updateProgress("reader-1", "book-1", 7, List.of("line A", "line B"));

        ReadingProgress progress = context.readingProgressService.getProgress("reader-1", "book-1");
        assertEquals(7, progress.getBookmarkPage(), "bookmark should persist");
        assertEquals(List.of("line A", "line B"), progress.getHighlights(), "highlights should persist");
    }

    private static void testProgressAccessRequiresActiveBorrow() {
        TestContext context = new TestContext();
        Book book = context.addApprovedBook("Restricted Borrow", "Security Tester", "Only borrower should read progress.");

        context.authService.registerStudentOrStaff("non-borrower", "No Borrow", "Password1!", Role.STUDENT);
        expectThrows(BusinessException.class,
                () -> context.borrowService.requireActiveBorrow("non-borrower", book.getId()),
                "not currently borrowed");
    }

    private static void testApprovedBookRetainsFileMetadata() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("metadata", ".pdf", List.of("fake pdf bytes"));

        context.authorService.registerAuthor("author-meta", "Metadata Author", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
                "author-meta",
                "Metadata Book",
                List.of("Technology"),
                "Book with file metadata.",
                manuscript.toString()
        );

        context.librarianService.registerLibrarian("lib-meta", "Metadata Librarian", "Password1!", "EMP-META");
        context.librarianService.approveSubmission(submission.getId(), "Approved with metadata.");

        List<Book> approved = context.bookService.searchApprovedBooks("Metadata Book");
        assertEquals(1, approved.size(), "approved metadata book should be searchable");
        assertEquals(manuscript.toString(), approved.get(0).getFilePath(), "approved book should keep submission file reference");
        assertEquals("application/pdf", approved.get(0).getContentType(), "approved book should store detected content type");

        Files.deleteIfExists(manuscript);
    }

    private static void testNotificationListAndMarkRead() {
        TestContext context = new TestContext();

        context.notificationService.addNotification("notify-user", "Borrow Update", "You borrowed Clean Code.");
        context.notificationService.addNotification("notify-user", "Return Reminder", "Please return by due date.");

        List<NotificationItem> before = context.notificationService.listByUser("notify-user");
        assertEquals(2, before.size(), "user should see personal notifications");
        assertFalse(before.get(0).isRead(), "latest notification should start unread");

        context.notificationService.markAsRead("notify-user", before.get(0).getId());

        List<NotificationItem> after = context.notificationService.listByUser("notify-user");
        NotificationItem updated = after.stream()
                .filter(item -> item.getId().equals(before.get(0).getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected marked notification to exist"));
        assertTrue(updated.isRead(), "notification should be marked as read");
    }

    private static void testNotificationOwnershipValidation() {
        TestContext context = new TestContext();

        NotificationItem foreignItem = context.notificationService.addNotification(
                "owner-a",
                "Private Message",
                "This should not be editable by other users."
        );

        expectThrows(BusinessException.class,
                () -> context.notificationService.markAsRead("owner-b", foreignItem.getId()),
                "does not belong to this user");
    }

    private static void testAuthorPublishedBooksOwnOnly() throws Exception {
        TestContext context = new TestContext();
        Path ownOne = createTempTextFile("author-own-1", ".txt", List.of("one"));
        Path ownTwo = createTempTextFile("author-own-2", ".txt", List.of("two"));
        Path other = createTempTextFile("author-other", ".txt", List.of("other"));

        context.authorService.registerAuthor("author-own", "Own Author", "Password1!", "Bio");
        context.authorService.registerAuthor("author-other", "Other Author", "Password1!", "Bio");

        BookSubmission2 ownSubmission1 = context.authorService.publishBook("author-own", "Own Title 1", List.of("Technology"), "Desc1", ownOne.toString());
        BookSubmission2 ownSubmission2 = context.authorService.publishBook("author-own", "Own Title 2", List.of("Technology"), "Desc2", ownTwo.toString());
        BookSubmission2 otherSubmission = context.authorService.publishBook("author-other", "Other Title", List.of("Technology"), "Desc3", other.toString());

        context.librarianService.registerLibrarian("lib-own", "Lib Own", "Password1!", "EMP-OWN");
        context.librarianService.approveSubmission(ownSubmission1.getId(), "ok");
        context.librarianService.approveSubmission(ownSubmission2.getId(), "ok");
        context.librarianService.approveSubmission(otherSubmission.getId(), "ok");

        List<Book> ownPublished = context.bookService.listApprovedBooksByAuthorUsername("author-own");
        assertEquals(2, ownPublished.size(), "author should only see own approved books");
        assertTrue(ownPublished.stream().allMatch(book -> "author-own".equals(book.getAuthorUsername())), "all returned books should belong to author-own");
        assertTrue(ownPublished.stream().allMatch(Book::isApproved), "all returned books should be approved");
        assertTrue(ownPublished.stream().allMatch(book -> book.getPublishDate() != null), "publish date should be present for approved books");

        Files.deleteIfExists(ownOne);
        Files.deleteIfExists(ownTwo);
        Files.deleteIfExists(other);
    }

    private static void testAuthorPublishedBooksOwnershipBoundary() throws Exception {
        TestContext context = new TestContext();
        Path ownerFile = createTempTextFile("owner-file", ".txt", List.of("owner"));

        context.authorService.registerAuthor("owner-a", "Owner A", "Password1!", "Bio");
        context.authorService.registerAuthor("owner-b", "Owner B", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook("owner-a", "Owner A Book", List.of("Technology"), "Desc", ownerFile.toString());

        context.librarianService.registerLibrarian("lib-bound", "Lib Bound", "Password1!", "EMP-BOUND");
        context.librarianService.approveSubmission(submission.getId(), "ok");

        List<Book> ownerBView = context.bookService.listApprovedBooksByAuthorUsername("owner-b");
        assertEquals(0, ownerBView.size(), "owner-b must not see owner-a published books");

        Files.deleteIfExists(ownerFile);
    }

        private static void testAuthorProfileUpdateSuccess() {
        TestContext context = new TestContext();
        context.authorService.registerAuthor("author-profile", "Old Name", "Password1!", "Old bio");

        AuthorService2.AuthorProfileSnapshot before = context.authorService.getAuthorProfile("author-profile");
        assertEquals("Old Name", before.fullName(), "initial full name should match");
        assertEquals("Old bio", before.bio(), "initial bio should match");

        AuthorService2.AuthorProfileSnapshot updated = context.authorService.updateAuthorProfile(
            "author-profile",
            "author-profile",
            "New Name",
            "New bio",
            "NewPass1!"
        );
        assertEquals("New Name", updated.fullName(), "full name should update");
        assertEquals("New bio", updated.bio(), "bio should update");

        context.authorService.loginAuthor("author-profile", "NewPass1!");
        expectThrows(AuthenticationException.class,
            () -> context.authorService.loginAuthor("author-profile", "Password1!"),
            "Invalid username or password");
        }

        private static void testAuthorProfileUpdateValidation() {
        TestContext context = new TestContext();
        context.authorService.registerAuthor("author-validate", "Valid Name", "Password1!", "Valid bio");

        expectThrows(ValidationException.class,
            () -> context.authorService.updateAuthorProfile("author-validate", "author-validate", "", "Valid bio", ""),
            "Full Name cannot be empty");
        expectThrows(ValidationException.class,
            () -> context.authorService.updateAuthorProfile("author-validate", "author-validate", "Valid Name", "", ""),
            "Bio cannot be empty");
        expectThrows(ValidationException.class,
            () -> context.authorService.updateAuthorProfile("author-validate", "author-validate", "Valid Name", "Valid bio", "short"),
            "Password must be between 8 and 64 characters");
        }

        private static void testAuthorProfileOwnershipBoundary() {
        TestContext context = new TestContext();
        context.authorService.registerAuthor("author-a", "Author A", "Password1!", "Bio A");
        context.authorService.registerAuthor("author-b", "Author B", "Password1!", "Bio B");

        expectThrows(ValidationException.class,
            () -> context.authorService.updateAuthorProfile("author-a", "author-b", "Changed", "Changed bio", ""),
            "Cannot update another author's profile");

        AuthorProfile2 profileB = context.authorProfileRepository.findByUsername("author-b")
            .orElseThrow(() -> new AssertionError("expected author-b profile to exist"));
        assertEquals("Bio B", profileB.getBio(), "owner boundary should keep original profile unchanged");
        }

    private static Path createTempTextFile(String prefix, String suffix, List<String> lines) throws Exception {
        Path path = Files.createTempFile(prefix, suffix);
        Files.write(path, lines);
        path.toFile().deleteOnExit();
        return path;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
            throw new AssertionError(message + " | expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectThrows(Class<? extends Throwable> expectedType, ThrowingRunnable runnable, String messageFragment) {
        try {
            runnable.run();
        } catch (Exception | AssertionError throwable) {
            if (!expectedType.isInstance(throwable)) {
                throw new AssertionError("Expected exception " + expectedType.getSimpleName() + " but got " + throwable.getClass().getSimpleName(), throwable);
            }
            if (messageFragment != null && (throwable.getMessage() == null || !throwable.getMessage().contains(messageFragment))) {
                throw new AssertionError("Exception message did not contain expected fragment: " + messageFragment);
            }
            return;
        }
        throw new AssertionError("Expected exception " + expectedType.getSimpleName() + " but nothing was thrown.");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TestContext {
        private final MemoryBookRepository bookRepository = new MemoryBookRepository();
        private final MemoryBorrowRepository borrowRepository = new MemoryBorrowRepository();
        private final MemoryUserRepository userRepository = new MemoryUserRepository();
        private final MemoryAuthorProfileRepository2 authorProfileRepository = new MemoryAuthorProfileRepository2();
        private final MemoryBookSubmissionRepository2 submissionRepository = new MemoryBookSubmissionRepository2();
        private final MemoryBookDraftRepository2 draftRepository = new MemoryBookDraftRepository2();
        private final MemoryLibrarianProfileRepository3 librarianProfileRepository = new MemoryLibrarianProfileRepository3();

        private final AuthService authService = new AuthService(userRepository);
        private final BookService bookService = new BookService(bookRepository);
        private final BorrowService borrowService = new BorrowService(bookRepository, borrowRepository);
        private final RecommendationService recommendationService = new RecommendationService(bookRepository, borrowRepository);
        private final AuthorService2 authorService = new AuthorService2(userRepository, authorProfileRepository, submissionRepository);
        private final AuthorDraftService authorDraftService = new AuthorDraftService(draftRepository);
        private final FileService fileService = new FileService();
        private final LibrarianService3 librarianService = new LibrarianService3(userRepository, librarianProfileRepository, submissionRepository, bookRepository);
        private final ReadingProgressService readingProgressService = new ReadingProgressService(new MemoryReadingProgressRepository());
        private final NotificationService notificationService = new NotificationService(new MemoryNotificationRepository());

        private TestContext() {
            SessionManager.getInstance().destroySession();
        }

        private Book addApprovedBook(String title, String author, String summary) {
            Book book = new Book(title, author, summary);
            book.approve(LocalDate.now());
            bookRepository.save(book);
            return book;
        }
    }

    private static final class TestRunner {
        private int passed;
        private int failed;

        private void run(String name, ThrowingRunnable testCase) throws Exception {
            try {
                testCase.run();
                passed++;
                System.out.println("[PASS] " + name);
            } catch (Exception | AssertionError throwable) {
                failed++;
                System.out.println("[FAIL] " + name + " -> " + throwable.getMessage());
                throwable.printStackTrace(System.out);
            } finally {
                SessionManager.getInstance().destroySession();
            }
        }

        private void finish() {
            System.out.println();
            System.out.println("Passed: " + passed);
            System.out.println("Failed: " + failed);
            if (failed > 0) {
                throw new AssertionError("Integration test suite failed.");
            }
        }
    }
}