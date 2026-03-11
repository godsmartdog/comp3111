# Library Management System Documentation

## 1. Project Overview

This project is a layered Library Management System for COMP3111. It supports three major user portals and four user roles:

- Student
- Staff
- Author
- Librarian

The system covers the following task scope:

- Student and staff registration and login
- Viewing approved books
- Borrowing books with duration and borrow-limit control
- Author registration and login
- Draft auto-save, preview, and new-book submission
- Librarian registration and login
- Reviewing pending submissions
- Approving or rejecting submissions
- File validation and preview
- JavaFX UI and console UI support

The codebase is divided into 6 core parts:

1. Model
2. Repository
3. Service
4. Exception
5. Security
6. UI

UI is the most visible part of the project, but it depends on the other five layers to work correctly.

## 2. Overall Architecture

The project follows a simple layered architecture:

- Model stores the system data structures.
- Repository stores and retrieves data.
- Service implements business rules.
- Exception reports invalid operations clearly.
- Security enforces password rules, session state, and shared constraints.
- UI connects user actions to services.

Typical flow:

1. The user enters data in Console UI or JavaFX UI.
2. UI calls a Service method.
3. Service validates input and business rules.
4. Service reads or writes data through Repository.
5. Model objects hold the final state.
6. If something is wrong, an Exception is thrown and UI shows an error message.

## requirement
ALL task use the code in folder "Exception" to handle the error repsonse to user
explanation of the use of code is listed under part 3
### task 1.1 and task 1.2 Student/Staff Reg and login

-code in Service/AuthService.java
-using Model/User and Model/Role as class defination , Repository/UserRepository is used as interface(all interface is defined is their Memoryxxx version)
-password using code in "Security"

### task 1.3 List the available book list
-code in Service/BookService.java and Service/RecommendationService.java
-using Model/Book and Model/BorrowRecord as class defination , Repository/BookRepository and Repository/BorrowRecordRepository is used as interface(all interface is defined is their Memoryxxx version)

### task 1.4 Borrow Book
-code in Service/AuthService.java
-using Model/User and Model/Role as class defination , Repository/UserRepository is used as interface(all interface is defined is their Memoryxxx version)


## 3. Part One: Model

The Model package defines the core entities and enums used by the whole system.

### 3.1 User.java

Purpose:

- Represents a generic system user.
- Used for student, staff, author, and librarian accounts.

Fields:

- `username`: unique account id
- `fullName`: display name
- `passwordHash`: encrypted password
- `role`: role type
- `createdAt`: account creation time

Functions:

- `User(String username, String fullName, String passwordHash, Role role)`
  Creates a new user object and records creation time.
- `getUsername()`
  Returns the username.
- `getFullName()`
  Returns the user's full name.
- `getPasswordHash()`
  Returns the stored password hash.
- `getRole()`
  Returns the user role.
- `getCreatedAt()`
  Returns the account creation timestamp.
- `equals(Object obj)`
  Compares users by username so the same username is treated as the same logical user.
- `hashCode()`
  Generates a hash code based on username for collections.

### 3.2 Role.java

Purpose:

- Defines the four user roles used in login and authorization.

Values:

- `STUDENT`
- `STAFF`
- `AUTHOR`
- `LIBRARIAN`

### 3.3 Book.java

Purpose:

- Represents a published library book after librarian approval.

Fields:

- `id`: unique book id
- `title`: book title
- `authorFullName`: author display name
- `publishDate`: publication date after approval
- `approved`: whether the book has passed review
- `available`: whether the book can be borrowed now
- `summary`: description or abstract

Functions:

- `Book(String title, String authorFullName, String summary)`
  Creates a new unpublished and unavailable book.
- `getId()`
  Returns the book id.
- `getTitle()`
  Returns the book title.
- `getAuthorFullName()`
  Returns the author name shown to readers.
- `getPublishDate()`
  Returns the approval or publish date.
- `isApproved()`
  Tells whether the book is officially approved.
- `isAvailable()`
  Tells whether the book can currently be borrowed.
- `getSummary()`
  Returns the book summary.
- `approve(LocalDate publishDate)`
  Marks the book as approved, sets publish date, and makes it available.
