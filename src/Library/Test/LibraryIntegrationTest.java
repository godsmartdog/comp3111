package Library.Test;

import Library.Exception.BusinessException;
import Library.Exception.AuthenticationException;
import Library.Exception.ValidationException;
import Library.Model.Book;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.NotificationAction;
import Library.Model.NotificationItem;
import Library.Model.ReadingProgress;
import Library.Model.Role;
import Library.Model.SubmissionState;
import Library.Model.AuthorProfile2;
import Library.Model.LibrarianProfile3;
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
import Library.Ui.LibraryApiHandlers;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class LibraryIntegrationTest {
    public static void main(String[] args) throws Exception {
        TestRunner runner = new TestRunner();
        runner.run("student/staff borrow and return flow", LibraryIntegrationTest::testStudentBorrowAndReturnFlow);
        runner.run("borrow duration cannot exceed fourteen days", LibraryIntegrationTest::testBorrowDurationLimit);
        runner.run("bulk borrow endpoint supports multiple selection", LibraryIntegrationTest::testBulkBorrowEndpointSupportsMultipleSelection);
        runner.run("bulk borrow endpoint validates duration bounds", LibraryIntegrationTest::testBulkBorrowEndpointValidatesDurationBounds);
        runner.run("non student/staff cannot use bulk borrow endpoint", LibraryIntegrationTest::testNonStudentStaffCannotUseBulkBorrowEndpoint);
        runner.run("single borrow endpoint remains compatible", LibraryIntegrationTest::testSingleBorrowEndpointRemainsCompatible);
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
        runner.run("author notifications list and mark read", LibraryIntegrationTest::testAuthorNotificationListAndRead);
        runner.run("author notifications ownership boundary", LibraryIntegrationTest::testAuthorNotificationOwnershipBoundary);
        runner.run("author notifications summary unread count", LibraryIntegrationTest::testAuthorNotificationSummaryUnreadCount);
        runner.run("non-author forbidden from author notification APIs", LibraryIntegrationTest::testNonAuthorForbiddenFromAuthorNotificationApis);
        runner.run("librarian can view approved books endpoint", LibraryIntegrationTest::testLibrarianCanViewApprovedBooksEndpoint);
        runner.run("non-librarian cannot access approved books endpoint", LibraryIntegrationTest::testNonLibrarianCannotAccessApprovedBooksEndpoint);
        runner.run("librarian profile update success", LibraryIntegrationTest::testLibrarianProfileUpdateSuccess);
        runner.run("librarian profile validation failures", LibraryIntegrationTest::testLibrarianProfileValidationFailures);
        runner.run("librarian profile ownership boundary", LibraryIntegrationTest::testLibrarianProfileOwnershipBoundary);
        runner.run("librarian notifications list and mark read success", LibraryIntegrationTest::testLibrarianNotificationsListAndReadSuccess);
        runner.run("librarian notification ownership boundary", LibraryIntegrationTest::testLibrarianNotificationOwnershipBoundary);
        runner.run("non-librarian forbidden from librarian notification APIs", LibraryIntegrationTest::testNonLibrarianForbiddenFromLibrarianNotificationApis);
        runner.run("student/staff notifications list and mark read success", LibraryIntegrationTest::testStudentStaffNotificationApisListAndReadSuccess);
        runner.run("student/staff notification ownership boundary endpoint", LibraryIntegrationTest::testStudentStaffNotificationOwnershipBoundaryEndpoint);
        runner.run("author and librarian forbidden from student/staff notification APIs", LibraryIntegrationTest::testAuthorAndLibrarianForbiddenFromStudentStaffNotificationApis);
        runner.run("librarian can view borrowed-books records", LibraryIntegrationTest::testLibrarianCanViewBorrowedBooksRecords);
        runner.run("non-librarian cannot access borrowed-books records endpoint", LibraryIntegrationTest::testNonLibrarianCannotAccessBorrowedBooksRecordsEndpoint);
        runner.run("librarian reject with reason persists and notifies author", LibraryIntegrationTest::testLibrarianRejectWithReasonPersistsAndNotifiesAuthor);
        runner.run("non-librarian cannot reject submissions endpoint", LibraryIntegrationTest::testNonLibrarianCannotRejectSubmissionsEndpoint);
        runner.run("reject reason length validation", LibraryIntegrationTest::testRejectReasonLengthValidation);
        runner.run("notification foundation supports metadata and action", LibraryIntegrationTest::testNotificationMetadataAndActionFoundation);
        runner.run("books endpoint supports keyword and availability filters", LibraryIntegrationTest::testBooksEndpointSupportsKeywordAndAvailabilityFilters);
        runner.run("books endpoint rejects invalid availability filter", LibraryIntegrationTest::testBooksEndpointRejectsInvalidAvailabilityFilter);
        runner.run("shared filters reject invalid recommendation limits", LibraryIntegrationTest::testSharedFilterParsingForRecommendationLimit);
        runner.run("session crash hook supports snapshot and recovery", LibraryIntegrationTest::testSessionSnapshotCrashRecoveryHook);
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

    private static void testBulkBorrowEndpointSupportsMultipleSelection() throws Exception {
        TestContext context = new TestContext();
        Book first = context.addApprovedBook("Bulk Book One", "Bulk Author", "First bulk candidate.");
        Book second = context.addApprovedBook("Bulk Book Two", "Bulk Author", "Second bulk candidate.");
        context.authService.registerStudentOrStaff("bulk-user", "Bulk User", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "bulk-user", "Password1!", "STUDENT");

            HttpRequest bulkBorrowRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrow/bulk"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("bookIds=" + first.getId() + "," + second.getId() + "&days=7"))
                    .build();
            HttpResponse<String> bulkBorrowResponse = client.send(bulkBorrowRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, bulkBorrowResponse.statusCode(), "bulk borrow endpoint should return HTTP 200 for valid request");
            assertTrue(bulkBorrowResponse.body().contains("Borrowed 2 books successfully"), "bulk borrow response should include borrowed count");

            assertFalse(first.isAvailable(), "first borrowed book should become unavailable");
            assertFalse(second.isAvailable(), "second borrowed book should become unavailable");
            assertEquals(2, context.borrowService.listActiveBorrowsByUser("bulk-user").size(), "bulk borrow should create two active records");
        } finally {
            server.stop(0);
        }
    }

    private static void testBulkBorrowEndpointValidatesDurationBounds() throws Exception {
        TestContext context = new TestContext();
        Book book = context.addApprovedBook("Duration Guard Book", "Duration Guard", "Used for duration guard checks.");
        context.authService.registerStudentOrStaff("bulk-duration-user", "Bulk Duration User", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "bulk-duration-user", "Password1!", "STUDENT");

            HttpRequest zeroDaysRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrow/bulk"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("bookIds=" + book.getId() + "&days=0"))
                    .build();
            HttpResponse<String> zeroDaysResponse = client.send(zeroDaysRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, zeroDaysResponse.statusCode(), "days=0 should be rejected for bulk borrow");
            assertTrue(zeroDaysResponse.body().contains("days must be between 1 and 14"), "days=0 response should explain allowed range");

            HttpRequest tooManyDaysRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrow/bulk"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("bookIds=" + book.getId() + "&days=15"))
                    .build();
            HttpResponse<String> tooManyDaysResponse = client.send(tooManyDaysRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, tooManyDaysResponse.statusCode(), "days>14 should be rejected for bulk borrow");
            assertTrue(tooManyDaysResponse.body().contains("days must be between 1 and 14"), "days>14 response should explain allowed range");
        } finally {
            server.stop(0);
        }
    }

    private static void testNonStudentStaffCannotUseBulkBorrowEndpoint() throws Exception {
        TestContext context = new TestContext();
        Book book = context.addApprovedBook("Role Guard Book", "Role Guard", "Role boundary test book.");
        context.authorService.registerAuthor("author-bulk-block", "Author Bulk Block", "Password1!", "Bio");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "author-bulk-block", "Password1!", "AUTHOR");

            HttpRequest bulkBorrowRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrow/bulk"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("bookIds=" + book.getId() + "&days=7"))
                    .build();
            HttpResponse<String> bulkBorrowResponse = client.send(bulkBorrowRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, bulkBorrowResponse.statusCode(), "non student/staff should be forbidden from bulk borrow endpoint");
            assertTrue(bulkBorrowResponse.body().contains("Permission denied"), "forbidden response should explain permission denied");
        } finally {
            server.stop(0);
        }
    }

    private static void testSingleBorrowEndpointRemainsCompatible() throws Exception {
        TestContext context = new TestContext();
        Book book = context.addApprovedBook("Single Borrow Compatible", "Compatibility Author", "Compatibility borrow check.");
        context.authService.registerStudentOrStaff("single-borrow-user", "Single Borrow User", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "single-borrow-user", "Password1!", "STUDENT");

            HttpRequest borrowRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrow"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("bookId=" + book.getId() + "&days=5"))
                    .build();
            HttpResponse<String> borrowResponse = client.send(borrowRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, borrowResponse.statusCode(), "single borrow endpoint should remain functional");
            assertTrue(borrowResponse.body().contains("Borrowed successfully"), "single borrow response should keep existing success wording");

            HttpRequest borrowsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrows"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> borrowsResponse = client.send(borrowsRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, borrowsResponse.statusCode(), "borrows endpoint should still work after single borrow");
            assertTrue(borrowsResponse.body().contains("\"bookId\":\"" + book.getId() + "\""), "single-borrowed book should appear in active borrows");
        } finally {
            server.stop(0);
        }
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
        BookSubmission2 rejectedSaved = context.submissionRepository.findById(rejected.getId())
            .orElseThrow(() -> new AssertionError("rejected submission should be stored"));
        assertEquals(SubmissionState.REJECTED, rejectedSaved.getStatus(), "rejected submission should remain rejected");
        assertEquals("Insufficient quality.", rejectedSaved.getRejectionReason(), "rejection reason should persist on submission");

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

    private static void testAuthorNotificationListAndRead() {
        TestContext context = new TestContext();
        context.authorService.registerAuthor("author-notify", "Author Notify", "Password1!", "Bio");

        context.notificationService.addNotification("author-notify", "Submission Update", "Your submission is pending review.");
        context.notificationService.addNotification("author-notify", "Review Result", "Your submission was approved.");

        List<NotificationItem> items = context.notificationService.listByUser("author-notify");
        assertEquals(2, items.size(), "author should see personal notifications");
        assertTrue(items.stream().allMatch(item -> "author-notify".equals(item.getUsername())), "all notifications should belong to author-notify");

        NotificationItem target = items.get(0);
        assertFalse(target.isRead(), "notification should start unread");
        context.notificationService.markAsRead("author-notify", target.getId());

        NotificationItem updated = context.notificationService.listByUser("author-notify").stream()
                .filter(item -> item.getId().equals(target.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected notification to exist"));
        assertTrue(updated.isRead(), "author notification should be marked read");
    }

    private static void testAuthorNotificationOwnershipBoundary() {
        TestContext context = new TestContext();
        context.authorService.registerAuthor("author-owner-a", "Owner A", "Password1!", "Bio");
        context.authorService.registerAuthor("author-owner-b", "Owner B", "Password1!", "Bio");

        NotificationItem foreign = context.notificationService.addNotification(
                "author-owner-a",
                "Private Notification",
                "Only owner A can mark this as read."
        );

        expectThrows(BusinessException.class,
                () -> context.notificationService.markAsRead("author-owner-b", foreign.getId()),
                "does not belong to this user");
    }

            private static void testAuthorNotificationSummaryUnreadCount() throws Exception {
            TestContext context = new TestContext();
            context.authorService.registerAuthor("author-summary", "Author Summary", "Password1!", "Bio");

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "author-summary", "Password1!", "AUTHOR");

                HttpRequest profileUpdateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Author+Summary&bio=Updated+bio&password="))
                    .build();
                HttpResponse<String> profileUpdateResponse = client.send(profileUpdateRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, profileUpdateResponse.statusCode(), "author profile update should generate one unread notification");

                HttpRequest notificationsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/notifications"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> notificationsResponse = client.send(notificationsRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, notificationsResponse.statusCode(), "author notifications list should return HTTP 200");
                String notificationId = extractJsonField(notificationsResponse.body(), "id");

                HttpRequest summaryRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/notifications/summary"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> summaryBefore = client.send(summaryRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, summaryBefore.statusCode(), "author notifications summary should return HTTP 200");
                assertTrue(summaryBefore.body().contains("\"unreadCount\":1"), "summary should show one unread notification initially");

                HttpRequest markReadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/notifications/read"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + notificationId))
                    .build();
                HttpResponse<String> markReadResponse = client.send(markReadRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, markReadResponse.statusCode(), "author mark-read should return HTTP 200");

                HttpResponse<String> summaryAfter = client.send(summaryRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, summaryAfter.statusCode(), "author notifications summary after read should return HTTP 200");
                assertTrue(summaryAfter.body().contains("\"unreadCount\":0"), "summary unread count should decrease after mark-read");
            } finally {
                server.stop(0);
            }
            }

            private static void testNonAuthorForbiddenFromAuthorNotificationApis() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("student-author-notify-no", "Student No Author Notify", "Password1!", Role.STUDENT);

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "student-author-notify-no", "Password1!", "STUDENT");

                HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/notifications"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(401, listResponse.statusCode(), "non-author should be forbidden from author notifications list");
                assertTrue(listResponse.body().contains("Permission denied"), "list response should explain permission denied");

                HttpRequest summaryRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/notifications/summary"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> summaryResponse = client.send(summaryRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(401, summaryResponse.statusCode(), "non-author should be forbidden from author notifications summary");
                assertTrue(summaryResponse.body().contains("Permission denied"), "summary response should explain permission denied");

                HttpRequest readRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/notifications/read"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=fake-id"))
                    .build();
                HttpResponse<String> readResponse = client.send(readRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(401, readResponse.statusCode(), "non-author should be forbidden from author notifications read endpoint");
                assertTrue(readResponse.body().contains("Permission denied"), "read response should explain permission denied");
            } finally {
                server.stop(0);
            }
            }

    private static void testLibrarianCanViewApprovedBooksEndpoint() throws Exception {
        TestContext context = new TestContext();
        Book b1 = context.addApprovedBook("Approved One", "Author One", "Summary One");
        Book b2 = context.addApprovedBook("Approved Two", "Author Two", "Summary Two");
        b2.setAvailable(false);

        context.librarianService.registerLibrarian("lib-view", "Lib View", "Password1!", "EMP-VIEW");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "lib-view", "Password1!", "LIBRARIAN");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/approved-books"))
                    .GET()
                    .header("X-Session-Id", sessionId)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), "librarian approved books endpoint should return HTTP 200");
            String body = response.body();
            assertTrue(body.contains("\"title\":\"" + b1.getTitle() + "\""), "response should include first approved book");
            assertTrue(body.contains("\"title\":\"" + b2.getTitle() + "\""), "response should include second approved book");
            assertTrue(body.contains("\"status\":\"Available\""), "response should include available status");
            assertTrue(body.contains("\"status\":\"Unavailable\""), "response should include unavailable status");
        } finally {
            server.stop(0);
        }
    }

    private static void testNonLibrarianCannotAccessApprovedBooksEndpoint() throws Exception {
        TestContext context = new TestContext();
        context.addApprovedBook("Approved Three", "Author Three", "Summary Three");
        context.authService.registerStudentOrStaff("stu-no-access", "Stu NoAccess", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "stu-no-access", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/approved-books"))
                    .GET()
                    .header("X-Session-Id", sessionId)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode(), "non-librarian should receive HTTP 401");
            assertTrue(response.body().contains("Permission denied"), "response should explain permission denied");
        } finally {
            server.stop(0);
        }
    }

    private static void testStudentStaffNotificationApisListAndReadSuccess() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("staff-notify", "Staff Notify", "Password1!", Role.STAFF);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "staff-notify", "Password1!", "STAFF");

            HttpRequest profileUpdateRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/profile"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", sessionId)
                .POST(HttpRequest.BodyPublishers.ofString("fullName=Staff+Notify+Updated&password="))
                .build();
            HttpResponse<String> profileUpdateResponse = client.send(profileUpdateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, profileUpdateResponse.statusCode(), "profile update should generate a notification");

            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listResponse.statusCode(), "student/staff notifications list should return HTTP 200");
            String notificationId = extractJsonField(listResponse.body(), "id");
            assertTrue(listResponse.body().contains("\"title\":\"Profile Updated\""), "response should include notification title");
            assertTrue(listResponse.body().contains("\"message\":\"Your profile details were updated successfully.\""), "response should include notification message");
            assertTrue(listResponse.body().contains("\"createdAt\":"), "response should include createdAt");
            assertTrue(listResponse.body().contains("\"read\":false"), "notification should initially be unread");

            HttpRequest markReadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/read"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + notificationId))
                    .build();
            HttpResponse<String> markReadResponse = client.send(markReadRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, markReadResponse.statusCode(), "mark read should return HTTP 200");
            assertTrue(markReadResponse.body().contains("\"status\":\"read\""), "mark read response should return read status");

            HttpResponse<String> afterReadListResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, afterReadListResponse.statusCode(), "notifications list after read should return HTTP 200");
            assertTrue(afterReadListResponse.body().contains("\"id\":\"" + notificationId + "\""), "same notification should still exist");
            assertTrue(afterReadListResponse.body().contains("\"read\":true"), "notification should become read");
        } finally {
            server.stop(0);
        }
    }

    private static void testStudentStaffNotificationOwnershipBoundaryEndpoint() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("student-owner-a", "Owner A", "Password1!", Role.STUDENT);
        context.authService.registerStudentOrStaff("student-owner-b", "Owner B", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String ownerASession = loginAndGetSessionId(client, baseUrl, "student-owner-a", "Password1!", "STUDENT");

            HttpRequest ownerAProfileUpdateRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/profile"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", ownerASession)
                .POST(HttpRequest.BodyPublishers.ofString("fullName=Owner+A+Updated&password="))
                .build();
            HttpResponse<String> ownerAProfileUpdateResponse = client.send(ownerAProfileUpdateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownerAProfileUpdateResponse.statusCode(), "owner A profile update should generate notification");

            HttpRequest ownerAListRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/notifications"))
                .header("X-Session-Id", ownerASession)
                .GET()
                .build();
            HttpResponse<String> ownerAListResponse = client.send(ownerAListRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownerAListResponse.statusCode(), "owner A notifications should list successfully");
            String foreignNotificationId = extractJsonField(ownerAListResponse.body(), "id");

            String ownerBSession = loginAndGetSessionId(client, baseUrl, "student-owner-b", "Password1!", "STUDENT");

            HttpRequest markReadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/read"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", ownerBSession)
                .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + foreignNotificationId))
                    .build();
            HttpResponse<String> markReadResponse = client.send(markReadRequest, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, markReadResponse.statusCode(), "student/staff cannot mark another user's notification");
            assertTrue(markReadResponse.body().contains("does not belong to this user"), "response should explain ownership boundary");
        } finally {
            server.stop(0);
        }
    }

    private static void testAuthorAndLibrarianForbiddenFromStudentStaffNotificationApis() throws Exception {
        TestContext context = new TestContext();
        context.authorService.registerAuthor("author-notify-no", "Author Notify", "Password1!", "Bio");
        context.librarianService.registerLibrarian("lib-notify-no", "Lib Notify", "Password1!", "EMP-NO");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();

            String authorSession = loginAndGetSessionId(client, baseUrl, "author-notify-no", "Password1!", "AUTHOR");
            HttpRequest authorListRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications"))
                    .header("X-Session-Id", authorSession)
                    .GET()
                    .build();
            HttpResponse<String> authorListResponse = client.send(authorListRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, authorListResponse.statusCode(), "author should be forbidden from student/staff notifications list");
            assertTrue(authorListResponse.body().contains("Permission denied"), "author list response should explain permission denied");

            HttpRequest authorReadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/read"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", authorSession)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=fake-id"))
                    .build();
            HttpResponse<String> authorReadResponse = client.send(authorReadRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, authorReadResponse.statusCode(), "author should be forbidden from student/staff mark-read endpoint");
            assertTrue(authorReadResponse.body().contains("Permission denied"), "author mark-read response should explain permission denied");

            String librarianSession = loginAndGetSessionId(client, baseUrl, "lib-notify-no", "Password1!", "LIBRARIAN");
            HttpRequest librarianListRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications"))
                    .header("X-Session-Id", librarianSession)
                    .GET()
                    .build();
            HttpResponse<String> librarianListResponse = client.send(librarianListRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, librarianListResponse.statusCode(), "librarian should be forbidden from student/staff notifications list");
            assertTrue(librarianListResponse.body().contains("Permission denied"), "librarian list response should explain permission denied");

            HttpRequest librarianReadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/read"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", librarianSession)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=fake-id"))
                    .build();
            HttpResponse<String> librarianReadResponse = client.send(librarianReadRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, librarianReadResponse.statusCode(), "librarian should be forbidden from student/staff mark-read endpoint");
            assertTrue(librarianReadResponse.body().contains("Permission denied"), "librarian mark-read response should explain permission denied");
        } finally {
            server.stop(0);
        }
    }

    private static void testLibrarianCanViewBorrowedBooksRecords() throws Exception {
        TestContext context = new TestContext();
        Book activeBook = context.addApprovedBook("Borrowed Active", "Author Active", "Active summary");
        Book returnedBook = context.addApprovedBook("Borrowed Returned", "Author Returned", "Returned summary");
        Book overdueBook = context.addApprovedBook("Borrowed Overdue", "Author Overdue", "Overdue summary");

        context.authService.registerStudentOrStaff("borrower-one", "Borrower One", "Password1!", Role.STUDENT);
        BorrowRecord activeRecord = context.borrowService.borrowBook("borrower-one", activeBook.getId(), 7);
        BorrowRecord returnedRecord = context.borrowService.borrowBook("borrower-one", returnedBook.getId(), 7);
        context.borrowService.returnBook("borrower-one", returnedBook.getId());
        BorrowRecord overdueRecord = new BorrowRecord(
            "borrower-one",
            overdueBook.getId(),
            LocalDate.now().minusDays(10),
            LocalDate.now().minusDays(2)
        );
        context.borrowRepository.save(overdueRecord);

        context.librarianService.registerLibrarian("lib-records", "Lib Records", "Password1!", "EMP-RECORDS");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "lib-records", "Password1!", "LIBRARIAN");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/borrowed-records"))
                    .GET()
                    .header("X-Session-Id", sessionId)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), "librarian borrowed-records endpoint should return HTTP 200");
            String body = response.body();
            assertTrue(body.contains("\"borrowId\":\"" + activeRecord.getId() + "\""), "response should include active borrow id");
            assertTrue(body.contains("\"borrowId\":\"" + returnedRecord.getId() + "\""), "response should include returned borrow id");
            assertTrue(body.contains("\"borrowId\":\"" + overdueRecord.getId() + "\""), "response should include overdue borrow id");
            assertTrue(body.contains("\"bookId\":\"" + activeBook.getId() + "\""), "response should include book id");
            assertTrue(body.contains("\"bookTitle\":\"" + activeBook.getTitle() + "\""), "response should include book title");
            assertTrue(body.contains("\"borrowerUsername\":\"borrower-one\""), "response should include borrower username");
            assertTrue(body.contains("\"borrowDate\":"), "response should include borrow date field");
            assertTrue(body.contains("\"dueDate\":"), "response should include due date field");
            assertTrue(body.contains("\"status\":\"Borrowed\""), "response should include borrowed status");
            assertTrue(body.contains("\"status\":\"Returned\""), "response should include returned status");

            String activeRecordJson = extractBorrowRecordObject(body, activeRecord.getId());
            assertTrue(activeRecordJson.contains("\"returned\":false"), "active record should expose returned=false");
            assertTrue(activeRecordJson.contains("\"overdue\":false"), "active non-overdue record should expose overdue=false");

            String returnedRecordJson = extractBorrowRecordObject(body, returnedRecord.getId());
            assertTrue(returnedRecordJson.contains("\"returned\":true"), "returned record should expose returned=true");
            assertTrue(returnedRecordJson.contains("\"overdue\":false"), "returned record should not be marked overdue");

            String overdueRecordJson = extractBorrowRecordObject(body, overdueRecord.getId());
            assertTrue(overdueRecordJson.contains("\"returned\":false"), "overdue record should expose returned=false");
            assertTrue(overdueRecordJson.contains("\"overdue\":true"), "overdue active record should expose overdue=true");
        } finally {
            server.stop(0);
        }
    }

    private static void testNonLibrarianCannotAccessBorrowedBooksRecordsEndpoint() throws Exception {
        TestContext context = new TestContext();
        context.addApprovedBook("Records Blocked", "Author Blocked", "Summary Blocked");
        context.authService.registerStudentOrStaff("stu-records-no-access", "Stu Records", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "stu-records-no-access", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/borrowed-records"))
                    .GET()
                    .header("X-Session-Id", sessionId)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode(), "non-librarian should receive HTTP 401");
            assertTrue(response.body().contains("Permission denied"), "response should explain permission denied");
        } finally {
            server.stop(0);
        }
    }

        private static void testLibrarianRejectWithReasonPersistsAndNotifiesAuthor() throws Exception {
        TestContext context = new TestContext();
        Path file = createTempTextFile("rejection-reason", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-reject", "Author Reject", "Password1!", "Bio");
        context.librarianService.registerLibrarian("lib-reject", "Lib Reject", "Password1!", "EMP-REJECT");

        BookSubmission2 submission = context.authorService.publishBook(
            "author-reject",
            "Rejected With Reason",
            List.of("Technology"),
            "Description",
            file.toString()
        );

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String librarianSession = loginAndGetSessionId(client, baseUrl, "lib-reject", "Password1!", "LIBRARIAN");

            HttpRequest rejectRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/librarian/review"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", librarianSession)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "submissionId=" + submission.getId() +
                        "&action=reject&comment=Rejected&reason=Insufficient+references"
                ))
                .build();
            HttpResponse<String> rejectResponse = client.send(rejectRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, rejectResponse.statusCode(), "librarian reject endpoint should return HTTP 200");

            BookSubmission2 updated = context.submissionRepository.findById(submission.getId())
                .orElseThrow(() -> new AssertionError("submission should still exist after reject"));
            assertEquals(SubmissionState.REJECTED, updated.getStatus(), "submission should be rejected");
            assertEquals("Insufficient references", updated.getRejectionReason(), "reject endpoint should persist rejection reason");

            String authorSession = loginAndGetSessionId(client, baseUrl, "author-reject", "Password1!", "AUTHOR");
            HttpRequest notificationsRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/author/notifications"))
                .header("X-Session-Id", authorSession)
                .GET()
                .build();
            HttpResponse<String> notificationsResponse = client.send(notificationsRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, notificationsResponse.statusCode(), "author notifications endpoint should return HTTP 200");
            assertTrue(notificationsResponse.body().contains("\"title\":\"Submission Rejected\""), "notification should include rejection title");
            assertTrue(notificationsResponse.body().contains("Insufficient references"), "notification should include rejection reason");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
        }
        }

        private static void testNonLibrarianCannotRejectSubmissionsEndpoint() throws Exception {
        TestContext context = new TestContext();
        Path file = createTempTextFile("reject-guard", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-guard", "Author Guard", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
            "author-guard",
            "Guarded Submission",
            List.of("Technology"),
            "Description",
            file.toString()
        );
        context.authService.registerStudentOrStaff("stu-reject-no", "Stu Reject", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String studentSession = loginAndGetSessionId(client, baseUrl, "stu-reject-no", "Password1!", "STUDENT");

            HttpRequest rejectRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/librarian/review"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", studentSession)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "submissionId=" + submission.getId() + "&action=reject&comment=Rejected&reason=Not+allowed"
                ))
                .build();
            HttpResponse<String> rejectResponse = client.send(rejectRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, rejectResponse.statusCode(), "non-librarian reject request should be forbidden");
            assertTrue(rejectResponse.body().contains("Permission denied"), "response should explain permission denied");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
        }
        }

        private static void testRejectReasonLengthValidation() throws Exception {
        TestContext context = new TestContext();
        Path file = createTempTextFile("reject-length", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-length", "Author Length", "Password1!", "Bio");
        context.librarianService.registerLibrarian("lib-length", "Lib Length", "Password1!", "EMP-LENGTH");

        BookSubmission2 submission = context.authorService.publishBook(
            "author-length",
            "Length Checked Submission",
            List.of("Technology"),
            "Description",
            file.toString()
        );

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String librarianSession = loginAndGetSessionId(client, baseUrl, "lib-length", "Password1!", "LIBRARIAN");

            String longReason = "x".repeat(501);
            HttpRequest rejectRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/librarian/review"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", librarianSession)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "submissionId=" + submission.getId() + "&action=reject&comment=Rejected&reason=" + longReason
                ))
                .build();
            HttpResponse<String> rejectResponse = client.send(rejectRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, rejectResponse.statusCode(), "too-long rejection reason should be rejected");
            assertTrue(rejectResponse.body().contains("Rejection reason must be at most 500 characters."), "response should describe reason length limit");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
        }
        }

    private static void testLibrarianNotificationsListAndReadSuccess() throws Exception {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-notify", "Lib Notify", "Password1!", "EMP-NOTIFY");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "lib-notify", "Password1!", "LIBRARIAN");

            HttpRequest profileUpdate = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Lib+Notify+Updated&employeeId=EMP-NOTIFY&password="))
                    .build();
            HttpResponse<String> profileUpdateResponse = client.send(profileUpdate, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, profileUpdateResponse.statusCode(), "profile update should succeed");

            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/notifications"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listResponse.statusCode(), "librarian notifications list should return HTTP 200");
            assertTrue(listResponse.body().contains("\"title\":\"Librarian Profile Updated\""), "notifications should include librarian profile update message");
            assertTrue(listResponse.body().contains("\"read\":false"), "new notification should be unread");
            String notificationId = extractJsonField(listResponse.body(), "id");

            HttpRequest markReadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/notifications/read"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + notificationId))
                    .build();
            HttpResponse<String> markReadResponse = client.send(markReadRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, markReadResponse.statusCode(), "mark read should return HTTP 200");
            assertTrue(markReadResponse.body().contains("\"status\":\"read\""), "mark read response should return read status");

            HttpResponse<String> afterReadListResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, afterReadListResponse.statusCode(), "librarian notifications list after read should return HTTP 200");
            assertTrue(afterReadListResponse.body().contains("\"id\":\"" + notificationId + "\""), "same notification should still exist");
            assertTrue(afterReadListResponse.body().contains("\"read\":true"), "notification should become read");
        } finally {
            server.stop(0);
        }
    }

    private static void testLibrarianNotificationOwnershipBoundary() throws Exception {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-owner-a", "Lib Owner A", "Password1!", "EMP-A");
        context.librarianService.registerLibrarian("lib-owner-b", "Lib Owner B", "Password1!", "EMP-B");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String ownerASession = loginAndGetSessionId(client, baseUrl, "lib-owner-a", "Password1!", "LIBRARIAN");

            HttpRequest ownerAProfileUpdate = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", ownerASession)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Lib+Owner+A&employeeId=EMP-A&password="))
                    .build();
            HttpResponse<String> ownerAProfileResponse = client.send(ownerAProfileUpdate, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownerAProfileResponse.statusCode(), "owner A profile update should succeed");

            HttpRequest ownerAListRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/notifications"))
                    .header("X-Session-Id", ownerASession)
                    .GET()
                    .build();
            HttpResponse<String> ownerAListResponse = client.send(ownerAListRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownerAListResponse.statusCode(), "owner A notifications should list successfully");
            String ownerANotificationId = extractJsonField(ownerAListResponse.body(), "id");

            String ownerBSession = loginAndGetSessionId(client, baseUrl, "lib-owner-b", "Password1!", "LIBRARIAN");
            HttpRequest ownerBMarkReadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/notifications/read"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", ownerBSession)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + ownerANotificationId))
                    .build();
            HttpResponse<String> ownerBMarkReadResponse = client.send(ownerBMarkReadRequest, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, ownerBMarkReadResponse.statusCode(), "librarian cannot mark another librarian's notification");
            assertTrue(ownerBMarkReadResponse.body().contains("does not belong to this user"), "response should explain ownership boundary");
        } finally {
            server.stop(0);
        }
    }

    private static void testNonLibrarianForbiddenFromLibrarianNotificationApis() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("stu-notification-api", "Stu Notification", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "stu-notification-api", "Password1!", "STUDENT");

            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/notifications"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, listResponse.statusCode(), "non-librarian list request should be forbidden");
            assertTrue(listResponse.body().contains("Permission denied"), "list response should explain permission denied");

            HttpRequest markReadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/notifications/read"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=fake-id"))
                    .build();
            HttpResponse<String> markReadResponse = client.send(markReadRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, markReadResponse.statusCode(), "non-librarian mark read should be forbidden");
            assertTrue(markReadResponse.body().contains("Permission denied"), "mark read response should explain permission denied");
        } finally {
            server.stop(0);
        }
    }

    private static void testNotificationMetadataAndActionFoundation() {
        TestContext context = new TestContext();
        NotificationAction action = new NotificationAction("deeplink", "Open Book", "/books/view", "GET");

        NotificationItem item = context.notificationService.addNotification(
                "foundation-user",
                "Borrow Ready",
                "Open your borrowed book.",
                action,
                Map.of("bookId", "BOOK-123", "source", "borrow-flow")
        );

        assertEquals("deeplink", item.getAction().getType(), "action type should be stored");
        assertEquals("Open Book", item.getAction().getLabel(), "action label should be stored");
        assertEquals("BOOK-123", item.getMetadata().get("bookId"), "metadata should store book identifier");
        assertEquals("borrow-flow", item.getMetadata().get("source"), "metadata should keep source context");

        NotificationItem stored = context.notificationService.listByUser("foundation-user").get(0);
        assertEquals("BOOK-123", stored.getMetadata().get("bookId"), "stored notification should retain metadata");
    }

    private static void testBooksEndpointSupportsKeywordAndAvailabilityFilters() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("books-filter-user", "Books Filter User", "Password1!", Role.STUDENT);

        context.addApprovedBook("Clean Code", "Robert Martin", "Readable code guidance.");
        Book cleanArchitecture = context.addApprovedBook("Clean Architecture", "Robert Martin", "Architecture patterns.");
        context.addApprovedBook("Domain Modeling", "Eric Evans", "Domain-driven design.");
        cleanArchitecture.setAvailable(false);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "books-filter-user", "Password1!", "STUDENT");

            HttpRequest byKeywordRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/books?keyword=Clean"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> byKeywordResponse = client.send(byKeywordRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, byKeywordResponse.statusCode(), "keyword filter request should succeed");
            assertTrue(byKeywordResponse.body().contains("\"title\":\"Clean Code\""), "keyword filter should include matching title");
            assertTrue(byKeywordResponse.body().contains("\"title\":\"Clean Architecture\""), "keyword filter should include second matching title");
            assertFalse(byKeywordResponse.body().contains("\"title\":\"Domain Modeling\""), "keyword filter should exclude non-matching title");

            HttpRequest availableOnlyRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/books?availability=available"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> availableOnlyResponse = client.send(availableOnlyRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, availableOnlyResponse.statusCode(), "availability filter request should succeed");
            assertTrue(availableOnlyResponse.body().contains("\"title\":\"Clean Code\""), "available filter should keep available books");
            assertFalse(availableOnlyResponse.body().contains("\"title\":\"Clean Architecture\""), "available filter should exclude unavailable books");

            HttpRequest combinedFilterRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/books?q=Clean&availability=unavailable"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> combinedFilterResponse = client.send(combinedFilterRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, combinedFilterResponse.statusCode(), "combined filters request should succeed");
            assertTrue(combinedFilterResponse.body().contains("\"title\":\"Clean Architecture\""), "combined filters should include matching unavailable book");
            assertFalse(combinedFilterResponse.body().contains("\"title\":\"Clean Code\""), "combined filters should exclude available book");
        } finally {
            server.stop(0);
        }
    }

    private static void testBooksEndpointRejectsInvalidAvailabilityFilter() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("books-filter-invalid", "Books Filter Invalid", "Password1!", Role.STUDENT);
        context.addApprovedBook("Filter Validation", "Validator", "Validation sample.");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "books-filter-invalid", "Password1!", "STUDENT");

            HttpRequest invalidFilterRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/books?availability=maybe"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> invalidFilterResponse = client.send(invalidFilterRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalidFilterResponse.statusCode(), "invalid availability filter should be rejected");
            assertTrue(invalidFilterResponse.body().contains("availability must be one of"), "error should explain allowed availability values");
        } finally {
            server.stop(0);
        }
    }

    private static void testSharedFilterParsingForRecommendationLimit() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("filter-user", "Filter User", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "filter-user", "Password1!", "STUDENT");

            HttpRequest invalidLimitRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/recommendations?limit=abc"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> invalidLimitResponse = client.send(invalidLimitRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalidLimitResponse.statusCode(), "invalid numeric limit should be rejected");
            assertTrue(invalidLimitResponse.body().contains("Invalid numeric value for limit"), "response should indicate numeric parsing issue");

            HttpRequest outOfRangeLimitRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/recommendations?limit=0"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> outOfRangeLimitResponse = client.send(outOfRangeLimitRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, outOfRangeLimitResponse.statusCode(), "out-of-range limit should be rejected");
            assertTrue(outOfRangeLimitResponse.body().contains("limit must be between 1 and 50"), "response should indicate allowed range");
        } finally {
            server.stop(0);
        }
    }

    private static void testSessionSnapshotCrashRecoveryHook() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("crash-user", "Crash User", "Password1!", Role.STUDENT);
        context.addApprovedBook("Crash Recovery Book", "Ops Team", "Used to validate post-recovery access.");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "crash-user", "Password1!", "STUDENT");

            HttpRequest snapshotRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/internal/crash-test?action=snapshot"))
                    .header("X-Crash-Test-Hook", "enable")
                    .GET()
                    .build();
            HttpResponse<String> snapshotResponse = client.send(snapshotRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, snapshotResponse.statusCode(), "snapshot action should succeed");
            assertTrue(snapshotResponse.body().contains("\"schemaVersion\":1"), "snapshot should include schema version");
            assertTrue(snapshotResponse.body().contains("\"sessionId\":\"" + sessionId + "\""), "snapshot should include active session");

            HttpRequest simulateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/internal/crash-test"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Crash-Test-Hook", "enable")
                    .POST(HttpRequest.BodyPublishers.ofString("action=simulate"))
                    .build();
            HttpResponse<String> simulateResponse = client.send(simulateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, simulateResponse.statusCode(), "simulate action should succeed");
            assertTrue(simulateResponse.body().contains("\"status\":\"simulated\""), "simulate response should confirm crash simulation");

            HttpRequest beforeRecoverBooksRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/books"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> beforeRecoverBooksResponse = client.send(beforeRecoverBooksRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, beforeRecoverBooksResponse.statusCode(), "session should be invalid after simulated crash");

            HttpRequest recoverRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/internal/crash-test"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Crash-Test-Hook", "enable")
                    .POST(HttpRequest.BodyPublishers.ofString("action=recover"))
                    .build();
            HttpResponse<String> recoverResponse = client.send(recoverRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, recoverResponse.statusCode(), "recover action should succeed");
            assertTrue(recoverResponse.body().contains("\"status\":\"recovered\""), "recover response should confirm restoration");

            HttpResponse<String> afterRecoverBooksResponse = client.send(beforeRecoverBooksRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, afterRecoverBooksResponse.statusCode(), "restored session should be usable after recovery");
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer createApiServer(TestContext context) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        LibraryApiHandlers handlers = new LibraryApiHandlers(
                context.authService,
                context.bookService,
                context.borrowService,
                context.recommendationService,
                context.authorService,
                context.authorDraftService,
                context.fileService,
                context.librarianService
        );
        handlers.register(server);
        server.start();
        return server;
    }

    private static String loginAndGetSessionId(HttpClient client,
                                               String baseUrl,
                                               String username,
                                               String password,
                                               String role) throws Exception {
        String form = "username=" + username + "&password=" + password + "&role=" + role;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "login should succeed for endpoint test");

        String body = response.body();
        String marker = "\"sessionId\":\"";
        int start = body.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("sessionId not found in login response: " + body);
        }
        int from = start + marker.length();
        int end = body.indexOf('"', from);
        if (end < 0) {
            throw new AssertionError("sessionId terminator not found in login response: " + body);
        }
        return body.substring(from, end);
    }

    private static String extractJsonField(String body, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = body.indexOf(marker);
        if (start < 0) {
            throw new AssertionError(fieldName + " not found in response: " + body);
        }
        int from = start + marker.length();
        int end = body.indexOf('"', from);
        if (end < 0) {
            throw new AssertionError(fieldName + " terminator not found in response: " + body);
        }
        return body.substring(from, end);
    }

    private static String extractBorrowRecordObject(String responseBody, String borrowId) {
        String marker = "\"borrowId\":\"" + borrowId + "\"";
        int markerIndex = responseBody.indexOf(marker);
        if (markerIndex < 0) {
            throw new AssertionError("Borrow record not found in response: " + borrowId);
        }

        int objectStart = responseBody.lastIndexOf('{', markerIndex);
        if (objectStart < 0) {
            throw new AssertionError("Borrow record object start not found for: " + borrowId);
        }

        int objectEnd = responseBody.indexOf('}', markerIndex);
        if (objectEnd < 0) {
            throw new AssertionError("Borrow record object end not found for: " + borrowId);
        }

        return responseBody.substring(objectStart, objectEnd + 1);
    }

        private static void testLibrarianProfileUpdateSuccess() {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-profile", "Old Lib", "Password1!", "EMP-OLD");

        LibrarianService3.LibrarianProfileSnapshot before = context.librarianService.getLibrarianProfile("lib-profile");
        assertEquals("Old Lib", before.fullName(), "initial librarian full name should match");
        assertEquals("EMP-OLD", before.employeeId(), "initial employee id should match");

        LibrarianService3.LibrarianProfileSnapshot updated = context.librarianService.updateLibrarianProfile(
            "lib-profile",
            "lib-profile",
            "New Lib",
            "EMP-NEW",
            "NewPass1!"
        );
        assertEquals("New Lib", updated.fullName(), "librarian full name should update");
        assertEquals("EMP-NEW", updated.employeeId(), "employee id should update");

        context.librarianService.loginLibrarian("lib-profile", "NewPass1!");
        expectThrows(AuthenticationException.class,
            () -> context.librarianService.loginLibrarian("lib-profile", "Password1!"),
            "Invalid username or password");
        }

        private static void testLibrarianProfileValidationFailures() {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-validate", "Valid Librarian", "Password1!", "EMP-VALID");

        expectThrows(ValidationException.class,
            () -> context.librarianService.updateLibrarianProfile("lib-validate", "lib-validate", "", "EMP-VALID", ""),
            "Full Name cannot be empty");
        expectThrows(ValidationException.class,
            () -> context.librarianService.updateLibrarianProfile("lib-validate", "lib-validate", "Valid Librarian", "", ""),
            "Employee ID cannot be empty");
        expectThrows(ValidationException.class,
            () -> context.librarianService.updateLibrarianProfile("lib-validate", "lib-validate", "Valid Librarian", "EMP-VALID", "short"),
            "Password must be between 8 and 64 characters");
        }

        private static void testLibrarianProfileOwnershipBoundary() {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-a", "Lib A", "Password1!", "EMP-A");
        context.librarianService.registerLibrarian("lib-b", "Lib B", "Password1!", "EMP-B");

        expectThrows(ValidationException.class,
            () -> context.librarianService.updateLibrarianProfile("lib-a", "lib-b", "Changed", "EMP-CHANGED", ""),
            "Cannot update another librarian's profile");

        LibrarianProfile3 profileB = context.librarianProfileRepository.findByUsername("lib-b")
            .orElseThrow(() -> new AssertionError("expected lib-b profile to exist"));
        assertEquals("EMP-B", profileB.getEmployeeId(), "ownership boundary should keep target profile unchanged");
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