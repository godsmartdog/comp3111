package Library.Test;

import Library.Exception.BusinessException;
import Library.Exception.AuthenticationException;
import Library.Exception.ValidationException;
import Library.Model.Book;
import Library.Model.BookRequest2;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.NotificationAction;
import Library.Model.NotificationItem;
import Library.Model.NotificationPriority;
import Library.Model.ReadingProgress;
import Library.Model.Role;
import Library.Model.SubmissionState;
import Library.Model.AuthorProfile2;
import Library.Model.LibrarianProfile3;
import Library.Persistence.LibraryDatabase;
import Library.Persistence.LibraryDatabaseService;
import Library.Repository.MemoryNotificationRepository;
import Library.Repository.MemoryAuthorProfileRepository2;
import Library.Repository.MemoryBookDraftRepository2;
import Library.Repository.MemoryBookRequestRepository2;
import Library.Repository.MemoryBookRepository;
import Library.Repository.MemoryBookReviewRepository;
import Library.Repository.MemoryBookSubmissionRepository2;
import Library.Repository.MemoryBorrowRepository;
import Library.Repository.MemoryLibrarianProfileRepository3;
import Library.Repository.MemoryReadingProgressRepository;
import Library.Repository.MemorySessionSnapshotRepository;
import Library.Repository.MemoryUserRepository;
import Library.Security.SessionManager;
import Library.Service.AuthService;
import Library.Service.AuthorDraftService;
import Library.Service.AuthorService2;
import Library.Service.BookService;
import Library.Service.BookRequestService;
import Library.Service.BookReviewService;
import Library.Service.BorrowService;
import Library.Service.FileService;
import Library.Service.LibrarianService3;
import Library.Service.NotificationService;
import Library.Service.ReadingProgressService;
import Library.Service.RecommendationService;
import Library.Service.SessionSnapshotService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class LibraryIntegrationTest {
    public static void main(String[] args) throws Exception {
        TestRunner runner = new TestRunner();
        runner.run("student/staff borrow and return flow", LibraryIntegrationTest::testStudentBorrowAndReturnFlow);
        runner.run("borrow duration cannot exceed fourteen days", LibraryIntegrationTest::testBorrowDurationLimit);
        runner.run("bulk borrow endpoint supports multiple selection", LibraryIntegrationTest::testBulkBorrowEndpointSupportsMultipleSelection);
        runner.run("bulk borrow endpoint validates duration bounds", LibraryIntegrationTest::testBulkBorrowEndpointValidatesDurationBounds);
        runner.run("non student/staff cannot use bulk borrow endpoint", LibraryIntegrationTest::testNonStudentStaffCannotUseBulkBorrowEndpoint);
        runner.run("single borrow endpoint remains compatible", LibraryIntegrationTest::testSingleBorrowEndpointRemainsCompatible);
        runner.run("borrow records sort by due date desc", LibraryIntegrationTest::testBorrowRecordsSortByDueDateDesc);
        runner.run("borrow records filter returned only", LibraryIntegrationTest::testBorrowRecordsFilterReturnedOnly);
        runner.run("borrow records filter overdue only", LibraryIntegrationTest::testBorrowRecordsFilterOverdueOnly);
        runner.run("borrow records no-filter baseline remains active only", LibraryIntegrationTest::testBorrowRecordsNoFilterCompatibilityBaseline);
        runner.run("borrow reminders generate due-soon notification", LibraryIntegrationTest::testBorrowRemindersGenerateDueSoonNotification);
        runner.run("borrow reminders generate overdue notification", LibraryIntegrationTest::testBorrowRemindersGenerateOverdueNotification);
        runner.run("borrow reminders suppress duplicates on same day", LibraryIntegrationTest::testBorrowRemindersSuppressDuplicatesOnSameDay);
        runner.run("borrow reminders stay owner scoped", LibraryIntegrationTest::testBorrowRemindersStayOwnerScoped);
        runner.run("borrows endpoint remains compatible with reminder fields", LibraryIntegrationTest::testBorrowsEndpointRemainsCompatibleWithReminderFields);
        runner.run("borrow limit and recommendation ranking", LibraryIntegrationTest::testBorrowLimitAndRecommendations);
        runner.run("author draft publish and librarian approval", LibraryIntegrationTest::testAuthorDraftPublishAndApproval);
        runner.run("author publish accepts normalized genres", LibraryIntegrationTest::testAuthorPublishAcceptsNormalizedGenres);
        runner.run("librarian reject and bulk approve flow", LibraryIntegrationTest::testRejectAndBulkApproveFlow);
        runner.run("file preview reads uploaded text", LibraryIntegrationTest::testFilePreview);
        runner.run("auto return overdue borrows", LibraryIntegrationTest::testAutoReturnOverdueBorrows);
        runner.run("auto return emits high priority notification once", LibraryIntegrationTest::testAutoReturnEmitsHighPriorityNotificationOnce);
        runner.run("auto-return notification filters under auto-return category not other", LibraryIntegrationTest::testAutoReturnNotificationFiltersUnderAutoReturnCategoryNotOther);
        runner.run("reader expiry does not prevent auto-return flow", LibraryIntegrationTest::testReaderExpiryDoesNotPreventAutoReturnFlow);
        runner.run("bulk return succeeds for multiple borrows", LibraryIntegrationTest::testBulkReturnSucceedsForMultipleBorrows);
        runner.run("bulk return partial failure when id invalid", LibraryIntegrationTest::testBulkReturnPartialFailureWhenIdInvalid);
        runner.run("reading progress persistence", LibraryIntegrationTest::testReadingProgressPersistence);
        runner.run("reading history endpoint supports search and progress data", LibraryIntegrationTest::testReadingHistoryEndpointSupportsSearchAndProgressData);
        runner.run("review submission and book rating summaries", LibraryIntegrationTest::testReviewSubmissionAndBookRatingSummaries);
        // Demo-sprint note: this optional test is intentionally not run by default
        // because review submission can trigger slow sentiment/LLM inference.
        // Manual verification covers Section 1.9 helpful review sorting.
        // runner.run("reviews can be marked helpful and sorted", LibraryIntegrationTest::testReviewsCanBeMarkedHelpfulAndSorted);
        runner.run("book request submission and librarian upload flow", LibraryIntegrationTest::testBookRequestSubmissionAndLibrarianUploadFlow);
        runner.run("non-borrowed book progress access is denied", LibraryIntegrationTest::testProgressAccessRequiresActiveBorrow);
        runner.run("approved book keeps file metadata", LibraryIntegrationTest::testApprovedBookRetainsFileMetadata);
        runner.run("personal notifications can be listed and marked read", LibraryIntegrationTest::testNotificationListAndMarkRead);
        runner.run("cannot mark another user's notification", LibraryIntegrationTest::testNotificationOwnershipValidation);
        runner.run("author can view own published books", LibraryIntegrationTest::testAuthorPublishedBooksOwnOnly);
        runner.run("author published books enforce ownership boundary", LibraryIntegrationTest::testAuthorPublishedBooksOwnershipBoundary);
        runner.run("author owner can update published book metadata", LibraryIntegrationTest::testAuthorOwnerCanUpdatePublishedBookMetadata);
        runner.run("author cannot update published book with active borrows", LibraryIntegrationTest::testAuthorCannotUpdatePublishedBookWithActiveBorrows);
        runner.run("author can update published book after all borrows returned", LibraryIntegrationTest::testAuthorCanUpdatePublishedBookAfterAllBorrowsReturned);
        runner.run("author owner can delete published book without active borrows", LibraryIntegrationTest::testAuthorOwnerCanDeletePublishedBookWithoutActiveBorrows);
        runner.run("author non-owner cannot update or delete published book", LibraryIntegrationTest::testAuthorNonOwnerCannotUpdateOrDeletePublishedBook);
        runner.run("author delete published book blocked with active borrows", LibraryIntegrationTest::testAuthorDeletePublishedBookBlockedWhenActiveBorrowsExist);
        runner.run("author bulk delete pending submissions partial success", LibraryIntegrationTest::testAuthorBulkDeletePendingSubmissionsPartialSuccess);
        runner.run("author bulk delete published books skips active borrows and fires notification", LibraryIntegrationTest::testAuthorBulkDeletePublishedBooksSkipsActiveBorrowsAndFiresNotification);
        runner.run("author profile update success", LibraryIntegrationTest::testAuthorProfileUpdateSuccess);
        runner.run("author profile update validation", LibraryIntegrationTest::testAuthorProfileUpdateValidation);
        runner.run("author profile ownership boundary", LibraryIntegrationTest::testAuthorProfileOwnershipBoundary);
        runner.run("author owner can update pending submission", LibraryIntegrationTest::testAuthorOwnerCanUpdatePendingSubmission);
        runner.run("author owner can delete pending submission", LibraryIntegrationTest::testAuthorOwnerCanDeletePendingSubmission);
        runner.run("author non-owner cannot update or delete submission", LibraryIntegrationTest::testAuthorNonOwnerCannotUpdateOrDeleteSubmission);
        runner.run("approved or rejected submission cannot be updated or deleted", LibraryIntegrationTest::testApprovedOrRejectedSubmissionCannotBeUpdatedOrDeleted);
        runner.run("author owner can read own pending submission file preview", LibraryIntegrationTest::testAuthorOwnerCanReadOwnPendingSubmissionFilePreview);
        runner.run("author non-owner cannot read submission file preview", LibraryIntegrationTest::testAuthorNonOwnerCannotReadSubmissionFilePreview);
        runner.run("author read submission rejects invalid submission id", LibraryIntegrationTest::testAuthorReadSubmissionRejectsInvalidSubmissionId);
        runner.run("author read submission handles missing file safely", LibraryIntegrationTest::testAuthorReadSubmissionHandlesMissingFileSafely);
        runner.run("author delete rule messages remain consistent", LibraryIntegrationTest::testAuthorDeleteRuleMessagesRemainConsistent);
        runner.run("author notifications list and mark read", LibraryIntegrationTest::testAuthorNotificationListAndRead);
        runner.run("author notifications ownership boundary", LibraryIntegrationTest::testAuthorNotificationOwnershipBoundary);
        runner.run("author notifications summary unread count", LibraryIntegrationTest::testAuthorNotificationSummaryUnreadCount);
        runner.run("non-author forbidden from author notification APIs", LibraryIntegrationTest::testNonAuthorForbiddenFromAuthorNotificationApis);
        runner.run("librarian can view approved books endpoint", LibraryIntegrationTest::testLibrarianCanViewApprovedBooksEndpoint);
        runner.run("non-librarian cannot access approved books endpoint", LibraryIntegrationTest::testNonLibrarianCannotAccessApprovedBooksEndpoint);
        runner.run("librarian pending queue keyword search by title", LibraryIntegrationTest::testLibrarianPendingQueueKeywordSearchByTitle);
        runner.run("librarian pending queue keyword search by author", LibraryIntegrationTest::testLibrarianPendingQueueKeywordSearchByAuthor);
        runner.run("librarian pending queue status filter", LibraryIntegrationTest::testLibrarianPendingQueueStatusFilter);
        runner.run("librarian pending queue sort by submitted date desc", LibraryIntegrationTest::testLibrarianPendingQueueSortBySubmittedDateDesc);
        runner.run("librarian pending queue no-filter compatibility baseline", LibraryIntegrationTest::testLibrarianPendingQueueNoFilterCompatibilityBaseline);
        runner.run("non-librarian forbidden from pending queue endpoint", LibraryIntegrationTest::testNonLibrarianForbiddenFromPendingQueueEndpoint);
        runner.run("librarian profile update success", LibraryIntegrationTest::testLibrarianProfileUpdateSuccess);
        runner.run("librarian profile validation failures", LibraryIntegrationTest::testLibrarianProfileValidationFailures);
        runner.run("librarian profile ownership boundary", LibraryIntegrationTest::testLibrarianProfileOwnershipBoundary);
        runner.run("librarian password change requires current password", LibraryIntegrationTest::testLibrarianPasswordChangeRequiresCurrentPassword);
        runner.run("librarian password change rejects wrong current password", LibraryIntegrationTest::testLibrarianPasswordChangeRejectsWrongCurrentPassword);
        runner.run("librarian password change succeeds with correct current password", LibraryIntegrationTest::testLibrarianPasswordChangeSucceedsWithCorrectCurrentPassword);
        runner.run("librarian non-password profile update works without current password", LibraryIntegrationTest::testLibrarianNonPasswordProfileUpdateWorksWithoutCurrentPassword);
        runner.run("librarian inactive session expires and rejects api call", LibraryIntegrationTest::testLibrarianInactiveSessionExpiresAndRejectsApiCall);
        runner.run("librarian active session review endpoint remains functioning", LibraryIntegrationTest::testLibrarianActiveSessionReviewEndpointRemainsFunctioning);
        runner.run("librarian notifications list and mark read success", LibraryIntegrationTest::testLibrarianNotificationsListAndReadSuccess);
        runner.run("librarian notification ownership boundary", LibraryIntegrationTest::testLibrarianNotificationOwnershipBoundary);
        runner.run("non-librarian forbidden from librarian notification APIs", LibraryIntegrationTest::testNonLibrarianForbiddenFromLibrarianNotificationApis);
        runner.run("student/staff notifications list and mark read success", LibraryIntegrationTest::testStudentStaffNotificationApisListAndReadSuccess);
        runner.run("student/staff notification ownership boundary endpoint", LibraryIntegrationTest::testStudentStaffNotificationOwnershipBoundaryEndpoint);
        runner.run("student/staff notification archive and unarchive success", LibraryIntegrationTest::testStudentStaffNotificationArchiveAndUnarchiveSuccess);
        runner.run("student/staff notification archive scope filtering and unread consistency", LibraryIntegrationTest::testStudentStaffNotificationArchiveScopeFilteringAndUnreadConsistency);
        runner.run("student/staff notifications keyword search filter", LibraryIntegrationTest::testStudentStaffNotificationKeywordSearchFilter);
        runner.run("student/staff notifications read filter", LibraryIntegrationTest::testStudentStaffNotificationReadFilter);
        runner.run("student/staff notifications priority filter", LibraryIntegrationTest::testStudentStaffNotificationPriorityFilter);
        runner.run("student/staff notifications scope and filter combo", LibraryIntegrationTest::testStudentStaffNotificationScopeAndFilterCombo);
        runner.run("student/staff notifications sort by createdAt desc", LibraryIntegrationTest::testStudentStaffNotificationSortByCreatedAtDesc);
        runner.run("student/staff notifications invalid filter values return bad request", LibraryIntegrationTest::testStudentStaffNotificationInvalidFilterValuesReturnBadRequest);
        runner.run("student/staff notifications filters keep ownership scoping", LibraryIntegrationTest::testStudentStaffNotificationFiltersKeepOwnershipScoping);
        runner.run("student/staff profile password change requires current password", LibraryIntegrationTest::testStudentStaffProfilePasswordChangeRequiresCurrentPassword);
        runner.run("student/staff profile password change rejects wrong current password", LibraryIntegrationTest::testStudentStaffProfilePasswordChangeRejectsWrongCurrentPassword);
        runner.run("student/staff profile password change succeeds with correct current password", LibraryIntegrationTest::testStudentStaffProfilePasswordChangeSucceedsWithCorrectCurrentPassword);
        runner.run("student/staff profile update without password does not require current password", LibraryIntegrationTest::testStudentStaffProfileUpdateWithoutPasswordDoesNotRequireCurrentPassword);
        runner.run("student/staff inactive session expires automatically", LibraryIntegrationTest::testStudentStaffInactiveSessionExpiresAutomatically);
        runner.run("student/staff active session stays valid with continuous activity", LibraryIntegrationTest::testStudentStaffActiveSessionStaysValidWithContinuousActivity);
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
        runner.run("session snapshot save and get success", LibraryIntegrationTest::testSessionSnapshotSaveAndGetSuccess);
        runner.run("session snapshot clear removes state", LibraryIntegrationTest::testSessionSnapshotClearRemovesState);
        runner.run("session snapshot no state returns safe empty response", LibraryIntegrationTest::testSessionSnapshotNoSnapshotReturnsSafeEmptyResponse);
        runner.run("session snapshot ownership is session scoped", LibraryIntegrationTest::testSessionSnapshotOwnershipIsSessionScoped);
        runner.run("logout clears session snapshot", LibraryIntegrationTest::testLogoutClearsSessionSnapshot);
        runner.run("session restore endpoint rehydrates session", LibraryIntegrationTest::testSessionRestoreEndpointRehydratesSession);
        runner.run("crash restore rejects role mismatch", LibraryIntegrationTest::testCrashRestoreRejectsRoleMismatch);
        runner.run("dev crash hook for snapshots is guarded", LibraryIntegrationTest::testDevCrashHookForSnapshotIsGuarded);
        runner.run("dev crash hook can save session snapshot", LibraryIntegrationTest::testDevCrashHookCanSaveSessionSnapshot);
        runner.run("session crash hook supports snapshot and recovery", LibraryIntegrationTest::testSessionSnapshotCrashRecoveryHook);
        runner.run("persistent database round trip", LibraryIntegrationTest::testPersistentDatabaseRoundTrip);
        runner.finish();
    }

    private static void testPersistentDatabaseRoundTrip() throws Exception {
        Path dbPath = Files.createTempDirectory("library-db-test").resolve("library-db.ser");
        dbPath.toFile().deleteOnExit();

        LibraryDatabase database = new LibraryDatabase();
        database.userRepository.save(new Library.Model.User("persist-user", "Persist User", "hash", Role.STUDENT));

        Book book = new Book(
            "Persistent Systems",
            "persist-author",
            "Persist Author",
            List.of("Technology"),
            "Database round-trip test book."
        );
        book.approve(LocalDate.now());
        database.bookRepository.save(book);

        BookRequest2 request = new BookRequest2(
            "Persistent Request",
            "persist-user",
            "Persist User",
            "Persist Author",
            List.of("Technology"),
            "Needed for persistence test."
        );
        database.bookRequestRepository.save(request);
        database.notificationRepository.save(new NotificationItem(
            "persist-user",
            "Persistence saved",
            "This notification should survive a database round trip.",
            LocalDateTime.now()
        ));

        LibraryDatabaseService.save(dbPath, database);
        LibraryDatabase loaded = LibraryDatabaseService.load(dbPath)
            .orElseThrow(() -> new AssertionError("database should load after save"));

        assertTrue(loaded.userRepository.existsByUsername("persist-user"), "loaded database should contain saved user");
        Book loadedBook = loaded.bookRepository.findById(book.getId())
            .orElseThrow(() -> new AssertionError("loaded database should contain saved book"));
        assertEquals("Persistent Systems", loadedBook.getTitle(), "loaded book title should match");
        assertEquals(request.getId(), loaded.bookRequestRepository.findById(request.getId())
            .orElseThrow(() -> new AssertionError("loaded database should contain saved request"))
            .getId(), "loaded request id should match");
        assertEquals(1, loaded.notificationRepository.findByUsername("persist-user").size(), "loaded notification should be queryable by user");
    }

    private static void testSessionRestoreEndpointRehydratesSession() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("restore-user", "Restore User", "Password1!", Role.STUDENT);
        context.addApprovedBook("Restore Book", "Recovery Team", "Book visible after session restore.");

        HttpClient client = HttpClient.newHttpClient();
        String sessionId;
        HttpServer firstServer = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + firstServer.getAddress().getPort();
            sessionId = loginAndGetSessionId(client, baseUrl, "restore-user", "Password1!", "STUDENT");
            HttpRequest snapshotRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot/save"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("portalKey=student-books.html&lastViewKey=default&lastAction=test&statePayload=%7B%7D"))
                    .build();
            HttpResponse<String> snapshotResponse = client.send(snapshotRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, snapshotResponse.statusCode(), "snapshot save should succeed before restore test");
        } finally {
            firstServer.stop(0);
        }

        HttpServer restartedServer = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + restartedServer.getAddress().getPort();
            HttpRequest restoreRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session/restore"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("sessionId=" + sessionId + "&username=restore-user&role=STUDENT"))
                    .build();
            HttpResponse<String> restoreResponse = client.send(restoreRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, restoreResponse.statusCode(), "session restore should return HTTP 200");
            assertTrue(restoreResponse.body().contains("\"restored\":true"), "restore response should confirm success");

            HttpRequest booksRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/books"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> booksResponse = client.send(booksRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, booksResponse.statusCode(), "authenticated endpoint should work with restored session id");
            assertTrue(booksResponse.body().contains("Restore Book"), "restored session should see approved books");
        } finally {
            restartedServer.stop(0);
        }
    }

    private static void testCrashRestoreRejectsRoleMismatch() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("restore-mismatch", "Restore Mismatch", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpRequest restoreRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session/restore"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("sessionId=mismatch-session&username=restore-mismatch&role=AUTHOR"))
                    .build();
            HttpResponse<String> restoreResponse = HttpClient.newHttpClient().send(restoreRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, restoreResponse.statusCode(), "role mismatch restore should be rejected");
            assertTrue(restoreResponse.body().contains("role mismatch"), "restore rejection should explain role mismatch");
        } finally {
            server.stop(0);
        }
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

    private static void testBorrowRecordsSortByDueDateDesc() throws Exception {
        TestContext context = new TestContext();
        Book shortDue = context.addApprovedBook("Sort Short Due", "Sorter", "Short due book.");
        Book longDue = context.addApprovedBook("Sort Long Due", "Sorter", "Long due book.");
        context.authService.registerStudentOrStaff("sort-user", "Sort User", "Password1!", Role.STUDENT);

        BorrowRecord shortRecord = context.borrowService.borrowBook("sort-user", shortDue.getId(), 3);
        BorrowRecord longRecord = context.borrowService.borrowBook("sort-user", longDue.getId(), 10);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "sort-user", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrows?status=all&sortBy=dueDate&sortDir=desc"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "sorted borrows request should return HTTP 200");

            String firstRecordId = extractJsonField(response.body(), "recordId");
            assertEquals(longRecord.getId(), firstRecordId, "due date desc should return later due date first");
            assertTrue(response.body().contains("\"recordId\":\"" + shortRecord.getId() + "\""), "response should include second borrow record");
        } finally {
            server.stop(0);
        }
    }

    private static void testBorrowRecordsFilterReturnedOnly() throws Exception {
        TestContext context = new TestContext();
        Book returnedBook = context.addApprovedBook("Returned Filter Book", "Filterer", "Should appear in returned filter.");
        Book activeBook = context.addApprovedBook("Active Filter Book", "Filterer", "Should not appear in returned filter.");
        context.authService.registerStudentOrStaff("returned-filter-user", "Returned Filter User", "Password1!", Role.STUDENT);

        BorrowRecord returnedRecord = context.borrowService.borrowBook("returned-filter-user", returnedBook.getId(), 7);
        context.borrowService.borrowBook("returned-filter-user", activeBook.getId(), 7);
        context.borrowService.returnBook("returned-filter-user", returnedBook.getId());

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "returned-filter-user", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrows?status=returned"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "returned filter request should return HTTP 200");

            String body = response.body();
            assertTrue(body.contains("\"recordId\":\"" + returnedRecord.getId() + "\""), "returned filter should include returned record");
            assertTrue(body.contains("\"returned\":true"), "returned filter payload should expose returned=true");
            assertFalse(body.contains("\"bookTitle\":\"Active Filter Book\""), "returned filter should exclude active borrows");
        } finally {
            server.stop(0);
        }
    }

    private static void testBorrowRecordsFilterOverdueOnly() throws Exception {
        TestContext context = new TestContext();
        Book overdueBook = context.addApprovedBook("Overdue Filter Book", "Filterer", "Should appear in overdue filter.");
        Book activeBook = context.addApprovedBook("Non Overdue Filter Book", "Filterer", "Should not appear in overdue filter.");
        context.authService.registerStudentOrStaff("overdue-filter-user", "Overdue Filter User", "Password1!", Role.STUDENT);

        context.borrowService.borrowBook("overdue-filter-user", activeBook.getId(), 7);

        BorrowRecord overdueRecord = new BorrowRecord(
                "overdue-filter-user",
                overdueBook.getId(),
                LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(2)
        );
        context.borrowRepository.save(overdueRecord);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "overdue-filter-user", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrows?status=overdue"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "overdue filter request should return HTTP 200");

            String body = response.body();
            assertTrue(body.contains("\"recordId\":\"" + overdueRecord.getId() + "\""), "overdue filter should include overdue record");
            assertTrue(body.contains("\"overdue\":true"), "overdue filter payload should expose overdue=true");
            assertFalse(body.contains("\"bookTitle\":\"Non Overdue Filter Book\""), "overdue filter should exclude non-overdue borrows");
        } finally {
            server.stop(0);
        }
    }

    private static void testBorrowRecordsNoFilterCompatibilityBaseline() throws Exception {
        TestContext context = new TestContext();
        Book returnedBook = context.addApprovedBook("Baseline Returned Book", "Baseline", "Returned record for baseline check.");
        Book activeBook = context.addApprovedBook("Baseline Active Book", "Baseline", "Active record for baseline check.");
        context.authService.registerStudentOrStaff("baseline-user", "Baseline User", "Password1!", Role.STUDENT);

        BorrowRecord activeRecord = context.borrowService.borrowBook("baseline-user", activeBook.getId(), 7);
        BorrowRecord returnedRecord = context.borrowService.borrowBook("baseline-user", returnedBook.getId(), 7);
        context.borrowService.returnBook("baseline-user", returnedBook.getId());

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "baseline-user", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrows"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "no-filter borrows request should return HTTP 200");

            String body = response.body();
            assertTrue(body.contains("\"recordId\":\"" + activeRecord.getId() + "\""), "default borrows listing should include active record");
            assertFalse(body.contains("\"recordId\":\"" + returnedRecord.getId() + "\""), "default borrows listing should keep returned records hidden");
        } finally {
            server.stop(0);
        }
    }

    private static void testBorrowRemindersGenerateDueSoonNotification() throws Exception {
        TestContext context = new TestContext();
        Book book = context.addApprovedBook("Due Soon Reminder Book", "Reminder Author", "Due soon reminder target.");
        context.authService.registerStudentOrStaff("reminder-due-soon", "Reminder Due Soon", "Password1!", Role.STUDENT);
        BorrowRecord record = context.borrowService.borrowBook("reminder-due-soon", book.getId(), 2);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "reminder-due-soon", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrow/reminders/check"))
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "manual reminder check should return HTTP 200");
            assertTrue(response.body().contains("\"generated\":1"), "manual reminder check should report one generated due-soon reminder");

            long reminderCount = countBorrowReminderNotifications(
                    context.notificationService.listByUser("reminder-due-soon"),
                    record.getId(),
                    "due-soon"
            );
            assertEquals(1L, reminderCount, "due-soon reminder should be created exactly once");

            NotificationItem reminder = context.notificationService.listByUser("reminder-due-soon").stream()
                    .filter(item -> "borrow-reminder".equals(item.getMetadata().get("type")))
                    .filter(item -> record.getId().equals(item.getMetadata().get("borrowRecordId")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected due-soon reminder notification"));
            assertEquals(NotificationPriority.NORMAL, reminder.getPriority(), "due-soon reminder should use normal priority");
        } finally {
            server.stop(0);
        }
    }

    private static void testBorrowRemindersGenerateOverdueNotification() throws Exception {
        TestContext context = new TestContext();
        Book book = context.addApprovedBook("Overdue Reminder Book", "Reminder Author", "Overdue reminder target.");
        context.authService.registerStudentOrStaff("reminder-overdue", "Reminder Overdue", "Password1!", Role.STUDENT);
        book.setAvailable(false);
        BorrowRecord record = new BorrowRecord(
                "reminder-overdue",
                book.getId(),
                LocalDate.now().minusDays(7),
                LocalDate.now().minusDays(1)
        );
        context.borrowRepository.save(record);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "reminder-overdue", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrow/reminders/check"))
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "manual reminder check should return HTTP 200 for overdue borrow");
            assertTrue(response.body().contains("\"generated\":1"), "manual reminder check should report one generated overdue reminder");

            NotificationItem reminder = context.notificationService.listByUser("reminder-overdue").stream()
                    .filter(item -> "borrow-reminder".equals(item.getMetadata().get("type")))
                    .filter(item -> "overdue".equals(item.getMetadata().get("category")))
                    .filter(item -> record.getId().equals(item.getMetadata().get("borrowRecordId")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected overdue reminder notification"));
            assertEquals(NotificationPriority.HIGH, reminder.getPriority(), "overdue reminder should use high priority");
        } finally {
            server.stop(0);
        }
    }

    private static void testBorrowRemindersSuppressDuplicatesOnSameDay() throws Exception {
        TestContext context = new TestContext();
        Book book = context.addApprovedBook("Duplicate Reminder Book", "Reminder Author", "Duplicate suppression target.");
        context.authService.registerStudentOrStaff("reminder-duplicate", "Reminder Duplicate", "Password1!", Role.STUDENT);
        BorrowRecord record = context.borrowService.borrowBook("reminder-duplicate", book.getId(), 2);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "reminder-duplicate", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrow/reminders/check"))
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> firstResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> secondResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, firstResponse.statusCode(), "first reminder check should return HTTP 200");
            assertEquals(200, secondResponse.statusCode(), "second reminder check should return HTTP 200");
            assertTrue(firstResponse.body().contains("\"generated\":1"), "first reminder check should create one reminder");
            assertTrue(secondResponse.body().contains("\"generated\":0"), "second reminder check should suppress duplicate reminder creation");

            long reminderCount = countBorrowReminderNotifications(
                    context.notificationService.listByUser("reminder-duplicate"),
                    record.getId(),
                    "due-soon"
            );
            assertEquals(1L, reminderCount, "duplicate reminder checks should keep exactly one reminder for the same borrow/day/category");
        } finally {
            server.stop(0);
        }
    }

    private static void testBorrowRemindersStayOwnerScoped() throws Exception {
        TestContext context = new TestContext();
        Book book = context.addApprovedBook("Owner Scoped Reminder Book", "Reminder Author", "Ownership reminder target.");
        context.authService.registerStudentOrStaff("reminder-owner-a", "Reminder Owner A", "Password1!", Role.STUDENT);
        context.authService.registerStudentOrStaff("reminder-owner-b", "Reminder Owner B", "Password1!", Role.STUDENT);
        BorrowRecord record = context.borrowService.borrowBook("reminder-owner-a", book.getId(), 2);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String otherSessionId = loginAndGetSessionId(client, baseUrl, "reminder-owner-b", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrow/reminders/check"))
                    .header("X-Session-Id", otherSessionId)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "other user reminder check should return HTTP 200");
            assertTrue(response.body().contains("\"generated\":0"), "other user should not generate reminders for someone else's borrow");

            long ownerReminderCount = countBorrowReminderNotifications(
                    context.notificationService.listByUser("reminder-owner-a"),
                    record.getId(),
                    "due-soon"
            );
            assertEquals(0L, ownerReminderCount, "other user's reminder check must not create owner reminders");
        } finally {
            server.stop(0);
        }
    }

    private static void testBorrowsEndpointRemainsCompatibleWithReminderFields() throws Exception {
        TestContext context = new TestContext();
        Book dueSoonBook = context.addApprovedBook("Borrow Reminder Due Soon API", "Reminder Author", "Due soon API payload target.");
        Book overdueBook = context.addApprovedBook("Borrow Reminder Overdue API", "Reminder Author", "Overdue API payload target.");
        context.authService.registerStudentOrStaff("reminder-api", "Reminder Api", "Password1!", Role.STUDENT);

        BorrowRecord dueSoonRecord = context.borrowService.borrowBook("reminder-api", dueSoonBook.getId(), 2);
        overdueBook.setAvailable(false);
        BorrowRecord overdueRecord = new BorrowRecord(
                "reminder-api",
                overdueBook.getId(),
                LocalDate.now().minusDays(8),
                LocalDate.now().minusDays(2)
        );
        context.borrowRepository.save(overdueRecord);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "reminder-api", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrows?status=all"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "borrows endpoint should remain compatible with reminder fields enabled");

            String body = response.body();
            assertTrue(body.contains("\"recordId\":\"" + dueSoonRecord.getId() + "\""), "borrows response should include due-soon record");
            assertTrue(body.contains("\"recordId\":\"" + overdueRecord.getId() + "\""), "borrows response should include overdue record");
            assertTrue(body.contains("\"dueSoon\":true"), "borrows response should expose dueSoon as a boolean");
            assertTrue(body.contains("\"reminderLevel\":\"due-soon\""), "borrows response should expose due-soon reminder level");
            assertTrue(body.contains("\"overdue\":true"), "borrows response should expose overdue as a boolean");
            assertTrue(body.contains("\"reminderLevel\":\"overdue\""), "borrows response should expose overdue reminder level");
            assertTrue(body.contains("\"daysUntilDue\":2"), "borrows response should expose numeric daysUntilDue values without quoting");

            long generatedReminderCount = context.notificationService.listByUser("reminder-api").stream()
                    .filter(item -> "borrow-reminder".equals(item.getMetadata().get("type")))
                    .count();
            assertEquals(2L, generatedReminderCount, "borrows endpoint should generate one reminder per due-soon or overdue borrow");
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

    private static void testAutoReturnEmitsHighPriorityNotificationOnce() {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("auto-return-user", "Auto Return User", "Password1!", Role.STUDENT);
        Book book = context.addApprovedBook("Overdue Notification Book", "Tester", "Auto-return notification fixture.");
        book.setAvailable(false);

        BorrowRecord overdue = new BorrowRecord("auto-return-user", book.getId(), LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        context.borrowRepository.save(overdue);

        context.borrowService.listActiveBorrowsByUser("auto-return-user");

        List<NotificationItem> first = context.notificationService.listByUser("auto-return-user");
        List<NotificationItem> autoReturnFirst = first.stream()
                .filter(n -> "auto-return".equals(n.getMetadata().getOrDefault("type", "")))
                .filter(n -> overdue.getId().equals(n.getMetadata().getOrDefault("borrowRecordId", "")))
                .collect(Collectors.toList());
        assertEquals(1, autoReturnFirst.size(), "auto-return should emit exactly one notification for the record");
        NotificationItem notification = autoReturnFirst.get(0);
        assertEquals(NotificationPriority.HIGH, notification.getPriority(), "auto-return notification should have HIGH priority");
        assertEquals(book.getId(), notification.getMetadata().getOrDefault("bookId", ""), "metadata should carry bookId");
        assertEquals(overdue.getDueDate().toString(), notification.getMetadata().getOrDefault("dueDate", ""), "metadata should carry dueDate");

        // Second call must not produce a duplicate even though autoReturnOverdueBooks runs again.
        context.borrowService.listActiveBorrowsByUser("auto-return-user");
        List<NotificationItem> second = context.notificationService.listByUser("auto-return-user");
        long autoReturnSecond = second.stream()
                .filter(n -> "auto-return".equals(n.getMetadata().getOrDefault("type", "")))
                .filter(n -> overdue.getId().equals(n.getMetadata().getOrDefault("borrowRecordId", "")))
                .count();
        assertEquals(1L, autoReturnSecond, "dedup should prevent repeat auto-return notifications");
    }

    private static void testAutoReturnNotificationFiltersUnderAutoReturnCategoryNotOther() {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("auto-return-category-user", "Auto Return Category User", "Password1!", Role.STUDENT);
        Book book = context.addApprovedBook("Auto Return Category Book", "Tester", "Slice 12 first-class category fixture.");
        book.setAvailable(false);

        BorrowRecord overdue = new BorrowRecord(
                "auto-return-category-user",
                book.getId(),
                LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1)
        );
        context.borrowRepository.save(overdue);

        // Slice 9 emits the auto-return notification on the first list call.
        context.borrowService.listActiveBorrowsByUser("auto-return-category-user");

        long autoReturnByMetadataType = context.notificationService.listByUser("auto-return-category-user").stream()
                .filter(n -> "auto-return".equals(n.getMetadata().getOrDefault("type", "")))
                .count();
        assertEquals(1L, autoReturnByMetadataType,
                "auto-return notification metadata.type literal must match the first-class category key");
    }

    private static void testReaderExpiryDoesNotPreventAutoReturnFlow() {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("reader-expiry-user", "Reader Expiry User", "Password1!", Role.STUDENT);
        Book book = context.addApprovedBook("Reader Expiry Book", "Tester", "Reader expiry watchdog backend invariant.");
        book.setAvailable(false);

        BorrowRecord overdue = new BorrowRecord(
                "reader-expiry-user",
                book.getId(),
                LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1)
        );
        context.borrowRepository.save(overdue);

        // Slice 11 watchdog calls /api/borrows?status=active on expiry; that hits listActiveBorrowsByUser.
        List<BorrowRecord> active = context.borrowService.listActiveBorrowsByUser("reader-expiry-user");

        assertEquals(0, active.size(), "auto-returned record should be filtered from active list");
        assertTrue(overdue.isReturned(), "overdue record should be marked returned");
        assertTrue(overdue.isAutoReturned(), "overdue record should be flagged auto-returned");
        assertTrue(book.isAvailable(), "book should become available after auto-return");

        List<NotificationItem> autoReturnNotifications = context.notificationService.listByUser("reader-expiry-user").stream()
                .filter(n -> "auto-return".equals(n.getMetadata().getOrDefault("type", "")))
                .filter(n -> overdue.getId().equals(n.getMetadata().getOrDefault("borrowRecordId", "")))
                .collect(Collectors.toList());
        assertEquals(1, autoReturnNotifications.size(), "Slice 9 auto-return notification must fire when watchdog pings list endpoint");
        assertEquals(NotificationPriority.HIGH, autoReturnNotifications.get(0).getPriority(), "auto-return notification should be HIGH priority");
    }

    private static void testBulkReturnSucceedsForMultipleBorrows() {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("bulk-return-user", "Bulk Return User", "Password1!", Role.STUDENT);
        Book book1 = context.addApprovedBook("Bulk Return One", "Tester", "Bulk return fixture #1.");
        Book book2 = context.addApprovedBook("Bulk Return Two", "Tester", "Bulk return fixture #2.");
        Book book3 = context.addApprovedBook("Bulk Return Three", "Tester", "Bulk return fixture #3.");
        context.borrowService.borrowBook("bulk-return-user", book1.getId(), 7);
        context.borrowService.borrowBook("bulk-return-user", book2.getId(), 7);
        context.borrowService.borrowBook("bulk-return-user", book3.getId(), 7);

        // Mirror the handler's loop: try each id, count succeeded, capture failed.
        List<String> idsToReturn = List.of(book1.getId(), book2.getId());
        int succeeded = 0;
        for (String bookId : idsToReturn) {
            try {
                BorrowRecord record = context.borrowService.returnBook("bulk-return-user", bookId);
                String title = context.bookService.findBookById(bookId).map(Book::getTitle).orElse(bookId);
                context.notificationService.addNotification(
                        "bulk-return-user",
                        "Book Returned",
                        "You return this book (" + title + "). Due date was: " + record.getDueDate(),
                        NotificationPriority.NORMAL,
                        null,
                        Map.of("type", "borrow-reminder", "bookId", bookId)
                );
                succeeded++;
            } catch (Exception ignored) {
                // not expected in this test
            }
        }

        assertEquals(2, succeeded, "two borrows should be returned");
        List<BorrowRecord> active = context.borrowService.listActiveBorrowsByUser("bulk-return-user");
        assertEquals(1, active.size(), "one borrow should remain active");
        assertEquals(book3.getId(), active.get(0).getBookId(), "third book should still be borrowed");

        long bookReturnedNotifications = context.notificationService.listByUser("bulk-return-user").stream()
                .filter(n -> "Book Returned".equals(n.getTitle()))
                .filter(n -> n.getPriority() == NotificationPriority.NORMAL)
                .count();
        assertEquals(2L, bookReturnedNotifications, "one Book Returned notification per successful return");

        List<BorrowRecord> all = context.borrowRepository.findByUsername("bulk-return-user");
        long manuallyReturned = all.stream()
                .filter(BorrowRecord::isReturned)
                .filter(r -> !r.isAutoReturned())
                .count();
        assertEquals(2L, manuallyReturned, "returned records should not be flagged as auto-returned");
    }

    private static void testBulkReturnPartialFailureWhenIdInvalid() {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("bulk-fail-user", "Bulk Fail User", "Password1!", Role.STUDENT);
        Book book1 = context.addApprovedBook("Bulk Fail One", "Tester", "Bulk-fail fixture #1.");
        Book book2 = context.addApprovedBook("Bulk Fail Two", "Tester", "Bulk-fail fixture #2.");
        context.borrowService.borrowBook("bulk-fail-user", book1.getId(), 7);
        context.borrowService.borrowBook("bulk-fail-user", book2.getId(), 7);

        List<String> ids = List.of(book1.getId(), "nonexistent-book-id", book2.getId());
        int succeeded = 0;
        List<String> failedReasons = new java.util.ArrayList<>();
        for (String bookId : ids) {
            try {
                context.borrowService.returnBook("bulk-fail-user", bookId);
                succeeded++;
            } catch (Exception ex) {
                failedReasons.add(ex.getMessage() == null ? "" : ex.getMessage());
            }
        }

        assertEquals(2, succeeded, "two valid ids should succeed");
        assertEquals(1, failedReasons.size(), "one id should fail");
        String reason = failedReasons.get(0);
        assertTrue(
                reason.contains("not currently borrowed") || reason.contains("Book not found"),
                "failure reason should mention not-borrowed or not-found, got: " + reason
        );
    }

    private static void testReadingProgressPersistence() {
        TestContext context = new TestContext();
        context.readingProgressService.updateProgress("reader-1", "book-1", 7, List.of("line A", "line B"));

        ReadingProgress progress = context.readingProgressService.getProgress("reader-1", "book-1");
        assertEquals(7, progress.getBookmarkPage(), "bookmark should persist");
        assertEquals(List.of("line A", "line B"), progress.getHighlights(), "highlights should persist");
    }

    private static void testReadingHistoryEndpointSupportsSearchAndProgressData() throws Exception {
        TestContext context = new TestContext();
        LocalDate today = LocalDate.now();
        Book historyBook = new Book("Reading Analytics", "author-a", "Author A", List.of("Science", "Technology"), "History summary.");
        historyBook.approve(today);
        context.bookRepository.save(historyBook);

        Book unrelatedBook = new Book("Other Title", "author-b", "Author B", List.of("History"), "Other summary.");
        unrelatedBook.approve(today);
        context.bookRepository.save(unrelatedBook);

        context.authService.registerStudentOrStaff("history-user", "History User", "Password1!", Role.STUDENT);
        BorrowRecord borrowed = context.borrowService.borrowBook("history-user", historyBook.getId(), 7);
        context.borrowService.borrowBook("history-user", unrelatedBook.getId(), 7);
        context.readingProgressService.updateProgress("history-user", historyBook.getId(), 12, List.of("chapter 1"));
        context.borrowService.returnBook("history-user", historyBook.getId());

        assertTrue(context.readingProgressService.findProgress("history-user", historyBook.getId()).isPresent(), "borrowing should create a reading-progress record");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "history-user", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/borrows/history?q=reading&author=Author+A&genre=Science&borrowDateFrom=" + today + "&borrowDateTo=" + today + "&returnDateFrom=" + today + "&returnDateTo=" + today + "&sortBy=title&sortDir=asc"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), "reading history request should return HTTP 200");
            assertTrue(response.body().contains("\"recordId\":\"" + borrowed.getId() + "\""), "reading history should include the borrowed record");
            assertTrue(response.body().contains("\"bookTitle\":\"Reading Analytics\""), "reading history should include the matching title");
            assertTrue(response.body().contains("\"authorFullName\":\"Author A\""), "reading history should include author details");
            assertTrue(response.body().contains("\"genres\":[\"Science\",\"Technology\"]"), "reading history should expose genres");
            assertTrue(response.body().contains("\"bookmarkPage\":12"), "reading history should include reading progress");
            assertTrue(response.body().contains("\"highlightCount\":1"), "reading history should include highlight count");
            assertFalse(response.body().contains("Other Title"), "reading history filters should exclude non-matching records");
        } finally {
            server.stop(0);
        }
    }

        private static void testReviewSubmissionAndBookRatingSummaries() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("review-feature", ".txt", List.of("Review feature content"));

        context.authorService.registerAuthor("review-author", "Review Author", "Password1!", "Writes reviewed books.");
        context.librarianService.registerLibrarian("review-lib", "Review Librarian", "Password1!", "EMP-REVIEW");
        BookSubmission2 submission = context.authorService.publishBook(
            "review-author",
            "Review Driven Design",
            List.of("Technology"),
            "Book for rating tests.",
            manuscript.toString()
        );
        context.librarianService.approveSubmission(submission.getId(), "Looks good.");

        Book reviewedBook = context.bookService.searchApprovedBooks("Review Driven Design").get(0);

        context.authService.registerStudentOrStaff("review-student-a", "Review Student A", "Password1!", Role.STUDENT);
        context.authService.registerStudentOrStaff("review-student-b", "Review Student B", "Password1!", Role.STUDENT);
        context.borrowService.borrowBook("review-student-a", reviewedBook.getId(), 7);
        context.borrowService.borrowBook("review-student-b", reviewedBook.getId(), 7);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();

            String studentASessionId = loginAndGetSessionId(client, baseUrl, "review-student-a", "Password1!", "STUDENT");
            String studentBSessionId = loginAndGetSessionId(client, baseUrl, "review-student-b", "Password1!", "STUDENT");
            String authorSessionId = loginAndGetSessionId(client, baseUrl, "review-author", "Password1!", "AUTHOR");
            String librarianSessionId = loginAndGetSessionId(client, baseUrl, "review-lib", "Password1!", "LIBRARIAN");

            HttpRequest saveReviewA = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", studentASessionId)
                .POST(HttpRequest.BodyPublishers.ofString("bookId=" + reviewedBook.getId() + "&rating=4&reviewText=Great+read"))
                .build();
            HttpResponse<String> saveReviewAResponse = client.send(saveReviewA, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, saveReviewAResponse.statusCode(), "first review submission should succeed");

            HttpRequest saveReviewB = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", studentBSessionId)
                .POST(HttpRequest.BodyPublishers.ofString("bookId=" + reviewedBook.getId() + "&rating=2&reviewText=Needs+improvement"))
                .build();
            HttpResponse<String> saveReviewBResponse = client.send(saveReviewB, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, saveReviewBResponse.statusCode(), "second review submission should succeed");

            HttpRequest reviewsForBookRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews?bookId=" + reviewedBook.getId()))
                .header("X-Session-Id", studentASessionId)
                .GET()
                .build();
            HttpResponse<String> reviewsForBookResponse = client.send(reviewsForBookRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, reviewsForBookResponse.statusCode(), "book review listing should succeed");
            assertTrue(reviewsForBookResponse.body().contains("\"reviewerFullName\":\"Review Student A\""), "book review listing should include reviewer name");

            HttpRequest myReviewsRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews/me"))
                .header("X-Session-Id", studentASessionId)
                .GET()
                .build();
            HttpResponse<String> myReviewsResponse = client.send(myReviewsRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, myReviewsResponse.statusCode(), "my reviews endpoint should succeed");
            assertTrue(myReviewsResponse.body().contains("\"bookId\":\"" + reviewedBook.getId() + "\""), "my reviews endpoint should include borrowed reviewed book");

            HttpRequest booksRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/books?keyword=Review+Driven+Design"))
                .header("X-Session-Id", studentASessionId)
                .GET()
                .build();
            HttpResponse<String> booksResponse = client.send(booksRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, booksResponse.statusCode(), "books endpoint should succeed with ratings data");
            assertTrue(booksResponse.body().contains("\"averageRating\":3.00"), "books endpoint should include computed average rating");
            assertTrue(booksResponse.body().contains("\"reviewCount\":2"), "books endpoint should include review count");

            HttpRequest authorBooksRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/author/published-books"))
                .header("X-Session-Id", authorSessionId)
                .GET()
                .build();
            HttpResponse<String> authorBooksResponse = client.send(authorBooksRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, authorBooksResponse.statusCode(), "author published books endpoint should succeed with ratings data");
            assertTrue(authorBooksResponse.body().contains("\"averageRating\":3.00"), "author published books should include average rating");
            assertTrue(authorBooksResponse.body().contains("\"reviewCount\":2"), "author published books should include review count");

            HttpRequest librarianBooksRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/librarian/approved-books"))
                .header("X-Session-Id", librarianSessionId)
                .GET()
                .build();
            HttpResponse<String> librarianBooksResponse = client.send(librarianBooksRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, librarianBooksResponse.statusCode(), "librarian approved books endpoint should succeed with ratings data");
            assertTrue(librarianBooksResponse.body().contains("\"averageRating\":3.00"), "librarian approved books should include average rating");
            assertTrue(librarianBooksResponse.body().contains("\"reviewCount\":2"), "librarian approved books should include review count");
        } finally {
            server.stop(0);
            Files.deleteIfExists(manuscript);
        }
        }

        // Optional/manual test for Section 1.9. Not run by default during final demo sprint
        // because review submission can trigger slow sentiment/LLM inference.
        private static void testReviewsCanBeMarkedHelpfulAndSorted() throws Exception {
        TestContext context = new TestContext();
        Book reviewedBook = context.addApprovedBook("Helpful Review Sorting", "Review Author", "Book for helpful sorting tests.");

        context.authService.registerStudentOrStaff("helpful-review-a", "Helpful Review A", "Password1!", Role.STUDENT);
        context.authService.registerStudentOrStaff("helpful-review-b", "Helpful Review B", "Password1!", Role.STUDENT);
        context.authService.registerStudentOrStaff("helpful-review-c", "Helpful Review C", "Password1!", Role.STUDENT);
        context.borrowService.borrowBook("helpful-review-a", reviewedBook.getId(), 7);
        context.borrowService.returnBook("helpful-review-a", reviewedBook.getId());
        context.borrowService.borrowBook("helpful-review-b", reviewedBook.getId(), 7);
        context.borrowService.returnBook("helpful-review-b", reviewedBook.getId());

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String reviewerASessionId = loginAndGetSessionId(client, baseUrl, "helpful-review-a", "Password1!", "STUDENT");
            String reviewerBSessionId = loginAndGetSessionId(client, baseUrl, "helpful-review-b", "Password1!", "STUDENT");
            String voterSessionId = loginAndGetSessionId(client, baseUrl, "helpful-review-c", "Password1!", "STUDENT");

            HttpResponse<String> reviewAResponse = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", reviewerASessionId)
                .POST(HttpRequest.BodyPublishers.ofString("bookId=" + reviewedBook.getId() + "&rating=3&reviewText=Solid+baseline"))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, reviewAResponse.statusCode(), "first helpful-sort review should submit");
            String reviewAId = extractJsonField(reviewAResponse.body(), "reviewId");

            HttpResponse<String> reviewBResponse = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", reviewerBSessionId)
                .POST(HttpRequest.BodyPublishers.ofString("bookId=" + reviewedBook.getId() + "&rating=5&reviewText=Most+useful+review"))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, reviewBResponse.statusCode(), "second helpful-sort review should submit");
            String reviewBId = extractJsonField(reviewBResponse.body(), "reviewId");

            HttpResponse<String> helpfulResponse = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews/helpful"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", voterSessionId)
                .POST(HttpRequest.BodyPublishers.ofString("reviewId=" + reviewBId))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, helpfulResponse.statusCode(), "helpful endpoint should accept first vote");
            assertTrue(helpfulResponse.body().contains("\"helpfulCount\":1"), "helpful response should include count");
            assertTrue(helpfulResponse.body().contains("\"helpfulByViewer\":true"), "helpful response should mark viewer vote");

            HttpResponse<String> duplicateHelpfulResponse = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews/helpful"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", voterSessionId)
                .POST(HttpRequest.BodyPublishers.ofString("reviewId=" + reviewBId))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, duplicateHelpfulResponse.statusCode(), "duplicate helpful vote should be rejected");
            assertTrue(duplicateHelpfulResponse.body().contains("already marked"), "duplicate response should explain dedup");

            HttpResponse<String> selfHelpfulResponse = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews/helpful"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", reviewerBSessionId)
                .POST(HttpRequest.BodyPublishers.ofString("reviewId=" + reviewBId))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, selfHelpfulResponse.statusCode(), "self helpful vote should be rejected");
            assertTrue(selfHelpfulResponse.body().contains("own review"), "self response should explain ownership rule");

            HttpResponse<String> sortedResponse = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/reviews?bookId=" + reviewedBook.getId() + "&sortBy=helpful"))
                .header("X-Session-Id", voterSessionId)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, sortedResponse.statusCode(), "helpful sorted review listing should succeed");
            assertTrue(sortedResponse.body().contains("\"helpfulCount\":1"), "sorted response should include helpful count");
            assertTrue(sortedResponse.body().contains("\"helpfulByViewer\":true"), "sorted response should include viewer helpful flag");
            assertTrue(sortedResponse.body().indexOf(reviewBId) >= 0, "sorted response should include helpful review");
            assertTrue(sortedResponse.body().indexOf(reviewAId) >= 0, "sorted response should include unhelpful review");
            assertTrue(sortedResponse.body().indexOf(reviewBId) < sortedResponse.body().indexOf(reviewAId), "helpful review should sort before unhelpful review");
        } finally {
            server.stop(0);
        }
        }

        private static void testBookRequestSubmissionAndLibrarianUploadFlow() throws Exception {
        TestContext context = new TestContext();

        context.authService.registerStudentOrStaff("request-student", "Request Student", "Password1!", Role.STUDENT);
        context.librarianService.registerLibrarian("request-lib", "Request Librarian", "Password1!", "EMP-RQ");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String studentSessionId = loginAndGetSessionId(client, baseUrl, "request-student", "Password1!", "STUDENT");
            String librarianSessionId = loginAndGetSessionId(client, baseUrl, "request-lib", "Password1!", "LIBRARIAN");

            HttpRequest submitRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/book-requests"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", studentSessionId)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "title=The+Structured+Library&authorName=Design+Author&genres=Technology,Education&reason=Need+this+book+for+the+collection"
                ))
                .build();
            HttpResponse<String> submitResponse = client.send(submitRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, submitResponse.statusCode(), "book request submission should succeed");
            assertTrue(submitResponse.body().contains("Book request submitted successfully"), "submission response should confirm success");

            List<BookRequest2> requests = context.bookRequestService.listRequestsByRequester("request-student");
            assertEquals(1, requests.size(), "request should be stored for the requester");
            BookRequest2 request = requests.get(0);

            HttpRequest librarianQueueRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/librarian/book-requests?status=pending"))
                .header("X-Session-Id", librarianSessionId)
                .GET()
                .build();
            HttpResponse<String> queueResponse = client.send(librarianQueueRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, queueResponse.statusCode(), "librarian request queue should load");
            assertTrue(queueResponse.body().contains("The Structured Library"), "librarian queue should include the submitted request");

            HttpRequest approveRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/librarian/book-request/review"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", librarianSessionId)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "requestId=" + request.getId() + "&action=approve&comment=Looks+useful"
                ))
                .build();
            HttpResponse<String> approveResponse = client.send(approveRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, approveResponse.statusCode(), "book request approval should succeed");

            List<NotificationItem> studentNotificationsAfterApprove = context.notificationService.listByUser("request-student");
            assertTrue(studentNotificationsAfterApprove.stream().anyMatch(item -> "Book Request Approved".equals(item.getTitle())), "approval should notify the requester");

            HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/librarian/book-request/review"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", librarianSessionId)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "requestId=" + request.getId() + "&action=upload&comment=Uploaded+to+catalog"
                ))
                .build();
            HttpResponse<String> uploadResponse = client.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, uploadResponse.statusCode(), "book request upload should succeed after approval");

            assertEquals(1, context.bookService.searchApprovedBooks("The Structured Library").size(), "uploaded request should create an approved book");
            assertTrue(context.notificationService.listByUser("request-student").stream().anyMatch(item ->
                "Requested Book Uploaded".equals(item.getTitle()) && item.getPriority() == NotificationPriority.HIGH),
                "upload should send a high-priority notification");
        } finally {
            server.stop(0);
        }
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
        assertEquals("NORMAL", updated.getPriority().name(), "default notification priority should be NORMAL");
        assertTrue(updated.getReadAt() != null, "read timestamp should be set after marking read");
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

        expectThrows(BusinessException.class,
            () -> context.notificationService.deleteNotification("owner-b", foreignItem.getId()),
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

    private static void testAuthorOwnerCanUpdatePublishedBookMetadata() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("published-update", ".txt", List.of("v1"));

        context.authorService.registerAuthor("author-pub-update", "Author Pub Update", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
                "author-pub-update",
                "Published Original",
                List.of("Technology"),
                "Original Description",
                manuscript.toString()
        );
        context.librarianService.registerLibrarian("lib-pub-update", "Lib Pub Update", "Password1!", "EMP-PU");
        context.librarianService.approveSubmission(submission.getId(), "approved");

        Book published = context.bookService.listApprovedBooksByAuthorUsername("author-pub-update").get(0);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-pub-update", "Password1!", "AUTHOR");

            HttpRequest updateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/published-book/update"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", authorSession)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "bookId=" + published.getId()
                                    + "&title=Published+Updated"
                                    + "&genres=Science,Technology"
                                    + "&description=Updated+Description"
                    ))
                    .build();
            HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, updateResponse.statusCode(), "owner update published book should return HTTP 200");
            assertTrue(updateResponse.body().contains("Published book updated"), "response should confirm published book update");

            Book updated = context.bookRepository.findById(published.getId())
                    .orElseThrow(() -> new AssertionError("expected published book to exist"));
            assertEquals("Published Updated", updated.getTitle(), "published title should update");
            assertEquals("Updated Description", updated.getSummary(), "published description should update");
            assertEquals(List.of("Science", "Technology"), updated.getGenres(), "published genres should update and normalize");
        } finally {
            server.stop(0);
            Files.deleteIfExists(manuscript);
        }
    }

    private static void testAuthorCannotUpdatePublishedBookWithActiveBorrows() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("published-update-borrowed", ".txt", List.of("v1"));

        context.authorService.registerAuthor("author-pub-upd-borrow", "Author Upd Borrow", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
                "author-pub-upd-borrow",
                "Borrowed Original",
                List.of("Technology"),
                "Borrowed Description",
                manuscript.toString()
        );
        context.librarianService.registerLibrarian("lib-pub-upd-borrow", "Lib Upd Borrow", "Password1!", "EMP-UB");
        context.librarianService.approveSubmission(submission.getId(), "approved");
        Book published = context.bookService.listApprovedBooksByAuthorUsername("author-pub-upd-borrow").get(0);

        context.authService.registerStudentOrStaff("stu-upd-borrow", "Student Upd Borrow", "Password1!", Role.STUDENT);
        context.borrowService.borrowBook("stu-upd-borrow", published.getId(), 7);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-pub-upd-borrow", "Password1!", "AUTHOR");

            HttpRequest updateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/published-book/update"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", authorSession)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "bookId=" + published.getId()
                                    + "&title=Should+Not+Change"
                                    + "&genres=Technology"
                                    + "&description=Should+Not+Change+Either"
                    ))
                    .build();
            HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, updateResponse.statusCode(), "update with active borrows should be rejected with HTTP 400");
            assertTrue(updateResponse.body().toLowerCase().contains("active borrows"),
                    "response body should mention active borrows: " + updateResponse.body());

            Book unchanged = context.bookRepository.findById(published.getId())
                    .orElseThrow(() -> new AssertionError("expected published book to exist"));
            assertEquals("Borrowed Original", unchanged.getTitle(), "title must not be modified when active borrows exist");
            assertEquals("Borrowed Description", unchanged.getSummary(), "description must not be modified when active borrows exist");
        } finally {
            server.stop(0);
            Files.deleteIfExists(manuscript);
        }
    }

    private static void testAuthorCanUpdatePublishedBookAfterAllBorrowsReturned() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("published-update-returned", ".txt", List.of("v1"));

        context.authorService.registerAuthor("author-pub-upd-ret", "Author Upd Ret", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
                "author-pub-upd-ret",
                "Returned Original",
                List.of("Technology"),
                "Returned Description",
                manuscript.toString()
        );
        context.librarianService.registerLibrarian("lib-pub-upd-ret", "Lib Upd Ret", "Password1!", "EMP-UR");
        context.librarianService.approveSubmission(submission.getId(), "approved");
        Book published = context.bookService.listApprovedBooksByAuthorUsername("author-pub-upd-ret").get(0);

        context.authService.registerStudentOrStaff("stu-upd-ret", "Student Upd Ret", "Password1!", Role.STUDENT);
        context.borrowService.borrowBook("stu-upd-ret", published.getId(), 7);
        context.borrowService.returnBook("stu-upd-ret", published.getId());

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-pub-upd-ret", "Password1!", "AUTHOR");

            HttpRequest updateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/published-book/update"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", authorSession)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "bookId=" + published.getId()
                                    + "&title=Returned+Updated"
                                    + "&genres=Technology"
                                    + "&description=Returned+Updated+Description"
                    ))
                    .build();
            HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, updateResponse.statusCode(), "update after all returns should succeed with HTTP 200");

            Book updated = context.bookRepository.findById(published.getId())
                    .orElseThrow(() -> new AssertionError("expected published book to exist"));
            assertEquals("Returned Updated", updated.getTitle(), "title should update after all borrows returned");
            assertEquals("Returned Updated Description", updated.getSummary(), "description should update after all borrows returned");
        } finally {
            server.stop(0);
            Files.deleteIfExists(manuscript);
        }
    }

    private static void testAuthorOwnerCanDeletePublishedBookWithoutActiveBorrows() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("published-delete", ".txt", List.of("v1"));

        context.authorService.registerAuthor("author-pub-delete", "Author Pub Delete", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
                "author-pub-delete",
                "Published Delete Me",
                List.of("Technology"),
                "Delete Description",
                manuscript.toString()
        );
        context.librarianService.registerLibrarian("lib-pub-delete", "Lib Pub Delete", "Password1!", "EMP-PD");
        context.librarianService.approveSubmission(submission.getId(), "approved");

        Book published = context.bookService.listApprovedBooksByAuthorUsername("author-pub-delete").get(0);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-pub-delete", "Password1!", "AUTHOR");

            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/published-book/delete"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", authorSession)
                    .POST(HttpRequest.BodyPublishers.ofString("bookId=" + published.getId()))
                    .build();
            HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, deleteResponse.statusCode(), "owner delete published book should return HTTP 200");
            assertTrue(deleteResponse.body().contains("Published book deleted"), "response should confirm published book deletion");

            assertTrue(context.bookRepository.findById(published.getId()).isEmpty(), "deleted published book should be removed from repository");
        } finally {
            server.stop(0);
            Files.deleteIfExists(manuscript);
        }
    }

    private static void testAuthorNonOwnerCannotUpdateOrDeletePublishedBook() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("published-boundary", ".txt", List.of("v1"));

        context.authorService.registerAuthor("author-pub-owner", "Author Pub Owner", "Password1!", "Bio");
        context.authorService.registerAuthor("author-pub-other", "Author Pub Other", "Password1!", "Bio");

        BookSubmission2 submission = context.authorService.publishBook(
                "author-pub-owner",
                "Owner Published",
                List.of("Technology"),
                "Owner Description",
                manuscript.toString()
        );
        context.librarianService.registerLibrarian("lib-pub-boundary", "Lib Pub Boundary", "Password1!", "EMP-PB");
        context.librarianService.approveSubmission(submission.getId(), "approved");
        Book published = context.bookService.listApprovedBooksByAuthorUsername("author-pub-owner").get(0);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String otherSession = loginAndGetSessionId(client, baseUrl, "author-pub-other", "Password1!", "AUTHOR");

            HttpRequest updateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/published-book/update"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", otherSession)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "bookId=" + published.getId()
                                    + "&title=Hacked+Title"
                                    + "&genres=Science"
                                    + "&description=Hacked+Description"
                    ))
                    .build();
            HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, updateResponse.statusCode(), "non-owner update published book should be rejected");
            assertTrue(updateResponse.body().contains("Cannot update another author's published book."), "non-owner update should explain ownership boundary");

            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/published-book/delete"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", otherSession)
                    .POST(HttpRequest.BodyPublishers.ofString("bookId=" + published.getId()))
                    .build();
            HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, deleteResponse.statusCode(), "non-owner delete published book should be rejected");
            assertTrue(deleteResponse.body().contains("Cannot delete another author's published book."), "non-owner delete should explain ownership boundary");
        } finally {
            server.stop(0);
            Files.deleteIfExists(manuscript);
        }
    }

    private static void testAuthorDeletePublishedBookBlockedWhenActiveBorrowsExist() throws Exception {
        TestContext context = new TestContext();
        Path manuscript = createTempTextFile("published-active-borrow", ".txt", List.of("v1"));

        context.authorService.registerAuthor("author-pub-active", "Author Pub Active", "Password1!", "Bio");
        context.authService.registerStudentOrStaff("reader-active", "Reader Active", "Password1!", Role.STUDENT);

        BookSubmission2 submission = context.authorService.publishBook(
                "author-pub-active",
                "Published Borrowed",
                List.of("Technology"),
                "Borrowed Description",
                manuscript.toString()
        );
        context.librarianService.registerLibrarian("lib-pub-active", "Lib Pub Active", "Password1!", "EMP-PA");
        context.librarianService.approveSubmission(submission.getId(), "approved");
        Book published = context.bookService.listApprovedBooksByAuthorUsername("author-pub-active").get(0);

        context.borrowService.borrowBook("reader-active", published.getId(), 7);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-pub-active", "Password1!", "AUTHOR");

            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/published-book/delete"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", authorSession)
                    .POST(HttpRequest.BodyPublishers.ofString("bookId=" + published.getId()))
                    .build();
            HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, deleteResponse.statusCode(), "delete should be blocked when active borrows exist");
            assertTrue(deleteResponse.body().contains("Cannot delete a published book with active borrows."), "delete-block response should explain active borrow restriction");
            assertTrue(context.bookRepository.findById(published.getId()).isPresent(), "published book should remain after blocked delete");
        } finally {
            server.stop(0);
            Files.deleteIfExists(manuscript);
        }
    }

    private static void testAuthorBulkDeletePendingSubmissionsPartialSuccess() throws Exception {
        TestContext context = new TestContext();
        Path mOne = createTempTextFile("bulk-pending-one", ".txt", List.of("v1"));
        Path mTwo = createTempTextFile("bulk-pending-two", ".txt", List.of("v1"));
        Path mThree = createTempTextFile("bulk-pending-three", ".txt", List.of("v1"));
        Path mFour = createTempTextFile("bulk-pending-four", ".txt", List.of("v1"));

        context.authorService.registerAuthor("author-bulk-a", "Author Bulk A", "Password1!", "Bio");
        context.authorService.registerAuthor("author-bulk-b", "Author Bulk B", "Password1!", "Bio");
        context.librarianService.registerLibrarian("lib-bulk", "Lib Bulk", "Password1!", "EMP-BULK");

        BookSubmission2 pendingOne = context.authorService.publishBook(
                "author-bulk-a", "Pending One", List.of("Technology"), "Desc1", mOne.toString());
        BookSubmission2 pendingTwo = context.authorService.publishBook(
                "author-bulk-a", "Pending Two", List.of("Technology"), "Desc2", mTwo.toString());
        BookSubmission2 approvedThree = context.authorService.publishBook(
                "author-bulk-a", "Approved Three", List.of("Technology"), "Desc3", mThree.toString());
        BookSubmission2 foreignFour = context.authorService.publishBook(
                "author-bulk-b", "Foreign Four", List.of("Technology"), "Desc4", mFour.toString());

        context.librarianService.approveSubmission(approvedThree.getId(), "ok");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-bulk-a", "Password1!", "AUTHOR");

            String csv = pendingOne.getId() + "," + pendingTwo.getId() + "," + approvedThree.getId()
                    + "," + foreignFour.getId() + ",bogus-five";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/submission/bulk-delete"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", authorSession)
                    .POST(HttpRequest.BodyPublishers.ofString("submissionIds=" + csv))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "bulk-delete should return HTTP 200");
            String body = resp.body();
            assertTrue(body.contains("\"deletedCount\":2"), "deletedCount should be 2: " + body);
            assertTrue(body.contains("\"skippedCount\":3"), "skippedCount should be 3: " + body);
            assertTrue(body.contains(pendingOne.getId()), "deletedIds should contain pendingOne");
            assertTrue(body.contains(pendingTwo.getId()), "deletedIds should contain pendingTwo");
            assertTrue(body.toLowerCase().contains("pending"), "skip reason for approved should mention 'pending': " + body);
            assertTrue(body.toLowerCase().contains("another author"),
                    "skip reason for foreign should mention 'another author': " + body);
            assertTrue(body.contains("bogus-five"), "skipped should include bogus-five");

            assertTrue(context.submissionRepository.findById(pendingOne.getId()).isEmpty(), "pendingOne should be deleted");
            assertTrue(context.submissionRepository.findById(pendingTwo.getId()).isEmpty(), "pendingTwo should be deleted");
            assertTrue(context.submissionRepository.findById(approvedThree.getId()).isPresent(), "approvedThree should remain");
            assertTrue(context.submissionRepository.findById(foreignFour.getId()).isPresent(), "foreignFour should remain");
        } finally {
            server.stop(0);
            Files.deleteIfExists(mOne);
            Files.deleteIfExists(mTwo);
            Files.deleteIfExists(mThree);
            Files.deleteIfExists(mFour);
        }
    }

    private static void testAuthorBulkDeletePublishedBooksSkipsActiveBorrowsAndFiresNotification() throws Exception {
        TestContext context = new TestContext();
        Path mFree = createTempTextFile("bulk-pub-free", ".txt", List.of("v1"));
        Path mBorrowed = createTempTextFile("bulk-pub-borrowed", ".txt", List.of("v1"));

        context.authorService.registerAuthor("author-bulk-pub", "Author Bulk Pub", "Password1!", "Bio");
        context.authService.registerStudentOrStaff("stu-bulk-1", "Stu Bulk 1", "Password1!", Role.STUDENT);
        context.authService.registerStudentOrStaff("stu-bulk-2", "Stu Bulk 2", "Password1!", Role.STUDENT);
        context.librarianService.registerLibrarian("lib-bulk-pub", "Lib Bulk Pub", "Password1!", "EMP-BPUB");

        BookSubmission2 subFree = context.authorService.publishBook(
                "author-bulk-pub", "Published Free", List.of("Technology"), "DescFree", mFree.toString());
        BookSubmission2 subBorrowed = context.authorService.publishBook(
                "author-bulk-pub", "Published Borrowed", List.of("Technology"), "DescBorrowed", mBorrowed.toString());
        context.librarianService.approveSubmission(subFree.getId(), "ok");
        context.librarianService.approveSubmission(subBorrowed.getId(), "ok");

        List<Book> approved = context.bookService.listApprovedBooksByAuthorUsername("author-bulk-pub");
        Book publishedFree = approved.stream().filter(b -> "Published Free".equals(b.getTitle())).findFirst().orElseThrow();
        Book publishedBorrowed = approved.stream().filter(b -> "Published Borrowed".equals(b.getTitle())).findFirst().orElseThrow();

        context.borrowService.borrowBook("stu-bulk-1", publishedBorrowed.getId(), 7);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-bulk-pub", "Password1!", "AUTHOR");

            String csv = publishedFree.getId() + "," + publishedBorrowed.getId();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/published-book/bulk-delete"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", authorSession)
                    .POST(HttpRequest.BodyPublishers.ofString("bookIds=" + csv))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "bulk-delete should return HTTP 200");
            String body = resp.body();
            assertTrue(body.contains("\"deletedCount\":1"), "deletedCount should be 1: " + body);
            assertTrue(body.contains("\"skippedCount\":1"), "skippedCount should be 1: " + body);
            assertTrue(body.contains(publishedFree.getId()), "deletedIds should contain publishedFree");
            assertTrue(body.contains(publishedBorrowed.getId()), "skipped should include publishedBorrowed");
            assertTrue(body.toLowerCase().contains("active borrows"),
                    "skip reason should mention 'active borrows': " + body);

            assertTrue(context.bookRepository.findById(publishedFree.getId()).isEmpty(),
                    "publishedFree should be deleted");
            assertTrue(context.bookRepository.findById(publishedBorrowed.getId()).isPresent(),
                    "publishedBorrowed should remain (active borrow)");

            // No 'book-deleted' notification should fire for stu-bulk-1 — the only deleted book
            // (publishedFree) had no borrowers; the borrowed one was skipped.
            long stuOneBookDeletedNotifs = context.notificationService.listByUser("stu-bulk-1").stream()
                    .filter(n -> "book-deleted".equals(n.getMetadata().get("type")))
                    .count();
            assertEquals(0L, stuOneBookDeletedNotifs,
                    "stu-bulk-1 should receive no book-deleted notification (skipped book has active borrow)");

            // Specifically, no notification for the SKIPPED publishedBorrowed.
            boolean skippedFiredForBorrowed = context.notificationService.listByUser("stu-bulk-1").stream()
                    .anyMatch(n -> "book-deleted".equals(n.getMetadata().get("type"))
                            && publishedBorrowed.getId().equals(n.getMetadata().get("bookId")));
            assertTrue(!skippedFiredForBorrowed,
                    "no book-deleted notification should fire for the skipped (active-borrow) book");

            // stu-bulk-2 had no borrows of either book — also no book-deleted notifications.
            long stuTwoBookDeletedNotifs = context.notificationService.listByUser("stu-bulk-2").stream()
                    .filter(n -> "book-deleted".equals(n.getMetadata().get("type")))
                    .count();
            assertEquals(0L, stuTwoBookDeletedNotifs,
                    "stu-bulk-2 should receive no book-deleted notification (no borrows)");
        } finally {
            server.stop(0);
            Files.deleteIfExists(mFree);
            Files.deleteIfExists(mBorrowed);
        }
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

        private static void testAuthorOwnerCanUpdatePendingSubmission() throws Exception {
        TestContext context = new TestContext();
        Path file = createTempTextFile("author-update-own", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-update-own", "Author Update Own", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
            "author-update-own",
            "Original Pending Title",
            List.of("Technology"),
            "Original Description",
            file.toString()
        );

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-update-own", "Password1!", "AUTHOR");

            HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/author/submission/update"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", authorSession)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "submissionId=" + submission.getId()
                        + "&title=Updated+Pending+Title"
                        + "&genres=Science"
                        + "&description=Updated+Description"
                        + "&filePath=" + file
                ))
                .build();
            HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, updateResponse.statusCode(), "owner should be able to update pending submission");
            assertTrue(updateResponse.body().contains("Submission updated"), "update response should confirm success");

            BookSubmission2 updated = context.submissionRepository.findById(submission.getId())
                .orElseThrow(() -> new AssertionError("submission should still exist after update"));
            assertEquals("Updated Pending Title", updated.getTitle(), "pending submission title should update");
            assertEquals(List.of("Science"), updated.getGenres(), "pending submission genres should update");
            assertEquals("Updated Description", updated.getDescription(), "pending submission description should update");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
        }
        }

        private static void testAuthorOwnerCanDeletePendingSubmission() throws Exception {
        TestContext context = new TestContext();
        Path file = createTempTextFile("author-delete-own", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-delete-own", "Author Delete Own", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
            "author-delete-own",
            "Pending Delete Title",
            List.of("Technology"),
            "Delete Description",
            file.toString()
        );

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-delete-own", "Password1!", "AUTHOR");

            HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/author/submission/delete"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", authorSession)
                .POST(HttpRequest.BodyPublishers.ofString("submissionId=" + submission.getId()))
                .build();
            HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, deleteResponse.statusCode(), "owner should be able to delete pending submission");
            assertTrue(deleteResponse.body().contains("Submission deleted"), "delete response should confirm success");

            assertTrue(context.submissionRepository.findById(submission.getId()).isEmpty(), "pending submission should be removed after owner delete");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
        }
        }

        private static void testAuthorNonOwnerCannotUpdateOrDeleteSubmission() throws Exception {
        TestContext context = new TestContext();
        Path file = createTempTextFile("author-non-owner", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-owner", "Author Owner", "Password1!", "Bio");
        context.authorService.registerAuthor("author-other", "Author Other", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
            "author-owner",
            "Owner Pending Title",
            List.of("Technology"),
            "Owner Description",
            file.toString()
        );

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String nonOwnerSession = loginAndGetSessionId(client, baseUrl, "author-other", "Password1!", "AUTHOR");

            HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/author/submission/update"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", nonOwnerSession)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "submissionId=" + submission.getId()
                        + "&title=Hacked"
                        + "&genres=Science"
                        + "&description=Hacked"
                        + "&filePath=" + file
                ))
                .build();
            HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, updateResponse.statusCode(), "non-owner update should be rejected");
            assertTrue(updateResponse.body().contains("Cannot update another author's submission."), "update rejection should explain ownership boundary");

            HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/author/submission/delete"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", nonOwnerSession)
                .POST(HttpRequest.BodyPublishers.ofString("submissionId=" + submission.getId()))
                .build();
            HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, deleteResponse.statusCode(), "non-owner delete should be rejected");
            assertTrue(deleteResponse.body().contains("Cannot delete another author's submission."), "delete rejection should explain ownership boundary");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
        }
        }

        private static void testApprovedOrRejectedSubmissionCannotBeUpdatedOrDeleted() throws Exception {
        TestContext context = new TestContext();
        Path approvedFile = createTempTextFile("author-approved-lock", ".txt", List.of("content"));
        Path rejectedFile = createTempTextFile("author-rejected-lock", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-locked", "Author Locked", "Password1!", "Bio");
        context.librarianService.registerLibrarian("lib-locked", "Lib Locked", "Password1!", "EMP-LOCKED");

        BookSubmission2 approvedSubmission = context.authorService.publishBook(
            "author-locked",
            "Approved Lock Title",
            List.of("Technology"),
            "Approved lock description",
            approvedFile.toString()
        );
        BookSubmission2 rejectedSubmission = context.authorService.publishBook(
            "author-locked",
            "Rejected Lock Title",
            List.of("Technology"),
            "Rejected lock description",
            rejectedFile.toString()
        );

        context.librarianService.approveSubmission(approvedSubmission.getId(), "Approved");
        context.librarianService.rejectSubmission(rejectedSubmission.getId(), "Rejected");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-locked", "Password1!", "AUTHOR");

            HttpRequest updateApprovedRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/author/submission/update"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", authorSession)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "submissionId=" + approvedSubmission.getId()
                        + "&title=Should+Not+Update"
                        + "&genres=Science"
                        + "&description=Should+Not+Update"
                        + "&filePath=" + approvedFile
                ))
                .build();
            HttpResponse<String> updateApprovedResponse = client.send(updateApprovedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, updateApprovedResponse.statusCode(), "approved submission update should be rejected");
            assertTrue(updateApprovedResponse.body().contains("Only pending submissions can be updated."), "approved update rejection should explain pending-only policy");

            HttpRequest deleteRejectedRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/author/submission/delete"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Session-Id", authorSession)
                .POST(HttpRequest.BodyPublishers.ofString("submissionId=" + rejectedSubmission.getId()))
                .build();
            HttpResponse<String> deleteRejectedResponse = client.send(deleteRejectedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, deleteRejectedResponse.statusCode(), "rejected submission delete should be rejected");
            assertTrue(deleteRejectedResponse.body().contains("Only pending submissions can be deleted."), "rejected delete rejection should explain pending-only policy");
        } finally {
            server.stop(0);
            Files.deleteIfExists(approvedFile);
            Files.deleteIfExists(rejectedFile);
        }
        }

    private static void testAuthorOwnerCanReadOwnPendingSubmissionFilePreview() throws Exception {
        TestContext context = new TestContext();
        Path file = createTempTextFile("author-read-own", ".txt", List.of("line-1", "line-2"));

        context.authorService.registerAuthor("author-read-own", "Author Read Own", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
                "author-read-own",
                "Readable Pending",
                List.of("Technology"),
                "Description",
                file.toString()
        );

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-read-own", "Password1!", "AUTHOR");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/submission/read?submissionId=" + submission.getId()))
                    .header("X-Session-Id", authorSession)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "owner read submission preview should return HTTP 200");
            assertTrue(response.body().contains("\"itemId\":\"" + submission.getId() + "\""), "response should include submission id");
            assertTrue(response.body().contains("\"sourceType\":\"submission\""), "response should include submission source type");
            assertTrue(response.body().contains("line-1"), "response should include text preview content");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
        }
    }

    private static void testAuthorNonOwnerCannotReadSubmissionFilePreview() throws Exception {
        TestContext context = new TestContext();
        Path file = createTempTextFile("author-read-non-owner", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-read-owner", "Author Read Owner", "Password1!", "Bio");
        context.authorService.registerAuthor("author-read-other", "Author Read Other", "Password1!", "Bio");
        BookSubmission2 submission = context.authorService.publishBook(
                "author-read-owner",
                "Owner Only Read",
                List.of("Technology"),
                "Description",
                file.toString()
        );

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String nonOwnerSession = loginAndGetSessionId(client, baseUrl, "author-read-other", "Password1!", "AUTHOR");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/submission/read?submissionId=" + submission.getId()))
                    .header("X-Session-Id", nonOwnerSession)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode(), "non-owner read submission preview should be rejected");
            assertTrue(response.body().contains("Cannot read another author's submission."), "ownership rejection should be clear for read endpoint");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
        }
    }

    private static void testAuthorReadSubmissionRejectsInvalidSubmissionId() throws Exception {
        TestContext context = new TestContext();
        context.authorService.registerAuthor("author-read-invalid", "Author Read Invalid", "Password1!", "Bio");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-read-invalid", "Password1!", "AUTHOR");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/submission/read?submissionId=missing-id"))
                    .header("X-Session-Id", authorSession)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode(), "invalid submission id should be rejected");
            assertTrue(response.body().contains("Submission not found."), "invalid id rejection should explain not found");
        } finally {
            server.stop(0);
        }
    }

    private static void testAuthorReadSubmissionHandlesMissingFileSafely() throws Exception {
        TestContext context = new TestContext();
        context.authorService.registerAuthor("author-read-missing", "Author Read Missing", "Password1!", "Bio");

        BookSubmission2 submission = context.authorService.publishBook(
                "author-read-missing",
                "Missing File Submission",
                List.of("Technology"),
                "Description",
                "/tmp/non-existent-preview-file.txt"
        );

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String authorSession = loginAndGetSessionId(client, baseUrl, "author-read-missing", "Password1!", "AUTHOR");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/submission/read?submissionId=" + submission.getId()))
                    .header("X-Session-Id", authorSession)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode(), "missing file should be rejected safely");
            assertTrue(response.body().contains("Uploaded file does not exist"), "missing-file response should be clear and safe");
        } finally {
            server.stop(0);
        }
    }

    private static void testAuthorDeleteRuleMessagesRemainConsistent() throws Exception {
        TestContext context = new TestContext();
        Path ownedFile = createTempTextFile("author-delete-consistent-owned", ".txt", List.of("content"));
        Path foreignFile = createTempTextFile("author-delete-consistent-foreign", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-delete-consistent-owner", "Owner", "Password1!", "Bio");
        context.authorService.registerAuthor("author-delete-consistent-other", "Other", "Password1!", "Bio");
        context.librarianService.registerLibrarian("lib-delete-consistent", "Lib", "Password1!", "EMP-DEL-C");

        BookSubmission2 ownerSubmission = context.authorService.publishBook(
                "author-delete-consistent-owner",
                "Owner Submission",
                List.of("Technology"),
                "Description",
                ownedFile.toString()
        );
        BookSubmission2 foreignSubmission = context.authorService.publishBook(
                "author-delete-consistent-other",
                "Foreign Submission",
                List.of("Technology"),
                "Description",
                foreignFile.toString()
        );
        context.librarianService.approveSubmission(ownerSubmission.getId(), "Approved");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String ownerSession = loginAndGetSessionId(client, baseUrl, "author-delete-consistent-owner", "Password1!", "AUTHOR");

            HttpRequest deleteApprovedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/submission/delete"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", ownerSession)
                    .POST(HttpRequest.BodyPublishers.ofString("submissionId=" + ownerSubmission.getId()))
                    .build();
            HttpResponse<String> deleteApprovedResponse = client.send(deleteApprovedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, deleteApprovedResponse.statusCode(), "approved submission delete should be blocked");
            assertTrue(deleteApprovedResponse.body().contains("Only pending submissions can be deleted."), "approved-delete message should stay consistent");

            HttpRequest deleteForeignRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/author/submission/delete"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", ownerSession)
                    .POST(HttpRequest.BodyPublishers.ofString("submissionId=" + foreignSubmission.getId()))
                    .build();
            HttpResponse<String> deleteForeignResponse = client.send(deleteForeignRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, deleteForeignResponse.statusCode(), "foreign submission delete should be blocked");
            assertTrue(deleteForeignResponse.body().contains("Cannot delete another author's submission."), "foreign-delete message should stay consistent");
        } finally {
            server.stop(0);
            Files.deleteIfExists(ownedFile);
            Files.deleteIfExists(foreignFile);
        }
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

    private static void testLibrarianPendingQueueKeywordSearchByTitle() throws Exception {
        TestContext context = new TestContext();
        Path titleA = createTempTextFile("queue-title-a", ".txt", List.of("A"));
        Path titleB = createTempTextFile("queue-title-b", ".txt", List.of("B"));

        context.authorService.registerAuthor("queue-title-author-a", "Queue Title Author A", "Password1!", "Bio");
        context.authorService.registerAuthor("queue-title-author-b", "Queue Title Author B", "Password1!", "Bio");
        context.authorService.publishBook("queue-title-author-a", "Neural Systems Design", List.of("Technology"), "Desc", titleA.toString());
        context.authorService.publishBook("queue-title-author-b", "Classic Literature", List.of("Technology"), "Desc", titleB.toString());
        context.librarianService.registerLibrarian("queue-lib-title", "Queue Lib Title", "Password1!", "EMP-QT");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String librarianSession = loginAndGetSessionId(client, baseUrl, "queue-lib-title", "Password1!", "LIBRARIAN");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending?q=neural"))
                    .header("X-Session-Id", librarianSession)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "keyword title search should return HTTP 200");
            assertTrue(response.body().contains("\"title\":\"Neural Systems Design\""), "title keyword search should include matching submission");
            assertFalse(response.body().contains("\"title\":\"Classic Literature\""), "title keyword search should exclude non-matching submission");
        } finally {
            server.stop(0);
            Files.deleteIfExists(titleA);
            Files.deleteIfExists(titleB);
        }
    }

    private static void testLibrarianPendingQueueKeywordSearchByAuthor() throws Exception {
        TestContext context = new TestContext();
        Path authorAFile = createTempTextFile("queue-author-a", ".txt", List.of("A"));
        Path authorBFile = createTempTextFile("queue-author-b", ".txt", List.of("B"));

        context.authorService.registerAuthor("queue-author-a", "Alice Queue", "Password1!", "Bio");
        context.authorService.registerAuthor("queue-author-b", "Bob Queue", "Password1!", "Bio");
        context.authorService.publishBook("queue-author-a", "Queue Book A", List.of("Technology"), "Desc", authorAFile.toString());
        context.authorService.publishBook("queue-author-b", "Queue Book B", List.of("Technology"), "Desc", authorBFile.toString());
        context.librarianService.registerLibrarian("queue-lib-author", "Queue Lib Author", "Password1!", "EMP-QA");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String librarianSession = loginAndGetSessionId(client, baseUrl, "queue-lib-author", "Password1!", "LIBRARIAN");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending?q=alice"))
                    .header("X-Session-Id", librarianSession)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "keyword author search should return HTTP 200");
            assertTrue(response.body().contains("\"authorFullName\":\"Alice Queue\""), "author keyword search should include matching author");
            assertFalse(response.body().contains("\"authorFullName\":\"Bob Queue\""), "author keyword search should exclude non-matching author");
        } finally {
            server.stop(0);
            Files.deleteIfExists(authorAFile);
            Files.deleteIfExists(authorBFile);
        }
    }

    private static void testLibrarianPendingQueueStatusFilter() throws Exception {
        TestContext context = new TestContext();
        Path pendingFile = createTempTextFile("queue-status-pending", ".txt", List.of("P"));
        Path approvedFile = createTempTextFile("queue-status-approved", ".txt", List.of("A"));
        Path rejectedFile = createTempTextFile("queue-status-rejected", ".txt", List.of("R"));

        context.authorService.registerAuthor("queue-status-author", "Queue Status Author", "Password1!", "Bio");
        BookSubmission2 pending = context.authorService.publishBook("queue-status-author", "Status Pending", List.of("Technology"), "Desc", pendingFile.toString());
        BookSubmission2 approved = context.authorService.publishBook("queue-status-author", "Status Approved", List.of("Technology"), "Desc", approvedFile.toString());
        BookSubmission2 rejected = context.authorService.publishBook("queue-status-author", "Status Rejected", List.of("Technology"), "Desc", rejectedFile.toString());

        context.librarianService.registerLibrarian("queue-lib-status", "Queue Lib Status", "Password1!", "EMP-QS");
        context.librarianService.approveSubmission(approved.getId(), "approved");
        context.librarianService.rejectSubmission(rejected.getId(), "rejected", "reason");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String librarianSession = loginAndGetSessionId(client, baseUrl, "queue-lib-status", "Password1!", "LIBRARIAN");

            HttpRequest pendingRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending?status=pending"))
                    .header("X-Session-Id", librarianSession)
                    .GET()
                    .build();
            HttpResponse<String> pendingResponse = client.send(pendingRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, pendingResponse.statusCode(), "pending status filter should return HTTP 200");
            assertTrue(pendingResponse.body().contains("\"id\":\"" + pending.getId() + "\""), "pending status filter should include pending submission");
            assertFalse(pendingResponse.body().contains("\"id\":\"" + approved.getId() + "\""), "pending status filter should exclude approved submission");
            assertFalse(pendingResponse.body().contains("\"id\":\"" + rejected.getId() + "\""), "pending status filter should exclude rejected submission");

            HttpRequest approvedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending?status=approved"))
                    .header("X-Session-Id", librarianSession)
                    .GET()
                    .build();
            HttpResponse<String> approvedResponse = client.send(approvedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, approvedResponse.statusCode(), "approved status filter should return HTTP 200");
            assertTrue(approvedResponse.body().contains("\"id\":\"" + approved.getId() + "\""), "approved status filter should include approved submission");

            HttpRequest rejectedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending?status=rejected"))
                    .header("X-Session-Id", librarianSession)
                    .GET()
                    .build();
            HttpResponse<String> rejectedResponse = client.send(rejectedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, rejectedResponse.statusCode(), "rejected status filter should return HTTP 200");
            assertTrue(rejectedResponse.body().contains("\"id\":\"" + rejected.getId() + "\""), "rejected status filter should include rejected submission");

            HttpRequest allRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending?status=all"))
                    .header("X-Session-Id", librarianSession)
                    .GET()
                    .build();
            HttpResponse<String> allResponse = client.send(allRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, allResponse.statusCode(), "all status filter should return HTTP 200");
            assertTrue(allResponse.body().contains("\"id\":\"" + pending.getId() + "\""), "all status filter should include pending submission");
            assertTrue(allResponse.body().contains("\"id\":\"" + approved.getId() + "\""), "all status filter should include approved submission");
            assertTrue(allResponse.body().contains("\"id\":\"" + rejected.getId() + "\""), "all status filter should include rejected submission");
        } finally {
            server.stop(0);
            Files.deleteIfExists(pendingFile);
            Files.deleteIfExists(approvedFile);
            Files.deleteIfExists(rejectedFile);
        }
    }

    private static void testLibrarianPendingQueueSortBySubmittedDateDesc() throws Exception {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("queue-lib-sort", "Queue Lib Sort", "Password1!", "EMP-QD");

        BookSubmission2 older = new BookSubmission2(
                "queue-old-id",
                "Queue Older",
                "queue-old-author",
                "Queue Old Author",
                List.of("Technology"),
                "Old",
                "old.txt",
                LocalDate.now().minusDays(5)
        );
        BookSubmission2 newer = new BookSubmission2(
                "queue-new-id",
                "Queue Newer",
                "queue-new-author",
                "Queue New Author",
                List.of("Technology"),
                "New",
                "new.txt",
                LocalDate.now().minusDays(1)
        );
        context.submissionRepository.save(older);
        context.submissionRepository.save(newer);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String librarianSession = loginAndGetSessionId(client, baseUrl, "queue-lib-sort", "Password1!", "LIBRARIAN");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending?status=all&sortBy=submittedDate&sortDir=desc"))
                    .header("X-Session-Id", librarianSession)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "submitted date desc sort should return HTTP 200");

            String firstId = extractJsonField(response.body(), "id");
            assertEquals(newer.getId(), firstId, "submitted date desc sort should place newer submission first");
        } finally {
            server.stop(0);
        }
    }

    private static void testLibrarianPendingQueueNoFilterCompatibilityBaseline() throws Exception {
        TestContext context = new TestContext();
        Path baselinePendingFile = createTempTextFile("queue-baseline-pending", ".txt", List.of("P"));
        Path baselineApprovedFile = createTempTextFile("queue-baseline-approved", ".txt", List.of("A"));

        context.authorService.registerAuthor("queue-baseline-author", "Queue Baseline Author", "Password1!", "Bio");
        BookSubmission2 pending = context.authorService.publishBook("queue-baseline-author", "Baseline Pending", List.of("Technology"), "Desc", baselinePendingFile.toString());
        BookSubmission2 approved = context.authorService.publishBook("queue-baseline-author", "Baseline Approved", List.of("Technology"), "Desc", baselineApprovedFile.toString());
        context.librarianService.registerLibrarian("queue-lib-baseline", "Queue Lib Baseline", "Password1!", "EMP-QB");
        context.librarianService.approveSubmission(approved.getId(), "approved");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String librarianSession = loginAndGetSessionId(client, baseUrl, "queue-lib-baseline", "Password1!", "LIBRARIAN");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending"))
                    .header("X-Session-Id", librarianSession)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "no-filter baseline should return HTTP 200");
            assertTrue(response.body().contains("\"id\":\"" + pending.getId() + "\""), "no-filter baseline should include pending submission");
            assertFalse(response.body().contains("\"id\":\"" + approved.getId() + "\""), "no-filter baseline should keep pending-only behavior");
        } finally {
            server.stop(0);
            Files.deleteIfExists(baselinePendingFile);
            Files.deleteIfExists(baselineApprovedFile);
        }
    }

    private static void testNonLibrarianForbiddenFromPendingQueueEndpoint() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("queue-non-lib", "Queue Non Lib", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "queue-non-lib", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending?status=all&q=queue"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode(), "non-librarian should be forbidden from pending queue endpoint");
            assertTrue(response.body().contains("Permission denied"), "response should explain permission denied");
        } finally {
            server.stop(0);
        }
    }

    private static void testLibrarianPasswordChangeRequiresCurrentPassword() throws Exception {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-reauth-missing", "Lib Missing", "Password1!", "EMP-MISSING");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "lib-reauth-missing", "Password1!", "LIBRARIAN");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Lib+Missing&employeeId=EMP-MISSING&password=NewPass1!"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode(), "librarian password change without current password should be rejected");
            assertTrue(response.body().contains("Current password is required"), "response should explain re-auth requirement");
        } finally {
            server.stop(0);
        }
    }

    private static void testLibrarianPasswordChangeRejectsWrongCurrentPassword() throws Exception {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-reauth-wrong", "Lib Wrong", "Password1!", "EMP-WRONG");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "lib-reauth-wrong", "Password1!", "LIBRARIAN");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Lib+Wrong&employeeId=EMP-WRONG&password=NewPass1!&currentPassword=WrongPass1!"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode(), "librarian password change with wrong current password should be rejected");
            assertTrue(response.body().contains("Current password is incorrect."), "response should explain current password mismatch");
        } finally {
            server.stop(0);
        }
    }

    private static void testLibrarianPasswordChangeSucceedsWithCorrectCurrentPassword() throws Exception {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-reauth-ok", "Lib Ok", "Password1!", "EMP-OK");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "lib-reauth-ok", "Password1!", "LIBRARIAN");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Lib+Ok+Updated&employeeId=EMP-NEW&password=NewPass1!&currentPassword=Password1!"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "librarian password change with correct current password should succeed");

            HttpRequest oldLogin = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=lib-reauth-ok&password=Password1!&role=LIBRARIAN"))
                    .build();
            HttpResponse<String> oldLoginResponse = client.send(oldLogin, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, oldLoginResponse.statusCode(), "old librarian password should no longer work");

            HttpRequest newLogin = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=lib-reauth-ok&password=NewPass1!&role=LIBRARIAN"))
                    .build();
            HttpResponse<String> newLoginResponse = client.send(newLogin, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, newLoginResponse.statusCode(), "new librarian password should work after successful update");
        } finally {
            server.stop(0);
        }
    }

    private static void testLibrarianNonPasswordProfileUpdateWorksWithoutCurrentPassword() throws Exception {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-nopwd", "Lib NoPwd", "Password1!", "EMP-NOPWD");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "lib-nopwd", "Password1!", "LIBRARIAN");

            HttpRequest updateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Lib+NoPwd+Updated&employeeId=EMP-NOPWD&password="))
                    .build();
            HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, updateResponse.statusCode(), "librarian non-password profile update should work without current password");

            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/profile"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode(), "updated librarian profile should still be retrievable");
            assertTrue(getResponse.body().contains("\"fullName\":\"Lib NoPwd Updated\""), "librarian full name should be updated");
        } finally {
            server.stop(0);
        }
    }

    private static void testLibrarianInactiveSessionExpiresAndRejectsApiCall() throws Exception {
        final String propertyKey = "library.sessionIdleTimeoutMs";
        String previous = System.getProperty(propertyKey);
        System.setProperty(propertyKey, "120");

        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-session-expired", "Lib Session", "Password1!", "EMP-SESSION");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "lib-session-expired", "Password1!", "LIBRARIAN");

            Thread.sleep(220);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode(), "inactive librarian session should expire automatically");
            assertTrue(response.body().contains("Session expired"), "response should explain inactivity session expiry");
        } finally {
            server.stop(0);
            restoreSystemProperty(propertyKey, previous);
        }
    }

    private static void testLibrarianActiveSessionReviewEndpointRemainsFunctioning() throws Exception {
        final String propertyKey = "library.sessionIdleTimeoutMs";
        String previous = System.getProperty(propertyKey);
        System.setProperty(propertyKey, "300");

        TestContext context = new TestContext();
        Path file = createTempTextFile("librarian-active-session", ".txt", List.of("content"));

        context.authorService.registerAuthor("author-active-review", "Author Active", "Password1!", "Bio");
        context.librarianService.registerLibrarian("lib-active-review", "Lib Active", "Password1!", "EMP-ACTIVE");

        BookSubmission2 submission = context.authorService.publishBook(
                "author-active-review",
                "Active Session Review",
                List.of("Technology"),
                "Description",
                file.toString()
        );

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "lib-active-review", "Password1!", "LIBRARIAN");

            HttpRequest keepAliveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/pending"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> firstResponse = client.send(keepAliveRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, firstResponse.statusCode(), "first librarian pending request should succeed");

            Thread.sleep(150);

            HttpRequest reviewRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/librarian/review"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "submissionId=" + submission.getId() + "&action=approve&comment=Looks+good"
                    ))
                    .build();
            HttpResponse<String> reviewResponse = client.send(reviewRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, reviewResponse.statusCode(), "librarian review endpoint should remain functional for active session");
            assertTrue(reviewResponse.body().contains("Submission approved."), "review response should confirm approval");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
            restoreSystemProperty(propertyKey, previous);
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
            assertTrue(listResponse.body().contains("\"priority\":"), "response should include priority field");
            int unreadBeforeRead = countOccurrences(listResponse.body(), "\"read\":false");

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
            int unreadAfterRead = countOccurrences(afterReadListResponse.body(), "\"read\":false");
            assertTrue(unreadAfterRead <= unreadBeforeRead, "unread counter should not increase after mark-read");

                HttpRequest secondProfileUpdateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Staff+Notify+Updated+Again&password="))
                    .build();
                HttpResponse<String> secondProfileUpdateResponse = client.send(secondProfileUpdateRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, secondProfileUpdateResponse.statusCode(), "second profile update should generate another notification");

                HttpResponse<String> beforeDeleteListResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, beforeDeleteListResponse.statusCode(), "notifications list before delete should return HTTP 200");
                int unreadBeforeDelete = countOccurrences(beforeDeleteListResponse.body(), "\"read\":false");
                String unreadNotificationId = extractJsonField(beforeDeleteListResponse.body(), "id");

            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/delete"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + unreadNotificationId))
                    .build();
            HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, deleteResponse.statusCode(), "delete notification should return HTTP 200");
            assertTrue(deleteResponse.body().contains("\"status\":\"deleted\""), "delete response should confirm deleted status");

            HttpResponse<String> afterDeleteListResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, afterDeleteListResponse.statusCode(), "notifications list after delete should return HTTP 200");
                assertFalse(afterDeleteListResponse.body().contains("\"id\":\"" + unreadNotificationId + "\""), "deleted notification should be removed from list");
            int unreadAfterDelete = countOccurrences(afterDeleteListResponse.body(), "\"read\":false");
                assertTrue(unreadAfterDelete <= unreadBeforeDelete, "unread counter should not increase after delete");
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

            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/delete"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", ownerBSession)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + foreignNotificationId))
                    .build();
            HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, deleteResponse.statusCode(), "student/staff cannot delete another user's notification");
            assertTrue(deleteResponse.body().contains("does not belong to this user"), "delete response should explain ownership boundary");

                HttpRequest archiveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/archive"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", ownerBSession)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + foreignNotificationId))
                    .build();
                HttpResponse<String> archiveResponse = client.send(archiveRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(400, archiveResponse.statusCode(), "student/staff cannot archive another user's notification");
                assertTrue(archiveResponse.body().contains("does not belong to this user"), "archive response should explain ownership boundary");

                HttpRequest unarchiveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/unarchive"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", ownerBSession)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + foreignNotificationId))
                    .build();
                HttpResponse<String> unarchiveResponse = client.send(unarchiveRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(400, unarchiveResponse.statusCode(), "student/staff cannot unarchive another user's notification");
                assertTrue(unarchiveResponse.body().contains("does not belong to this user"), "unarchive response should explain ownership boundary");
        } finally {
            server.stop(0);
        }
    }

            private static void testStudentStaffNotificationArchiveAndUnarchiveSuccess() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("archive-owner", "Archive Owner", "Password1!", Role.STUDENT);

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "archive-owner", "Password1!", "STUDENT");

                HttpRequest profileUpdateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Archive+Owner+Updated&password="))
                    .build();
                HttpResponse<String> profileUpdateResponse = client.send(profileUpdateRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, profileUpdateResponse.statusCode(), "profile update should create a notification to archive");

                HttpRequest activeListRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> activeListResponse = client.send(activeListRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, activeListResponse.statusCode(), "active notifications list should return HTTP 200");
                String notificationId = extractJsonField(activeListResponse.body(), "id");
                assertTrue(activeListResponse.body().contains("\"archived\":false"), "new notification should be active by default");

                HttpRequest archiveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/archive"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + notificationId))
                    .build();
                HttpResponse<String> archiveResponse = client.send(archiveRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, archiveResponse.statusCode(), "archive should succeed for owner");
                assertTrue(archiveResponse.body().contains("\"status\":\"archived\""), "archive response should confirm archived status");

                HttpRequest archivedListRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=archived"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> archivedListResponse = client.send(archivedListRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, archivedListResponse.statusCode(), "archived notifications list should return HTTP 200");
                assertTrue(archivedListResponse.body().contains("\"id\":\"" + notificationId + "\""), "archived list should include archived notification");
                assertTrue(archivedListResponse.body().contains("\"archived\":true"), "archived notification should expose archived=true");

                HttpRequest unarchiveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/unarchive"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + notificationId))
                    .build();
                HttpResponse<String> unarchiveResponse = client.send(unarchiveRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, unarchiveResponse.statusCode(), "unarchive should succeed for owner");
                assertTrue(unarchiveResponse.body().contains("\"status\":\"active\""), "unarchive response should confirm active status");

                HttpResponse<String> afterUnarchiveActiveResponse = client.send(activeListRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, afterUnarchiveActiveResponse.statusCode(), "active notifications after unarchive should return HTTP 200");
                assertTrue(afterUnarchiveActiveResponse.body().contains("\"id\":\"" + notificationId + "\""), "unarchived notification should return to active list");
            } finally {
                server.stop(0);
            }
            }

            private static void testStudentStaffNotificationArchiveScopeFilteringAndUnreadConsistency() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("archive-scope", "Archive Scope", "Password1!", Role.STAFF);

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "archive-scope", "Password1!", "STAFF");

                HttpRequest profileUpdateOne = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Archive+Scope+One&password="))
                    .build();
                HttpRequest profileUpdateTwo = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Archive+Scope+Two&password="))
                    .build();
                assertEquals(200, client.send(profileUpdateOne, HttpResponse.BodyHandlers.ofString()).statusCode(), "first profile update should succeed");
                assertEquals(200, client.send(profileUpdateTwo, HttpResponse.BodyHandlers.ofString()).statusCode(), "second profile update should succeed");

                HttpRequest activeListRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> activeBeforeArchive = client.send(activeListRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, activeBeforeArchive.statusCode(), "default notifications endpoint should return active notifications");
                String toArchiveId = extractJsonField(activeBeforeArchive.body(), "id");
                int unreadBeforeArchive = countOccurrences(activeBeforeArchive.body(), "\"read\":false");

                HttpRequest archiveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications/archive"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("notificationId=" + toArchiveId))
                    .build();
                HttpResponse<String> archiveResponse = client.send(archiveRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, archiveResponse.statusCode(), "archive endpoint should succeed for owner");

                HttpResponse<String> activeAfterArchive = client.send(activeListRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, activeAfterArchive.statusCode(), "default notifications endpoint should still return active notifications");
                assertFalse(activeAfterArchive.body().contains("\"id\":\"" + toArchiveId + "\""), "default active scope should exclude archived notifications");

                HttpRequest archivedScopeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=archived"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> archivedScopeResponse = client.send(archivedScopeRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, archivedScopeResponse.statusCode(), "archived scope endpoint should return HTTP 200");
                assertTrue(archivedScopeResponse.body().contains("\"id\":\"" + toArchiveId + "\""), "archived scope should include archived notification");
                assertTrue(archivedScopeResponse.body().contains("\"archived\":true"), "archived scope payload should mark notifications as archived");

                HttpRequest invalidScopeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=invalid"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> invalidScopeResponse = client.send(invalidScopeRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(400, invalidScopeResponse.statusCode(), "invalid scope should return HTTP 400");
                assertTrue(invalidScopeResponse.body().contains("Invalid scope"), "invalid scope response should explain allowed values");

                int unreadAfterArchive = countOccurrences(activeAfterArchive.body(), "\"read\":false");
                assertTrue(unreadAfterArchive <= unreadBeforeArchive, "active unread count should not increase after archiving an active notification");
            } finally {
                server.stop(0);
            }
            }

            private static void testStudentStaffNotificationKeywordSearchFilter() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("notify-search", "Notify Search", "Password1!", Role.STUDENT);

            context.notificationService.addNotification(
                "notify-search",
                "Alpha Notice",
                "General update",
                NotificationPriority.NORMAL,
                null,
                Map.of()
            );
            context.notificationService.addNotification(
                "notify-search",
                "Other",
                "Contains keywordToken",
                NotificationPriority.NORMAL,
                null,
                Map.of()
            );

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "notify-search", "Password1!", "STUDENT");

                HttpRequest titleSearch = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=all&q=alpha"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> titleSearchResponse = client.send(titleSearch, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, titleSearchResponse.statusCode(), "keyword title search should return HTTP 200");
                assertTrue(titleSearchResponse.body().contains("\"title\":\"Alpha Notice\""), "title search should include matching title");
                assertFalse(titleSearchResponse.body().contains("\"title\":\"Other\""), "title search should exclude non-matching notification");

                HttpRequest messageSearch = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=all&q=KEYWORDTOKEN"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> messageSearchResponse = client.send(messageSearch, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, messageSearchResponse.statusCode(), "keyword message search should return HTTP 200");
                assertTrue(messageSearchResponse.body().contains("\"title\":\"Other\""), "message search should include matching notification");
                assertFalse(messageSearchResponse.body().contains("\"title\":\"Alpha Notice\""), "message search should exclude non-matching notification");
            } finally {
                server.stop(0);
            }
            }

            private static void testStudentStaffNotificationReadFilter() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("notify-read", "Notify Read", "Password1!", Role.STAFF);

            NotificationItem readTarget = context.notificationService.addNotification(
                "notify-read",
                "Read Target",
                "Read me",
                NotificationPriority.NORMAL,
                null,
                Map.of()
            );
            context.notificationService.addNotification(
                "notify-read",
                "Unread Target",
                "Unread me",
                NotificationPriority.NORMAL,
                null,
                Map.of()
            );
            context.notificationService.markAsRead("notify-read", readTarget.getId());

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "notify-read", "Password1!", "STAFF");

                HttpRequest readOnly = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=all&read=read"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> readOnlyResponse = client.send(readOnly, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, readOnlyResponse.statusCode(), "read filter should return HTTP 200");
                assertTrue(readOnlyResponse.body().contains("\"title\":\"Read Target\""), "read filter should include read notification");
                assertFalse(readOnlyResponse.body().contains("\"title\":\"Unread Target\""), "read filter should exclude unread notification");

                HttpRequest unreadOnly = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=all&read=unread"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> unreadOnlyResponse = client.send(unreadOnly, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, unreadOnlyResponse.statusCode(), "unread filter should return HTTP 200");
                assertTrue(unreadOnlyResponse.body().contains("\"title\":\"Unread Target\""), "unread filter should include unread notification");
                assertFalse(unreadOnlyResponse.body().contains("\"title\":\"Read Target\""), "unread filter should exclude read notification");
            } finally {
                server.stop(0);
            }
            }

            private static void testStudentStaffNotificationPriorityFilter() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("notify-priority", "Notify Priority", "Password1!", Role.STUDENT);

            context.notificationService.addNotification(
                "notify-priority",
                "Priority High",
                "High priority",
                NotificationPriority.HIGH,
                null,
                Map.of()
            );
            context.notificationService.addNotification(
                "notify-priority",
                "Priority Low",
                "Low priority",
                NotificationPriority.LOW,
                null,
                Map.of()
            );

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "notify-priority", "Password1!", "STUDENT");

                HttpRequest highOnly = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=all&priority=high"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> highOnlyResponse = client.send(highOnly, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, highOnlyResponse.statusCode(), "priority filter should return HTTP 200");
                assertTrue(highOnlyResponse.body().contains("\"title\":\"Priority High\""), "high priority filter should include high notification");
                assertFalse(highOnlyResponse.body().contains("\"title\":\"Priority Low\""), "high priority filter should exclude low notification");
            } finally {
                server.stop(0);
            }
            }

            private static void testStudentStaffNotificationScopeAndFilterCombo() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("notify-combo", "Notify Combo", "Password1!", Role.STAFF);

            NotificationItem archivedHighUnread = context.notificationService.addNotification(
                "notify-combo",
                "Archived High",
                "Archived and unread",
                NotificationPriority.HIGH,
                null,
                Map.of()
            );
            context.notificationService.addNotification(
                "notify-combo",
                "Active High",
                "Active and unread",
                NotificationPriority.HIGH,
                null,
                Map.of()
            );
            context.notificationService.archiveNotification("notify-combo", archivedHighUnread.getId());

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "notify-combo", "Password1!", "STAFF");

                HttpRequest comboRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=archived&read=unread&priority=high"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> comboResponse = client.send(comboRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, comboResponse.statusCode(), "scope and filter combo should return HTTP 200");
                assertTrue(comboResponse.body().contains("\"title\":\"Archived High\""), "combo filter should include archived high unread notification");
                assertFalse(comboResponse.body().contains("\"title\":\"Active High\""), "combo filter should exclude active high unread notification");
            } finally {
                server.stop(0);
            }
            }

            private static void testStudentStaffNotificationSortByCreatedAtDesc() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("notify-sort", "Notify Sort", "Password1!", Role.STUDENT);

            context.notificationService.addNotification(
                "notify-sort",
                "Sort Old",
                "Older item",
                NotificationPriority.NORMAL,
                null,
                Map.of()
            );
            Thread.sleep(10);
            context.notificationService.addNotification(
                "notify-sort",
                "Sort New",
                "Newer item",
                NotificationPriority.NORMAL,
                null,
                Map.of()
            );

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "notify-sort", "Password1!", "STUDENT");

                HttpRequest sortedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=all&sortBy=createdAt&sortDir=desc"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> sortedResponse = client.send(sortedRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, sortedResponse.statusCode(), "sortBy createdAt desc should return HTTP 200");

                int newIndex = sortedResponse.body().indexOf("\"title\":\"Sort New\"");
                int oldIndex = sortedResponse.body().indexOf("\"title\":\"Sort Old\"");
                assertTrue(newIndex >= 0 && oldIndex >= 0, "sorted response should include both notifications");
                assertTrue(newIndex < oldIndex, "newer notification should appear before older notification for createdAt desc");
            } finally {
                server.stop(0);
            }
            }

            private static void testStudentStaffNotificationInvalidFilterValuesReturnBadRequest() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("notify-invalid", "Notify Invalid", "Password1!", Role.STAFF);
            context.notificationService.addNotification("notify-invalid", "Seed", "Seed", NotificationPriority.NORMAL, null, Map.of());

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String sessionId = loginAndGetSessionId(client, baseUrl, "notify-invalid", "Password1!", "STAFF");

                HttpRequest invalidRead = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?read=maybe"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> invalidReadResponse = client.send(invalidRead, HttpResponse.BodyHandlers.ofString());
                assertEquals(400, invalidReadResponse.statusCode(), "invalid read filter should return HTTP 400");
                assertTrue(invalidReadResponse.body().contains("Invalid read filter"), "invalid read response should explain allowed values");

                HttpRequest invalidPriority = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?priority=urgent"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> invalidPriorityResponse = client.send(invalidPriority, HttpResponse.BodyHandlers.ofString());
                assertEquals(400, invalidPriorityResponse.statusCode(), "invalid priority filter should return HTTP 400");
                assertTrue(invalidPriorityResponse.body().contains("priority must be one of"), "invalid priority response should explain allowed values");

                HttpRequest invalidSort = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?sortBy=title"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
                HttpResponse<String> invalidSortResponse = client.send(invalidSort, HttpResponse.BodyHandlers.ofString());
                assertEquals(400, invalidSortResponse.statusCode(), "invalid sortBy should return HTTP 400");
                assertTrue(invalidSortResponse.body().contains("Invalid sortBy"), "invalid sortBy response should explain allowed values");
            } finally {
                server.stop(0);
            }
            }

            private static void testStudentStaffNotificationFiltersKeepOwnershipScoping() throws Exception {
            TestContext context = new TestContext();
            context.authService.registerStudentOrStaff("notify-owner-a", "Notify Owner A", "Password1!", Role.STUDENT);
            context.authService.registerStudentOrStaff("notify-owner-b", "Notify Owner B", "Password1!", Role.STUDENT);

            context.notificationService.addNotification(
                "notify-owner-a",
                "Owner A Secret",
                "Only owner A should see this",
                NotificationPriority.HIGH,
                null,
                Map.of()
            );
            context.notificationService.addNotification(
                "notify-owner-b",
                "Owner B Own",
                "Only owner B should see this",
                NotificationPriority.HIGH,
                null,
                Map.of()
            );

            HttpServer server = createApiServer(context);
            try {
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                String ownerBSession = loginAndGetSessionId(client, baseUrl, "notify-owner-b", "Password1!", "STUDENT");

                HttpRequest filteredListRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/notifications?scope=all&priority=high&read=unread&q=secret"))
                    .header("X-Session-Id", ownerBSession)
                    .GET()
                    .build();
                HttpResponse<String> filteredListResponse = client.send(filteredListRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, filteredListResponse.statusCode(), "filtered list request should return HTTP 200");
                assertFalse(filteredListResponse.body().contains("Owner A Secret"), "filtered list should not leak other user's notifications");
                assertFalse(filteredListResponse.body().contains("Owner B Own"), "owner B item should not match secret keyword");
            } finally {
                server.stop(0);
            }
            }

    private static void testStudentStaffProfilePasswordChangeRequiresCurrentPassword() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("profile-reauth-missing", "Profile Missing", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "profile-reauth-missing", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Profile+Missing&password=NewPass1!"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode(), "password update without current password should be rejected");
            assertTrue(response.body().contains("Current password is required"), "response should explain re-auth requirement");
        } finally {
            server.stop(0);
        }
    }

    private static void testStudentStaffProfilePasswordChangeRejectsWrongCurrentPassword() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("profile-reauth-wrong", "Profile Wrong", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "profile-reauth-wrong", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Profile+Wrong&password=NewPass1!&currentPassword=WrongPass1!"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode(), "password update with wrong current password should be rejected");
            assertTrue(response.body().contains("Current password is incorrect."), "response should explain current password mismatch");
        } finally {
            server.stop(0);
        }
    }

    private static void testStudentStaffProfilePasswordChangeSucceedsWithCorrectCurrentPassword() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("profile-reauth-ok", "Profile Ok", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "profile-reauth-ok", "Password1!", "STUDENT");

            HttpRequest updateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Profile+Ok+Updated&password=NewPass1!&currentPassword=Password1!"))
                    .build();
            HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, updateResponse.statusCode(), "password update with correct current password should succeed");

            HttpRequest oldLogin = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=profile-reauth-ok&password=Password1!&role=STUDENT"))
                    .build();
            HttpResponse<String> oldLoginResponse = client.send(oldLogin, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, oldLoginResponse.statusCode(), "old password should no longer work");

            HttpRequest newLogin = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=profile-reauth-ok&password=NewPass1!&role=STUDENT"))
                    .build();
            HttpResponse<String> newLoginResponse = client.send(newLogin, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, newLoginResponse.statusCode(), "new password should work after successful update");
        } finally {
            server.stop(0);
        }
    }

    private static void testStudentStaffProfileUpdateWithoutPasswordDoesNotRequireCurrentPassword() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("profile-nopwd", "Profile NoPwd", "Password1!", Role.STAFF);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "profile-nopwd", "Password1!", "STAFF");

            HttpRequest updateRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/profile"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("fullName=Profile+NoPwd+Updated&password="))
                    .build();
            HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, updateResponse.statusCode(), "non-password profile update should succeed without current password");

            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/profile"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode(), "updated profile should still be retrievable");
            assertTrue(getResponse.body().contains("\"fullName\":\"Profile NoPwd Updated\""), "full name should update without password change");
        } finally {
            server.stop(0);
        }
    }

    private static void testStudentStaffInactiveSessionExpiresAutomatically() throws Exception {
        final String propertyKey = "library.sessionIdleTimeoutMs";
        String previous = System.getProperty(propertyKey);
        System.setProperty(propertyKey, "120");

        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("session-expire", "Session Expire", "Password1!", Role.STUDENT);
        context.addApprovedBook("Idle Timeout Book", "Idle Author", "Idle Summary");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "session-expire", "Password1!", "STUDENT");

            Thread.sleep(220);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/books"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode(), "inactive session should expire automatically");
            assertTrue(response.body().contains("Session expired"), "response should explain session inactivity expiry");
        } finally {
            server.stop(0);
            restoreSystemProperty(propertyKey, previous);
        }
    }

    private static void testStudentStaffActiveSessionStaysValidWithContinuousActivity() throws Exception {
        final String propertyKey = "library.sessionIdleTimeoutMs";
        String previous = System.getProperty(propertyKey);
        System.setProperty(propertyKey, "300");

        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("session-active", "Session Active", "Password1!", Role.STUDENT);
        context.addApprovedBook("Keep Alive Book", "Keep Author", "Keep Summary");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "session-active", "Password1!", "STUDENT");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/books"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();

            HttpResponse<String> first = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, first.statusCode(), "first authenticated request should succeed");

            Thread.sleep(150);
            HttpResponse<String> second = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, second.statusCode(), "session should remain valid with recent activity");

            Thread.sleep(150);
            HttpResponse<String> third = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, third.statusCode(), "continuous requests should keep session active");
        } finally {
            server.stop(0);
            restoreSystemProperty(propertyKey, previous);
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
            assertFalse(notificationsResponse.body().contains("Insufficient references"), "notification should not include rejection reason");
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

    private static void testSessionSnapshotSaveAndGetSuccess() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("snapshot-user", "Snapshot User", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "snapshot-user", "Password1!", "STUDENT");

            HttpRequest saveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot/save"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "portalKey=student-portal&lastViewKey=notifications-board&lastAction=apply-filter&statePayload=%7B%22scope%22%3A%22archived%22%7D"
                    ))
                    .build();
            HttpResponse<String> saveResponse = client.send(saveRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, saveResponse.statusCode(), "save snapshot should return HTTP 200");
            assertTrue(saveResponse.body().contains("\"exists\":true"), "save response should confirm snapshot exists");
            assertTrue(saveResponse.body().contains("\"portalKey\":\"student-portal\""), "save response should include portal key");

            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode(), "get snapshot should return HTTP 200");
            assertTrue(getResponse.body().contains("\"exists\":true"), "get response should indicate snapshot exists");
            assertTrue(getResponse.body().contains("\"username\":\"snapshot-user\""), "get response should include username");
            assertTrue(getResponse.body().contains("\"lastAction\":\"apply-filter\""), "get response should include last action");
            assertTrue(getResponse.body().contains("archived"), "get response should include saved state payload");
        } finally {
            server.stop(0);
        }
    }

    private static void testSessionSnapshotClearRemovesState() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("snapshot-clear", "Snapshot Clear", "Password1!", Role.STAFF);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "snapshot-clear", "Password1!", "STAFF");

            HttpRequest saveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot/save"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("portalKey=staff-portal&lastViewKey=notifications-board&lastAction=save"))
                    .build();
            assertEquals(200, client.send(saveRequest, HttpResponse.BodyHandlers.ofString()).statusCode(), "save before clear should succeed");

            HttpRequest clearRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot/clear"))
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> clearResponse = client.send(clearRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, clearResponse.statusCode(), "clear snapshot should return HTTP 200");
            assertTrue(clearResponse.body().contains("\"cleared\":true"), "clear response should confirm removal");

            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode(), "get after clear should return HTTP 200");
            assertTrue(getResponse.body().contains("\"exists\":false"), "get after clear should be empty");
        } finally {
            server.stop(0);
        }
    }

    private static void testSessionSnapshotNoSnapshotReturnsSafeEmptyResponse() throws Exception {
        TestContext context = new TestContext();
        context.authorService.registerAuthor("snapshot-empty", "Snapshot Empty", "Password1!", "Bio");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "snapshot-empty", "Password1!", "AUTHOR");

            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode(), "empty snapshot request should return HTTP 200");
            assertTrue(getResponse.body().contains("\"exists\":false"), "empty snapshot response should be safe and explicit");
            assertTrue(getResponse.body().contains("\"portalKey\":\"\""), "empty snapshot response should include blank portal key");
        } finally {
            server.stop(0);
        }
    }

    private static void testSessionSnapshotOwnershipIsSessionScoped() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("snapshot-scope", "Snapshot Scope", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String firstSessionId = loginAndGetSessionId(client, baseUrl, "snapshot-scope", "Password1!", "STUDENT");
            String secondSessionId = loginAndGetSessionId(client, baseUrl, "snapshot-scope", "Password1!", "STUDENT");

            HttpRequest saveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot/save"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", firstSessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("portalKey=student-portal&lastViewKey=notifications-board&lastAction=owner-save"))
                    .build();
            assertEquals(200, client.send(saveRequest, HttpResponse.BodyHandlers.ofString()).statusCode(), "owner session save should succeed");

            HttpRequest otherSessionGet = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot"))
                    .header("X-Session-Id", secondSessionId)
                    .GET()
                    .build();
            HttpResponse<String> otherSessionResponse = client.send(otherSessionGet, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, otherSessionResponse.statusCode(), "second session get should return HTTP 200");
            assertTrue(otherSessionResponse.body().contains("\"exists\":false"), "second session should not see first session snapshot");
        } finally {
            server.stop(0);
        }
    }

    private static void testLogoutClearsSessionSnapshot() throws Exception {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("snapshot-logout", "Snapshot Logout", "Password1!", "EMP-SNAPSHOT");

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "snapshot-logout", "Password1!", "LIBRARIAN");

            HttpRequest saveRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot/save"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("portalKey=librarian-portal&lastViewKey=pending-submissions&lastAction=save"))
                    .build();
            assertEquals(200, client.send(saveRequest, HttpResponse.BodyHandlers.ofString()).statusCode(), "save before logout should succeed");

            HttpRequest logoutRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/logout"))
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> logoutResponse = client.send(logoutRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, logoutResponse.statusCode(), "logout should succeed");
            assertFalse(context.sessionSnapshotService.getSnapshot(sessionId, "snapshot-logout", Role.LIBRARIAN).isPresent(), "logout should clear server-side snapshot");
        } finally {
            server.stop(0);
        }
    }

    private static void testDevCrashHookForSnapshotIsGuarded() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("snapshot-guard", "Snapshot Guard", "Password1!", Role.STUDENT);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "snapshot-guard", "Password1!", "STUDENT");

            HttpRequest guardedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/dev/crash-test"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString("portalKey=student-portal&lastViewKey=notifications-board"))
                    .build();
            HttpResponse<String> guardedResponse = client.send(guardedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(403, guardedResponse.statusCode(), "dev crash hook should be guarded without token");
            assertTrue(guardedResponse.body().contains("Crash test hook disabled"), "guarded response should explain dev-only restriction");
        } finally {
            server.stop(0);
        }
    }

    private static void testDevCrashHookCanSaveSessionSnapshot() throws Exception {
        TestContext context = new TestContext();
        context.authService.registerStudentOrStaff("snapshot-dev", "Snapshot Dev", "Password1!", Role.STAFF);

        HttpServer server = createApiServer(context);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            String sessionId = loginAndGetSessionId(client, baseUrl, "snapshot-dev", "Password1!", "STAFF");

            HttpRequest devRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/dev/crash-test"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Session-Id", sessionId)
                    .header("X-Crash-Test-Hook", "enable")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "portalKey=staff-portal&lastViewKey=notifications-board&lastAction=simulate&statePayload=%7B%22query%22%3A%22urgent%22%7D"
                    ))
                    .build();
            HttpResponse<String> devResponse = client.send(devRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, devResponse.statusCode(), "dev crash hook should succeed with token");
            assertTrue(devResponse.body().contains("\"status\":\"simulated\""), "dev crash hook should confirm simulation");

            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/session-snapshot"))
                    .header("X-Session-Id", sessionId)
                    .GET()
                    .build();
            HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode(), "snapshot get after dev crash hook should return HTTP 200");
            assertTrue(getResponse.body().contains("\"portalKey\":\"staff-portal\""), "dev crash hook should save portal key");
            assertTrue(getResponse.body().contains("urgent"), "dev crash hook should persist state payload");
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
                context.bookReviewService,
                context.bookRequestService,
                context.recommendationService,
                context.authorService,
                context.authorDraftService,
                context.fileService,
                context.librarianService,
                context.notificationService,
                context.readingProgressService,
                context.sessionSnapshotService
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

    private static int countOccurrences(String text, String token) {
        if (text == null || text.isEmpty() || token == null || token.isEmpty()) {
            return 0;
        }

        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static long countBorrowReminderNotifications(List<NotificationItem> notifications,
                                                         String borrowRecordId,
                                                         String category) {
        return notifications.stream()
                .filter(item -> "borrow-reminder".equals(item.getMetadata().get("type")))
                .filter(item -> borrowRecordId.equals(item.getMetadata().get("borrowRecordId")))
                .filter(item -> category.equals(item.getMetadata().get("category")))
                .count();
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
            "NewPass1!",
            "Password1!"
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
            () -> context.librarianService.updateLibrarianProfile("lib-validate", "lib-validate", "", "EMP-VALID", "", ""),
            "Full Name cannot be empty");
        expectThrows(ValidationException.class,
            () -> context.librarianService.updateLibrarianProfile("lib-validate", "lib-validate", "Valid Librarian", "", "", ""),
            "Employee ID cannot be empty");
        expectThrows(ValidationException.class,
            () -> context.librarianService.updateLibrarianProfile("lib-validate", "lib-validate", "Valid Librarian", "EMP-VALID", "short", "Password1!"),
            "Password must be between 8 and 64 characters");
        expectThrows(ValidationException.class,
            () -> context.librarianService.updateLibrarianProfile("lib-validate", "lib-validate", "Valid Librarian", "EMP-VALID", "NewPass1!", ""),
            "Current password is required to change password");
        expectThrows(AuthenticationException.class,
            () -> context.librarianService.updateLibrarianProfile("lib-validate", "lib-validate", "Valid Librarian", "EMP-VALID", "NewPass1!", "WrongPass1!"),
            "Current password is incorrect");
        }

        private static void testLibrarianProfileOwnershipBoundary() {
        TestContext context = new TestContext();
        context.librarianService.registerLibrarian("lib-a", "Lib A", "Password1!", "EMP-A");
        context.librarianService.registerLibrarian("lib-b", "Lib B", "Password1!", "EMP-B");

        expectThrows(ValidationException.class,
            () -> context.librarianService.updateLibrarianProfile("lib-a", "lib-b", "Changed", "EMP-CHANGED", "", ""),
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

    private static void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
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
        private final MemoryBookReviewRepository bookReviewRepository = new MemoryBookReviewRepository();
        private final MemoryBookRequestRepository2 bookRequestRepository = new MemoryBookRequestRepository2();
        private final MemoryUserRepository userRepository = new MemoryUserRepository();
        private final MemoryAuthorProfileRepository2 authorProfileRepository = new MemoryAuthorProfileRepository2();
        private final MemoryBookSubmissionRepository2 submissionRepository = new MemoryBookSubmissionRepository2();
        private final MemoryBookDraftRepository2 draftRepository = new MemoryBookDraftRepository2();
        private final MemoryLibrarianProfileRepository3 librarianProfileRepository = new MemoryLibrarianProfileRepository3();

        private final AuthService authService = new AuthService(userRepository);
        private final BookService bookService = new BookService(bookRepository);
        private final RecommendationService recommendationService = new RecommendationService(bookRepository, borrowRepository);
        private final BookRequestService bookRequestService = new BookRequestService(bookRequestRepository, bookRepository);
        private final AuthorService2 authorService = new AuthorService2(
            userRepository,
            authorProfileRepository,
            submissionRepository,
            bookRepository,
            borrowRepository,
            new FileService()
        );
        private final AuthorDraftService authorDraftService = new AuthorDraftService(draftRepository);
        private final FileService fileService = new FileService();
        private final ReadingProgressService readingProgressService = new ReadingProgressService(new MemoryReadingProgressRepository());
        private final NotificationService notificationService = new NotificationService(new MemoryNotificationRepository());
        private final BorrowService borrowService = new BorrowService(bookRepository, borrowRepository, readingProgressService, notificationService);
        private final BookReviewService bookReviewService = new BookReviewService(bookReviewRepository, bookService, borrowService);
        private final LibrarianService3 librarianService = new LibrarianService3(
            userRepository,
            authorProfileRepository,
            librarianProfileRepository,
            submissionRepository,
            bookRepository
        );
        private final SessionSnapshotService sessionSnapshotService = new SessionSnapshotService(new MemorySessionSnapshotRepository());

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