- `setAvailable(boolean available)`
  Updates borrow availability after borrow or return.

### 3.4 BorrowRecord.java

Purpose:

- Tracks one borrow transaction.

Fields:

- `id`: borrow record id
- `username`: borrower username
- `bookId`: borrowed book id
- `borrowDate`: date borrowed
- `dueDate`: due date
- `returned`: whether the book has been returned

Functions:

- `BorrowRecord(String username, String bookId, LocalDate borrowDate, LocalDate dueDate)`
  Creates a new active borrow record.
- `getId()`
  Returns record id.
- `getUsername()`
  Returns borrower username.
- `getBookId()`
  Returns borrowed book id.
- `getBorrowDate()`
  Returns borrow start date.
- `getDueDate()`
  Returns due date.
- `isReturned()`
  Shows whether the record is closed.
- `markReturned()`
  Marks the borrow record as returned.

### 3.5 AuthorProfile2.java

Purpose:

- Stores author-specific profile data that does not belong in generic User.

Fields:

- `username`
- `bio`

Functions:

- `AuthorProfile2(String username, String bio)`
  Creates an author profile.
- `getUsername()`
  Returns the linked username.
- `getBio()`
  Returns the author biography.

### 3.6 LibrarianProfile3.java

Purpose:

- Stores librarian-specific profile data.

Fields:

- `username`
- `employeeId`

Functions:

- `LibrarianProfile3(String username, String employeeId)`
  Creates a librarian profile.
- `getUsername()`
  Returns linked username.
- `getEmployeeId()`
  Returns employee id.

### 3.7 BookDraft2.java

Purpose:

- Stores an author's work-in-progress submission draft.
- Supports the auto-save feature.

Fields:

- `authorUsername`
- `title`
- `genres`
- `description`
- `filePath`
- `lastSavedAt`

Functions:

- `BookDraft2(String authorUsername)`
  Creates an empty draft owned by the author.
- `getAuthorUsername()`
  Returns the draft owner.
- `getTitle()`
  Returns draft title.
- `getGenres()`
  Returns selected genres.
- `getDescription()`
  Returns draft description.
- `getFilePath()`
  Returns uploaded file path.
- `getLastSavedAt()`
  Returns the latest auto-save time.
- `setTitle(String title)`
  Updates title and refreshes save timestamp.
- `setGenres(List<String> genres)`
  Updates genres and refreshes save timestamp.
- `setDescription(String description)`
  Updates description and refreshes save timestamp.
- `setFilePath(String filePath)`
  Updates file path and refreshes save timestamp.

### 3.8 BookSubmission2.java

Purpose:

- Represents a final author submission waiting for librarian review.

Fields:

- `id`
- `title`
- `authorUsername`
- `authorFullName`
- `genres`
- `description`
- `fileName`
- `submittedDate`
- `status`
- `librarianComment`
- `approvedDate`

Functions:

- `BookSubmission2(String title, String authorUsername, String authorFullName, List<String> genres, String description, String fileName)`
  Creates a new submission with current date and initial `PENDING` state.
- `BookSubmission2(String id, String title, String authorUsername, String authorFullName, List<String> genres, String description, String fileName, LocalDate submittedDate)`
  Creates a submission with explicit id and date, useful for controlled creation or tests.
- `getId()`
  Returns submission id.
- `getTitle()`
  Returns title.
- `getAuthorUsername()`
  Returns author account name.
- `getAuthorFullName()`
  Returns author display name.
- `getGenres()`
  Returns a defensive copy of genres.
- `getDescription()`
  Returns description.
- `getFileName()`
  Returns submitted file path or file name.
- `getSubmittedDate()`
  Returns submission date.
- `getStatus()`
  Returns `PENDING`, `APPROVED`, or `REJECTED`.
- `getLibrarianComment()`
  Returns librarian decision comment.
- `getApprovedDate()`
  Returns approval date if approved.
- `approve(String comment)`
  Changes state to `APPROVED`, stores comment, and sets approval date.
- `reject(String comment)`
  Changes state to `REJECTED` and stores comment.

### 3.9 SubmissionState.java

Purpose:

- Defines review states of a submission.

Values:

- `PENDING`
- `APPROVED`
- `REJECTED`

## 4. Part Two: Repository

The Repository package is the data-access layer. In this project all repositories are in-memory implementations, which makes the system simple to run without database setup.

### 4.1 UserRepository and MemoryUserRepository

Purpose:

- Store and query user accounts.

Interface functions:

- `findByUsername(String username)`
  Finds one user safely with `Optional`.
- `save(User user)`
  Inserts or updates a user.
- `existsByUsername(String username)`
  Checks global username uniqueness.
- `findAll()`
  Returns all users.

Memory implementation details:

- Uses `Map<String, User>` with username as key.
- Fast lookup is important because registration and login use username often.

### 4.2 BookRepository and MemoryBookRepository

Purpose:

- Store approved books and search catalog data.

Interface functions:

- `save(Book book)`
  Stores a book.
- `findById(String id)`
  Looks up one book by id.
- `findAll()`
  Returns all books.
- `searchByTitleOrAuthor(String keyword)`
  Searches catalog by title or author name.

Memory implementation details:

- Uses `Map<String, Book>` with book id as key.
- Search normalizes text to lowercase for case-insensitive matching.

### 4.3 BorrowRepository and MemoryBorrowRepository

Purpose:

- Store borrowing history and active borrow records.

Interface functions:

- `save(BorrowRecord record)`
  Stores a new borrow record.
- `findById(String id)`
  Finds one record by record id.
- `findByUsername(String username)`
  Returns all records for one user.
- `findActiveByUsernameAndBookId(String username, String bookId)`
  Finds one active borrow relation for return logic.
- `findAll()`
  Returns all borrow records.

Memory implementation details:

- Uses `List<BorrowRecord>`.
- Suitable because the project is small and record count is limited.

### 4.4 AuthorProfileRepository2 and MemoryAuthorProfileRepository2

Purpose:

- Store author biography information.

Functions:

- `save(AuthorProfile2 profile)`
  Stores author profile.
- `findByUsername(String username)`
  Looks up author profile by username.

Memory implementation details:

- Uses `Map<String, AuthorProfile2>`.

### 4.5 BookDraftRepository2 and MemoryBookDraftRepository2

Purpose:

- Support auto-save draft storage for authors.

Functions:

- `save(BookDraft2 draft)`
  Stores or updates one draft.
- `findByAuthorUsernameAndTitle(String authorUsername, String title)`
  Finds one draft by owner and title.
- `findAllByAuthorUsername(String authorUsername)`
  Returns all drafts owned by one author.
- `deleteByAuthorUsernameAndTitle(String authorUsername, String title)`
  Deletes a draft, usually after successful submission.

Memory implementation details:

- Uses nested map structure: author username -> normalized title -> draft.
- Title normalization avoids lookup errors caused by extra spaces.

### 4.6 BookSubmissionRepository2 and MemoryBookSubmissionRepository2

Purpose:

- Store author submissions during the review workflow.

Functions:

- `save(BookSubmission2 submission)`
  Stores or replaces a submission by id.
- `findById(String id)`
  Finds a submission by id.
- `findAll()`
  Returns all submissions.
- `findByStatus(SubmissionState status)`
  Returns submissions in a specific review state.
- `findByAuthorUsername(String username)`
  Returns submissions created by one author.

Memory implementation details:

- Uses `CopyOnWriteArrayList`.
- Saving removes same-id entries first, then adds the updated version.

### 4.7 LibrarianProfileRepository3 and MemoryLibrarianProfileRepository3

Purpose:

- Store librarian-specific profile information such as employee id.

Functions:

- `save(LibrarianProfile3 profile)`
  Stores librarian profile.
- `findByUsername(String username)`
  Retrieves profile by username.

Memory implementation details:

- Uses `Map<String, LibrarianProfile3>`.

## 5. Part Three: Service

The Service package is the business layer. This is where almost all task rules are enforced.

### 5.1 AuthService

Purpose:

- Handles student and staff registration, login, and logout.

Dependencies:

- `UserRepository`
- `PasswordHasher`
- `PasswordPolicy`
- `SessionManager`

Functions:

- `AuthService(UserRepository userRepository)`
  Injects repository and gets the singleton session manager.
- `registerStudentOrStaff(String username, String fullName, String password, Role role)`
  Validates role, validates fields, checks password policy, checks username uniqueness, hashes password, creates user, and saves it.
- `loginStudentOrStaff(String username, String password, Role expectedRole)`
  Finds user, checks expected role, compares password hash, creates session, and returns logged-in user.
- `logout()`
  Destroys the current session.
- `isLoggedIn()`
  Returns whether there is an authenticated session.
- `getCurrentUser()`
  Returns the current session user.

Internal helper:

- `validateBasicFields(String username, String fullName)`
  Ensures required fields are not empty.

### 5.2 BookService

Purpose:

- Provides catalog display logic for approved books.

Functions:

- `BookService(BookRepository bookRepository)`
  Injects book storage.
- `listApprovedBooksWithAvailability()`
  Returns only approved books, sorted by title.
- `searchApprovedBooks(String keyword)`
  Searches books by title or author, then filters only approved results.

### 5.3 BorrowService

Purpose:

- Controls borrowing and returning behavior.

Important rules enforced here:

- Only approved books can be borrowed.
- Only available books can be borrowed.
- Borrow limit is 5 active books.
- Borrow duration must be between 1 and 60 days.

Functions:

- `BorrowService(BookRepository bookRepository, BorrowRepository borrowRepository)`
  Injects book and borrow storage.
- `borrowBook(String username, String bookId)`
  Borrows with default duration from `SecurityConfig`.
- `borrowBook(String username, String bookId, int borrowDays)`
  Full borrow flow: find book, validate status, count active borrows, validate duration, create record, save record, and set book unavailable.
- `returnBook(String username, String bookId)`
  Finds active borrow record, marks it returned, and sets book available again.
- `listActiveBorrowsByUser(String username)`
  Returns only unreturned records of one user.

### 5.4 RecommendationService

Purpose:

- Implements the nice-to-have recommendation feature.

Functions:

- `RecommendationService(BookRepository bookRepository, BorrowRepository borrowRepository)`
  Injects catalog and borrow history.
- `recommendTopPopular(int limit)`
  Counts borrow frequency by book id, sorts approved books by popularity, and returns the top results.

### 5.5 AuthorService2

Purpose:

- Handles author account management and book submission.

Important rules enforced here:

- Username must be unique.
- Password must pass policy checks.
- Only AUTHOR users can submit books.
- Title, genres, description, and file name must be present.
- Genres must come from supported genre list.
- File extension must be one of the allowed author submission types.

Functions:

- `AuthorService2(UserRepository userRepository, AuthorProfileRepository2 authorProfileRepository, BookSubmissionRepository2 submissionRepository)`
  Injects repositories used by the author flow.
- `registerAuthor(String username, String fullName, String password, String bio)`
  Validates base data, hashes password, creates AUTHOR user, saves user, and saves author profile.
- `loginAuthor(String username, String password)`
  Authenticates AUTHOR user and creates session.
- `publishBook(String authorUsername, String title, List<String> genres, String description, String fileName)`
  Validates fields, validates genres and file format, checks author identity, creates `BookSubmission2`, and saves it.
- `getSupportedGenres()`
  Returns sorted supported genre list for UI display.
- `previewBook(String title, List<String> genres, String description)`
  Validates preview input and returns a formatted preview string with truncated description if needed.

Internal helpers:

- `validateGenres(List<String> genres)`
  Rejects unsupported genre names.
- `validateFileFormat(String fileName)`
  Rejects unsupported file extensions.
- `validateBasic(String username, String fullName)`
  Checks required fields for author registration.

### 5.6 AuthorDraftService

Purpose:

- Implements the author draft auto-save feature.

Functions:

- `AuthorDraftService(BookDraftRepository2 draftRepository)`
  Injects draft storage.
- `autoSave(String authorUsername, String title, List<String> genres, String description, String filePath)`
  Validates title, loads existing draft or creates new one, updates fields, saves it, and returns the draft.
- `loadDraft(String authorUsername, String title)`
  Loads one draft by title.
- `loadDrafts(String authorUsername)`
  Returns all drafts of one author.
- `clearDraft(String authorUsername, String title)`
  Deletes a saved draft after submission or manual cleanup.

Internal helper:

- `validateTitle(String title)`
  Ensures draft title is not empty.

### 5.7 FileService

Purpose:

- Validates uploaded files and creates preview text for librarians.

Functions:

- `validateSubmissionFile(String filePath)`
  Checks that file path exists, extension is allowed, file is a regular file, and size is within limit.
- `getPreviewDetails(String filePath)`
  Re-validates the file, returns path and size, and if the file is text or markdown, returns the first 20 lines; otherwise explains that console mode cannot render binary documents directly.

### 5.8 LibrarianService3

Purpose:

- Handles librarian account management and submission review.

Important rules enforced here:

- Username must be unique.
- Only LIBRARIAN users can log in through this service.
- Only `PENDING` submissions can be approved or rejected.
- Approval converts a submission into a real `Book` object.

Functions:

- `LibrarianService3(UserRepository userRepository, LibrarianProfileRepository3 librarianProfileRepository, BookSubmissionRepository2 submissionRepository, BookRepository bookRepository)`
  Injects repositories used by the librarian workflow.
- `registerLibrarian(String username, String fullName, String password, String employeeId)`
  Validates input, hashes password, creates LIBRARIAN user, and stores librarian profile.
- `loginLibrarian(String username, String password)`
  Authenticates librarian and creates session.
- `getPendingSubmissions()`
  Returns only `PENDING` submissions.
- `approveSubmission(String submissionId, String comment)`
  Finds a pending submission, marks it approved, saves it, creates a new approved `Book`, sets publish date, and saves the book into catalog.
- `rejectSubmission(String submissionId, String comment)`
  Finds a pending submission, marks it rejected, and saves it.
- `bulkApprove(List<String> submissionIds, String comment)`
  Approves multiple submissions one by one.
- `bulkReject(List<String> submissionIds, String comment)`
  Rejects multiple submissions one by one.

## 6. Part Four: Exception

The Exception package centralizes error reporting. Services throw these exceptions, and UI catches them to display user-friendly messages.

### 6.1 AuthenticationException

Purpose:

- Used when login fails or account role does not match.

Functions:

- `AuthenticationException(String message)`
  Creates an authentication error with message.
- `AuthenticationException(String message, Throwable cause)`
  Creates an authentication error with a root cause.

### 6.2 BusinessException

Purpose:

- Used when business rules are violated.

Typical examples:

- Borrowing an unavailable book
- Borrowing more than limit
- Borrowing an unapproved book

Functions:

- `BusinessException(String message)`
- `BusinessException(String message, Throwable cause)`

### 6.3 NotFoundException

Purpose:

- Used when a required resource does not exist.

Typical examples:

- Book id not found
- Submission id not found

Functions:

- `NotFoundException(String message)`

### 6.4 ValidationException

Purpose:

- Used for invalid input data.

Typical examples:

- Empty username
- Weak password
- Empty title
- Unsupported file type

Functions:

- `ValidationException(String message)`

## 7. Part Five: Security

The Security package keeps shared rules and security-related helpers in one place.

### 7.1 PasswordHasher

Purpose:

- Hashes passwords securely and verifies them later.

Important design:

- Uses PBKDF2 with HmacSHA256
- Uses random salt
- Stores iteration count, salt, and hash in one string
- Keeps backward compatibility with legacy SHA-256 style check

Functions:

- `sha256(String rawPassword)`
  Legacy compatibility wrapper that now delegates to the current hashing flow.
- `hashPassword(String rawPassword)`
  Generates salt, hashes password, and returns the encoded storage format.
- `matches(String rawPassword, String storedHash)`
  Verifies raw password against stored hash. Supports both PBKDF2 and legacy hash formats.

Internal helpers:

- `pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits)`
  Performs the real PBKDF2 derivation.
- `legacySha256(String rawPassword)`
  Legacy fallback verification path.

### 7.2 PasswordPolicy

Purpose:

- Enforces password quality rules.

Function:

- `validate(String password)`
  Checks for non-empty password, length between 8 and 64, no spaces, at least one uppercase letter, one lowercase letter, one digit, and one special character.

### 7.3 SecurityConfig

Purpose:

- Stores shared constants used across services.

Constants:

- `PASSWORD_MIN_LENGTH = 8`
- `MAX_BORROW_LIMIT = 5`
- `DEFAULT_BORROW_DAYS = 14`
- `MAX_FILE_SIZE_BYTES = 10 MB`
- `ALLOWED_EXTENSIONS = [.pdf, .txt, .doc, .docx, .md]`

Special design:

- Private constructor prevents accidental instantiation.

### 7.4 SessionManager

Purpose:

- Keeps login session state for the running application.
- Implemented as a singleton.

Fields managed internally:

- current user
- login time
- arbitrary session attributes

Functions:

- `getInstance()`
  Returns the singleton instance.
- `createSession(User user)`
  Stores logged-in user, records login time, and clears old session data.
- `destroySession()`
  Clears current session.
- `getCurrentUser()`
  Returns current logged-in user.
- `isAuthenticated()`
  Returns whether someone is logged in.
- `getLoginTime()`
  Returns login timestamp.
- `setAttribute(String key, Object value)`
  Stores custom session data.
- `getAttribute(String key)`
  Reads custom session data.

## 8. Part Six: UI

UI is the most important visible layer in this project because it demonstrates how all requirements are exposed to users. This project provides both Console UI and JavaFX UI.

### 8.1 UI Design Summary

The UI layer is responsible for:

- collecting user input
- calling the correct service
- catching exceptions through shared handlers
- showing success or failure feedback
- refreshing lists and tables after data changes

The UI layer does not directly implement business rules. It delegates that responsibility to the Service layer.

### 8.2 LibraryManagementApp.java

Purpose:

- JavaFX application entry point.
- Builds all repositories and services.
- Seeds demo data.
- Launches the main JavaFX scene.

Functions:

- `start(Stage stage)`
  Creates application context, builds `LibraryManagementUI`, sets the window title and scene, shows stage, and optionally runs a smoke test shutdown flow.
- `main(String[] args)`
  Prints demo account information and launches JavaFX application.
- `createContext()`
  Creates every repository and service instance, seeds demo users, seeds sample books, creates a pending submission file, and returns all dependencies bundled into `AppContext`.

Inner record:

- `AppContext(...)`
  Packages service objects so the UI constructor stays clean.

Why this file matters:

- It wires the whole project together.
- It proves the layered architecture can run end to end.

### 8.3 LibraryManagementUI.java

Purpose:

- Main JavaFX user interface.
- Contains the Student/Staff tab, Author tab, and Librarian tab.
- This is the central visual integration point of the whole task.

High-level responsibilities:

- build all JavaFX controls
- connect controls to service calls
- refresh tables and lists when state changes
- show alert dialogs for success and failure

Main functions:

- `LibraryManagementUI(...)`
  Receives all services through constructor injection.
- `createContent()`
  Creates the root layout, top heading, three tabs, and performs initial data refresh for books, recommendations, drafts, submissions, and borrows.

Student and Staff UI functions:

- `buildStudentStaffTab()`
  Builds the whole student/staff portal including registration form, login form, search bar, book table, summary area, borrow duration spinner, borrow button, recommendation list, and active borrow list.

What happens in this tab:

- registration form calls `authService.registerStudentOrStaff(...)`
- login form calls `authService.loginStudentOrStaff(...)`
- search bar calls `refreshBookResults()`
- table selection updates the summary area and borrow button state
- borrow button triggers `handleBorrow()`
- recommendations are shown through `RecommendationService`
- active borrows are shown through `BorrowService`

Author UI functions:

- `buildAuthorTab()`
  Builds the whole author portal including registration, login, draft list, submission form, preview area, auto-save button, preview button, and publish button.

What happens in this tab:

- author registration calls `authorService.registerAuthor(...)`
- author login calls `authorService.loginAuthor(...)`
- draft list selection calls `populateDraft(...)`
- auto-save button calls `authorDraftService.autoSave(...)`
- preview button calls `authorService.previewBook(...)`
- publish button validates file with `fileService.validateSubmissionFile(...)`, submits with `authorService.publishBook(...)`, clears saved draft, refreshes pending submission list, and clears the form

Librarian UI functions:

- `buildLibrarianTab()`
  Builds the whole librarian portal including registration, login, pending submission table, comment field, preview area, refresh button, preview button, approve button, reject button, bulk approve button, and bulk reject button.

What happens in this tab:

- librarian registration calls `librarianService.registerLibrarian(...)`
- librarian login calls `librarianService.loginLibrarian(...)`
- selecting a submission automatically previews the file if possible
- preview button reads file detail through `fileService.getPreviewDetails(...)`
- approve button calls `librarianService.approveSubmission(...)`
- reject button calls `librarianService.rejectSubmission(...)`
- bulk approve and bulk reject operate on all selected rows
- after approval, the book catalog is refreshed because a new approved `Book` has been created

Shared helper functions in this class:

- `createForm(String title, String[] labels, Node... controls)`
  Builds a reusable two-column form layout used by registration and login panes.
- `handleBorrow()`
  Executes full JavaFX borrow flow: verify login, verify selection, verify availability, show confirmation alert, call `borrowService.borrowBook(...)`, then refresh books, recommendations, and active borrows.
- `handleAction(Runnable action)`
  Wraps UI actions with shared exception handling and shows alert dialogs on failure.
- `refreshBookResults()`
  Reloads book table with either all approved books or search results.
- `refreshRecommendations()`
  Reloads popular recommendation list.
- `refreshActiveBorrows()`
  Reloads current student/staff borrow list.
- `refreshDrafts()`
  Reloads author draft list.
- `refreshPendingSubmissions()`
  Reloads pending submission table and refreshes book table too.
- `updateBorrowButtonState()`
  Disables borrow button when login or selection conditions are not met.
- `ensureAuthorLogin()`
  Throws validation error if author is not logged in.
- `ensureLibrarianLogin()`
  Throws validation error if librarian is not logged in.
- `requireSelectedSubmission()`
  Returns current selected submission or throws validation error.
- `afterSubmissionUpdate(String message)`
  Refreshes librarian and book views after approval or rejection, clears preview and comment field, and shows success dialog.
- `populateDraft(BookDraft2 draft)`
  Loads draft data into the author form fields.
- `clearAuthorForm()`
  Clears author input controls after submission.
- `parseGenres(String rawGenres)`
  Splits comma-separated text into clean genre list.
- `showError(String header, String message)`
  Shows JavaFX error alert.
- `showInfo(String header, String message)`
  Shows JavaFX information alert.

Why this file is the most important UI file:

- It integrates every major feature in one application.
- It shows end-to-end user flows for all three portals.
- It demonstrates how the six project parts work together.

### 8.4 ConsoleUI.java

Purpose:

- Console version of the student/staff portal.

Functions:

- `ConsoleUI(AuthService authService, BookService bookService, BorrowService borrowService, RecommendationService recommendationService)`
  Injects services needed by Task 1 flow.
- `start()`
  Starts console UI with a fresh scanner.
- `start(Scanner sc)`
  Shows menu loop for register, login, list books, borrow, and exit.
- `register(Scanner sc)`
  Reads student/staff registration input, registers account, and redirects to login.
- `login(Scanner sc)`
  Reads credentials, logs user in, and redirects to available books list.
- `listBooks()`
  Prints approved books, availability, summaries, and popular recommendations.
- `borrow(Scanner sc)`
  Reads book id and borrow duration, validates input, shows confirmation details, calls `borrowService.borrowBook(...)`, and prints result.

### 8.5 AuthorConsoleUI2.java

Purpose:

- Console version of the author portal.

Functions:

- `AuthorConsoleUI2(AuthorService2 authorService, AuthorDraftService draftService, FileService fileService)`
  Injects author-related services.
- `start(Scanner sc)`
  Runs author menu loop.
- `register(Scanner sc)`
  Registers author and redirects to login.
- `login(Scanner sc)`
  Authenticates author.
- `loadDraft(Scanner sc)`
  Lists drafts, asks for title, and displays loaded draft summary.
- `publish(Scanner sc)`
  Reads author submission fields, auto-saves draft, prints preview, optionally validates file, submits the book, and clears the draft after successful submission.

### 8.6 LibrarianConsoleUI3.java

Purpose:

- Console version of the librarian portal.

Functions:

- `LibrarianConsoleUI3(LibrarianService3 librarianService, FileService fileService)`
  Injects librarian services.
- `start(Scanner sc)`
  Runs librarian menu loop.
- `register(Scanner sc)`
  Registers librarian and redirects to login.
- `login(Scanner sc)`
  Authenticates librarian.
- `listPending()`
  Prints all pending submissions.
- `approve(Scanner sc)`
  Reads submission id, previews file, asks confirmation, collects comment, and approves submission.
- `reject(Scanner sc)`
  Reads submission id, previews file, asks confirmation, collects comment, and rejects submission.
- `previewSubmissionFile(Scanner sc)`
  Reads submission id and prints preview details.
- `bulkApprove(Scanner sc)`
  Reads multiple ids, previews all files, asks confirmation, and approves all.
- `bulkReject(Scanner sc)`
  Reads multiple ids, previews all files, asks confirmation, and rejects all.
- `showSubmissionFilePreview(String submissionId)`
  Locates selected pending submission and asks `FileService` for preview text.
- `confirm(Scanner sc, String message)`
  Shared yes/no prompt helper.
- `ensureLogin()`
  Throws validation error if librarian is not logged in.

### 8.7 EnhancementHelper.java

Purpose:

- Provides UI helper behavior for nice-to-have features.

Functions:

- `printAvailability(Book b)`
  Prints a text tag for available or unavailable book in console mode.
- `quickReadSummary(Book b)`
  Prints full summary if short, otherwise prints shortened preview.
- `confirmBorrow(String bookTitle, int durationDays)`
  Prints borrow confirmation details in console mode.
- `getAvailabilityColor(Book b)`
  Returns JavaFX color black for available and red for unavailable.
- `buildBorrowConfirmation(String bookTitle, int durationDays)`
  Builds the message text used by JavaFX borrow confirmation alert.
- `printBorrowResult(BorrowRecord record)`
  Prints borrow start date and due date.

## 9. End-to-End Feature Mapping

This section maps the whole task to the code.

### 9.1 Student and Staff Task Flow

1. Register account through `AuthService.registerStudentOrStaff(...)`
2. Login through `AuthService.loginStudentOrStaff(...)`
3. View books through `BookService.listApprovedBooksWithAvailability()`
4. Search books through `BookService.searchApprovedBooks(...)`
5. Borrow through `BorrowService.borrowBook(...)`
6. View active borrow list through `BorrowService.listActiveBorrowsByUser(...)`
7. View recommendations through `RecommendationService.recommendTopPopular(...)`

### 9.2 Author Task Flow

1. Register account through `AuthorService2.registerAuthor(...)`
2. Login through `AuthorService2.loginAuthor(...)`
3. Auto-save draft through `AuthorDraftService.autoSave(...)`
4. Reload draft through `AuthorDraftService.loadDraft(...)` and `loadDrafts(...)`
5. Preview submission through `AuthorService2.previewBook(...)`
6. Validate file through `FileService.validateSubmissionFile(...)`
7. Submit book through `AuthorService2.publishBook(...)`
8. Delete draft after successful publish through `AuthorDraftService.clearDraft(...)`

### 9.3 Librarian Task Flow

1. Register account through `LibrarianService3.registerLibrarian(...)`
2. Login through `LibrarianService3.loginLibrarian(...)`
3. View pending submissions through `LibrarianService3.getPendingSubmissions()`
4. Preview file through `FileService.getPreviewDetails(...)`
5. Approve through `LibrarianService3.approveSubmission(...)`
6. Reject through `LibrarianService3.rejectSubmission(...)`
7. Bulk approve and reject through `bulkApprove(...)` and `bulkReject(...)`
8. After approval, approved title appears in book catalog through `BookRepository` and `BookService`

## 10. Conclusion

This project is a clear example of a six-layer library system:

- Model defines the data.
- Repository stores the data.
- Service enforces the rules.
- Exception handles failure cases.
- Security protects core operations.
- UI exposes the whole system to users.

Among all parts, UI is the most important from the demonstration perspective because it connects every feature together and shows the complete student, author, and librarian workflows in both JavaFX and console forms.
