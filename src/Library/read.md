# S2026_Project Group 21

# COMP3111 Project

## Library Management System (E-Book Library System)

## 1. Project Summary

This project is a Library Management System for COMP3111. It supports three user portals and four user roles:

- Student
- Staff
- Author
- Librarian

The system is implemented with a layered design. Even though many core features are implemented in the Service layer, each function also belongs to a course task, so this document explains the project in two ways:

- by system architecture
- by Task 1.x, 2.x, and 3.x requirements

This is important because, for example, `AuthorService2` is a Service class, but its functions are also part of Task 2.1, Task 2.2, and Task 2.3.

## 2. Requirements for All Tasks and All Phases

### 2.1 Non-functional Requirements

The project addresses the common requirements for all tasks:

- The application has a user-friendly interface through the JavaFX UI.
- The interface is consistent across student, staff, author, and librarian flows.
- User actions follow similar patterns: register, login, perform task-specific operations, and receive feedback.
- User credentials are securely stored using hashed passwords instead of raw passwords.
- The project uses readable package separation so the code is easier to maintain.

### 2.2 Technical Requirements

The project addresses the technical requirements in the following ways:

- Readability and maintainability are supported by package-based separation into Model, Repository, Service, Exception, Security, and UI.
- Appropriate data structures are used for users, books, drafts, submissions, and borrow records.
- Business rules are kept in Service classes instead of being mixed into UI classes.
- Exceptions are centralized so error handling is clear and reusable.

### 2.3 Submission and README Requirement

This file serves as the README-style explanation of the project. It describes:

- setup understanding
- architecture
- task breakdown
- major classes and functions
- feature mapping from the specification to the implementation

## 3. System Architecture: The 6 Parts

The project is divided into 6 parts:

1. Model
2. Repository
3. Service
4. Exception
5. Security
6. UI

### 3.1 Model

The Model package defines system data objects.

- `User` stores username, full name, password hash, role, and created time.
- `Role` defines `STUDENT`, `STAFF`, `AUTHOR`, and `LIBRARIAN`.
- `Book` stores approved library books.
- `BorrowRecord` stores borrow transactions.
- `AuthorProfile2` stores author bio.
- `LibrarianProfile3` stores employee id.
- `BookDraft2` stores author drafts for auto-save.
- `BookSubmission2` stores submitted books waiting for librarian review.
- `SubmissionState` defines `PENDING`, `APPROVED`, and `REJECTED`.

### 3.2 Repository

The Repository package stores and retrieves data.

- `UserRepository` and `MemoryUserRepository`
- `BookRepository` and `MemoryBookRepository`
- `BorrowRepository` and `MemoryBorrowRepository`
- `AuthorProfileRepository2` and `MemoryAuthorProfileRepository2`
- `BookDraftRepository2` and `MemoryBookDraftRepository2`
- `BookSubmissionRepository2` and `MemoryBookSubmissionRepository2`
- `LibrarianProfileRepository3` and `MemoryLibrarianProfileRepository3`

All current implementations are in-memory, which is sufficient for the project scope and demo workflow.

### 3.3 Service

The Service package contains the main business logic.

- `AuthService` supports Task 1 registration and login.
- `BookService` supports Task 1.3 available book screen.
- `BorrowService` supports Task 1.4 borrow book.
- `RecommendationService` supports Task 1 nice-to-have recommendations.
- `AuthorService2` supports Task 2.1, 2.2, and 2.3.
- `AuthorDraftService` supports Task 2.3 nice-to-have draft auto-save.
- `FileService` supports Task 2.3 and Task 3.3 file validation and preview.
- `LibrarianService3` supports Task 3.1, 3.2, and 3.3.

### 3.4 Exception

The Exception package defines reusable runtime exceptions.

- `AuthenticationException`
- `BusinessException`
- `NotFoundException`
- `ValidationException`

### 3.5 Security

The Security package centralizes security and shared rule helpers.

- `PasswordHasher`
- `PasswordPolicy`
- `SecurityConfig`
- `SessionManager`

### 3.6 UI

The UI package is the visible interaction layer.

- `LibraryManagementApp` is the JavaFX application entry point.
- `LibraryManagementUI` is the main JavaFX screen and the most important UI class.
- `EnhancementHelper` supports several UI nice-to-have behaviors.

## 4. Main Classes and Important Functions by Part

This section gives a compact reference of the major classes before the task-based breakdown.

### 4.1 Model Functions

#### User

- `User(String username, String fullName, String passwordHash, Role role)` creates a user.
- `getUsername()` returns username.
- `getFullName()` returns full name.
- `getPasswordHash()` returns hashed password.
- `getRole()` returns role.
- `getCreatedAt()` returns creation time.
- `equals(Object obj)` compares users by username.
- `hashCode()` generates hash from username.

#### Book

- `Book(String title, String authorFullName, String summary)` creates an unpublished book.
- `getId()` returns book id.
- `getTitle()` returns title.
- `getAuthorFullName()` returns author name.
- `getPublishDate()` returns approval date.
- `isApproved()` checks publish approval.
- `isAvailable()` checks borrow availability.
- `getSummary()` returns summary.
- `approve(LocalDate publishDate)` marks the book approved and available.
- `setAvailable(boolean available)` updates borrow state.

#### BorrowRecord

- `BorrowRecord(String username, String bookId, LocalDate borrowDate, LocalDate dueDate)` creates a borrow record.
- `getId()` returns record id.
- `getUsername()` returns borrower username.
- `getBookId()` returns borrowed book id.
- `getBorrowDate()` returns borrow date.
- `getDueDate()` returns due date.
- `isReturned()` checks whether the record is closed.
- `markReturned()` marks the record returned.

#### AuthorProfile2

- `AuthorProfile2(String username, String bio)` creates author profile.
- `getUsername()` returns author username.
- `getBio()` returns author bio.

#### LibrarianProfile3

- `LibrarianProfile3(String username, String employeeId)` creates librarian profile.
- `getUsername()` returns librarian username.
- `getEmployeeId()` returns employee id.

#### BookDraft2

- `BookDraft2(String authorUsername)` creates an empty draft.
- `getAuthorUsername()` returns draft owner.
- `getTitle()` returns draft title.
- `getGenres()` returns selected genres.
- `getDescription()` returns draft description.
- `getFilePath()` returns draft file path.
- `getLastSavedAt()` returns last auto-save time.
- `setTitle(String title)` updates title and refreshes time.
- `setGenres(List<String> genres)` updates genres and refreshes time.
- `setDescription(String description)` updates description and refreshes time.
- `setFilePath(String filePath)` updates file path and refreshes time.

#### BookSubmission2

- `BookSubmission2(...)` creates a new pending submission.
- `getId()` returns submission id.
- `getTitle()` returns title.
- `getAuthorUsername()` returns author username.
- `getAuthorFullName()` returns author full name.
- `getGenres()` returns genres.
- `getDescription()` returns description.
- `getFileName()` returns file path or name.
- `getSubmittedDate()` returns submitted date.
- `getStatus()` returns submission state.
- `getLibrarianComment()` returns librarian comment.
- `getApprovedDate()` returns approved date.
- `approve(String comment)` changes state to approved.
- `reject(String comment)` changes state to rejected.

### 4.2 Repository Functions

#### UserRepository and MemoryUserRepository

- `findByUsername(String username)` finds user by username.
- `save(User user)` stores user.
- `existsByUsername(String username)` checks duplicate username.
- `findAll()` returns all users.

#### BookRepository and MemoryBookRepository

- `save(Book book)` stores book.
- `findById(String id)` finds book by id.
- `findAll()` returns all books.
- `searchByTitleOrAuthor(String keyword)` searches by title or author.

#### BorrowRepository and MemoryBorrowRepository

- `save(BorrowRecord record)` stores borrow record.
- `findById(String id)` finds record by id.
- `findByUsername(String username)` returns borrow history of a user.
- `findActiveByUsernameAndBookId(String username, String bookId)` finds active borrow relation.
- `findAll()` returns all borrow records.

#### AuthorProfileRepository2 and MemoryAuthorProfileRepository2

- `save(AuthorProfile2 profile)` stores author profile.
- `findByUsername(String username)` finds profile by username.

#### BookDraftRepository2 and MemoryBookDraftRepository2

- `save(BookDraft2 draft)` stores draft.
- `findByAuthorUsernameAndTitle(String authorUsername, String title)` finds a specific draft.
- `findAllByAuthorUsername(String authorUsername)` returns all drafts of the author.
- `deleteByAuthorUsernameAndTitle(String authorUsername, String title)` deletes a draft.

#### BookSubmissionRepository2 and MemoryBookSubmissionRepository2

- `save(BookSubmission2 submission)` stores submission.
- `findById(String id)` finds submission by id.
- `findAll()` returns all submissions.
- `findByStatus(SubmissionState status)` filters submissions by status.
- `findByAuthorUsername(String username)` returns submissions of one author.

#### LibrarianProfileRepository3 and MemoryLibrarianProfileRepository3

- `save(LibrarianProfile3 profile)` stores librarian profile.
- `findByUsername(String username)` finds librarian profile.

### 4.3 Service Functions

#### AuthService

- `registerStudentOrStaff(String username, String fullName, String password, Role role)` handles Task 1.1 registration.
- `loginStudentOrStaff(String username, String password, Role expectedRole)` handles Task 1.2 login.
- `logout()` clears current session.
- `isLoggedIn()` checks session state.
- `getCurrentUser()` returns current session user.

#### BookService

- `listApprovedBooksWithAvailability()` supports Task 1.3 book listing.
- `searchApprovedBooks(String keyword)` supports Task 1.3 book searching.

#### BorrowService

- `borrowBook(String username, String bookId)` supports Task 1.4 borrow with default days.
- `borrowBook(String username, String bookId, int borrowDays)` supports Task 1.4 full borrow flow.
- `returnBook(String username, String bookId)` handles return logic.
- `listActiveBorrowsByUser(String username)` shows active borrows.

#### RecommendationService

- `recommendTopPopular(int limit)` supports Task 1 nice-to-have recommendations.

#### AuthorService2

- `registerAuthor(String username, String fullName, String password, String bio)` handles Task 2.1.
- `loginAuthor(String username, String password)` handles Task 2.2.
- `publishBook(String authorUsername, String title, List<String> genres, String description, String fileName)` handles Task 2.3.
- `getSupportedGenres()` supports Task 2.3 multiple-genre selection.
- `previewBook(String title, List<String> genres, String description)` supports Task 2.3 preview.

#### AuthorDraftService

- `autoSave(String authorUsername, String title, List<String> genres, String description, String filePath)` supports Task 2.3 auto-save draft.
- `loadDraft(String authorUsername, String title)` loads one draft.
- `loadDrafts(String authorUsername)` loads all drafts.
- `clearDraft(String authorUsername, String title)` removes saved draft after submission.

#### FileService

- `validateSubmissionFile(String filePath)` validates uploaded file before submission.
- `getPreviewDetails(String filePath)` supports librarian content preview.

#### LibrarianService3

- `registerLibrarian(String username, String fullName, String password, String employeeId)` handles Task 3.1.
- `loginLibrarian(String username, String password)` handles Task 3.2.
- `getPendingSubmissions()` supports Task 3.3 pending review screen.
- `approveSubmission(String submissionId, String comment)` supports Task 3.3 approve flow.
- `rejectSubmission(String submissionId, String comment)` supports Task 3.3 reject flow.
- `bulkApprove(List<String> submissionIds, String comment)` supports Task 3.3 bulk approve.
- `bulkReject(List<String> submissionIds, String comment)` supports Task 3.3 bulk reject.

### 4.4 Exception Functions

- `AuthenticationException(String message)` and `AuthenticationException(String message, Throwable cause)` are used for login failures.
- `BusinessException(String message)` and `BusinessException(String message, Throwable cause)` are used for business rule errors.
- `NotFoundException(String message)` is used for missing books or submissions.
- `ValidationException(String message)` is used for invalid input.

### 4.5 Security Functions

#### PasswordHasher

- `sha256(String rawPassword)` provides compatibility.
- `hashPassword(String rawPassword)` hashes passwords.
- `matches(String rawPassword, String storedHash)` verifies passwords.

#### PasswordPolicy

- `validate(String password)` checks password strength.

#### SecurityConfig

- `MAX_BORROW_LIMIT` defines the borrow cap.
- `DEFAULT_BORROW_DAYS` defines default borrow duration.
- `MAX_FILE_SIZE_BYTES` defines file upload limit.
- `ALLOWED_EXTENSIONS` defines accepted upload types.

#### SessionManager

- `getInstance()` returns singleton session manager.
- `createSession(User user)` opens a session.
- `destroySession()` clears session.
- `getCurrentUser()` returns current session user.
- `isAuthenticated()` checks whether a session exists.
- `getLoginTime()` returns login time.
- `setAttribute(String key, Object value)` stores session data.
- `getAttribute(String key)` reads session data.

### 4.6 UI Functions

#### LibraryManagementApp

- `start(Stage stage)` launches JavaFX UI.
- `main(String[] args)` starts the application.
- `createContext()` wires repositories, services, and demo data.

#### LibraryManagementUI

- `createContent()` builds the main JavaFX screen.
- `buildStudentStaffTab()` builds Task 1 UI.
- `buildAuthorTab()` builds Task 2 UI.
- `buildLibrarianTab()` builds Task 3 UI.
- `handleBorrow()` executes borrow flow.
- `refreshBookResults()` refreshes book table.
- `refreshRecommendations()` refreshes recommendation list.
- `refreshActiveBorrows()` refreshes active borrow list.
- `refreshDrafts()` refreshes author draft list.
- `refreshPendingSubmissions()` refreshes librarian submission list.
- `updateBorrowButtonState()` enables or disables borrow button.
- `populateDraft(BookDraft2 draft)` loads draft into author form.
- `parseGenres(String rawGenres)` parses multi-genre input.
- `showError(String header, String message)` shows error dialog.
- `showInfo(String header, String message)` shows information dialog.

#### ConsoleUI

- `start()` and `start(Scanner sc)` run Student/Staff console portal.
- `register(Scanner sc)` supports Task 1.1.
- `login(Scanner sc)` supports Task 1.2.
- `listBooks()` supports Task 1.3.
- `borrow(Scanner sc)` supports Task 1.4.

#### AuthorConsoleUI2

- `start(Scanner sc)` runs Author portal.
- `register(Scanner sc)` supports Task 2.1.
- `login(Scanner sc)` supports Task 2.2.
- `loadDraft(Scanner sc)` supports Task 2.3 draft reload.
- `publish(Scanner sc)` supports Task 2.3 submission.

#### LibrarianConsoleUI3

- `start(Scanner sc)` runs Librarian portal.
- `register(Scanner sc)` supports Task 3.1.
- `login(Scanner sc)` supports Task 3.2.
- `listPending()` supports Task 3.3.
- `approve(Scanner sc)` supports approve flow.
- `reject(Scanner sc)` supports reject flow.
- `previewSubmissionFile(Scanner sc)` supports file preview.
- `bulkApprove(Scanner sc)` supports bulk approval.
- `bulkReject(Scanner sc)` supports bulk rejection.

#### EnhancementHelper

- `printAvailability(Book b)` prints availability tag.
- `quickReadSummary(Book b)` supports quick summary reading.
- `confirmBorrow(String bookTitle, int durationDays)` prints borrow confirmation details.
- `getAvailabilityColor(Book b)` supports red or black availability display.
- `buildBorrowConfirmation(String bookTitle, int durationDays)` builds borrow dialog content.
- `printBorrowResult(BorrowRecord record)` prints borrow success output.

## 5. Phase 1 Main Features

## 5.1 Task 1 Student/Staff Portal

Task 1 includes four main subtasks:

1. Student/Staff Registration
2. Student/Staff Login
3. Available Book Screen
4. Borrow Book

### 5.1.1 Task 1.1 Student/Staff Registration

Requirement summary:

- User enters username, full name, password, and role.
- Username must be unique.
- Password must satisfy validation rules.
- Role must be Student or Staff.
- User should receive success or failure feedback.

Files involved by part:

- Model: `User`, `Role`
- Repository: `UserRepository`, `MemoryUserRepository`
- Service: `AuthService.registerStudentOrStaff(...)`
- Exception: `ValidationException`
- Security: `PasswordHasher`, `PasswordPolicy`, `SessionManager`
- UI: `LibraryManagementUI.buildStudentStaffTab()`, `ConsoleUI.register(Scanner sc)`

Detailed function mapping:

- `AuthService.registerStudentOrStaff(...)`
  Checks that role is only `STUDENT` or `STAFF`.
  Checks that username and full name are not empty.
  Checks password strength.
  Checks duplicate username across the repository.
  Hashes the password before storage.
  Creates and saves the new `User`.

How the UI handles it:

- In JavaFX, the register form in `buildStudentStaffTab()` collects the fields and shows success or error alerts.
- In Console UI, `register(Scanner sc)` reads terminal input and redirects to login after success.

### 5.1.2 Task 1.2 Student/Staff Login

Requirement summary:

- User enters username and password.
- System validates credentials.
- System checks that the account belongs to the selected role.
- User receives success or failure feedback.

Files involved by part:

- Model: `User`, `Role`
- Repository: `UserRepository`, `MemoryUserRepository`
- Service: `AuthService.loginStudentOrStaff(...)`
- Exception: `AuthenticationException`, `ValidationException`
- Security: `PasswordHasher`, `SessionManager`
- UI: `LibraryManagementUI.buildStudentStaffTab()`, `ConsoleUI.login(Scanner sc)`

Detailed function mapping:

- `AuthService.loginStudentOrStaff(...)`
  Finds the user by username.
  Checks whether the selected role matches the stored account role.
  Verifies password hash.
  Creates a session on success.

How the UI handles it:

- JavaFX updates the status label and refreshes active borrows.
- Console UI prints welcome message and redirects to the available book screen.

### 5.1.3 Task 1.3 Available Book Screen

Requirement summary:

- Show approved books.
- Show title, author, publish date, availability status, and summary.

Files involved by part:

- Model: `Book`
- Repository: `BookRepository`, `MemoryBookRepository`
- Service: `BookService.listApprovedBooksWithAvailability()`, `BookService.searchApprovedBooks(...)`, `RecommendationService.recommendTopPopular(...)`
- Exception: `ValidationException`, `BusinessException`
- Security: shared limits from `SecurityConfig`
- UI: `LibraryManagementUI.buildStudentStaffTab()`, `ConsoleUI.listBooks()`, `EnhancementHelper`

Detailed function mapping:

- `BookService.listApprovedBooksWithAvailability()`
  Returns only approved books and sorts them by title.
- `BookService.searchApprovedBooks(...)`
  Searches title or author and still filters only approved books.
- `EnhancementHelper.getAvailabilityColor(Book b)`
  Supports red or black text in JavaFX.
- `EnhancementHelper.printAvailability(Book b)`
  Supports console display of availability.
- `EnhancementHelper.quickReadSummary(Book b)`
  Supports short or truncated summary reading.

How the UI handles it:

- JavaFX uses a `TableView` to show books and a text area to show the selected summary.
- Console UI prints book information line by line.

### 5.1.4 Task 1.4 Borrow Book

Requirement summary:

- Borrow must only succeed if the book is available.
- Book status must change after borrow.
- User receives confirmation after successful borrow.

Files involved by part:

- Model: `Book`, `BorrowRecord`
- Repository: `BookRepository`, `BorrowRepository`
- Service: `BorrowService.borrowBook(...)`, `BorrowService.listActiveBorrowsByUser(...)`
- Exception: `BusinessException`, `NotFoundException`, `ValidationException`
- Security: `SecurityConfig.MAX_BORROW_LIMIT`, `SecurityConfig.DEFAULT_BORROW_DAYS`
- UI: `LibraryManagementUI.handleBorrow()`, `ConsoleUI.borrow(Scanner sc)`, `EnhancementHelper`

Detailed function mapping:

- `BorrowService.borrowBook(String username, String bookId, int borrowDays)`
  Finds the selected book.
  Checks that the book exists and is approved.
  Checks availability.
  Checks that the user has not exceeded the borrow limit.
  Checks that borrow duration is valid.
  Creates a `BorrowRecord`.
  Saves the borrow record.
  Marks the book unavailable.
- `EnhancementHelper.buildBorrowConfirmation(...)`
  Builds the message shown in JavaFX confirmation alert.
- `EnhancementHelper.confirmBorrow(...)`
  Prints confirmation detail in console mode.
- `EnhancementHelper.printBorrowResult(...)`
  Prints borrow success output.

How the UI handles it:

- JavaFX shows a confirmation alert before calling the service.
- Console UI asks user to confirm with `Y/N`.

## 5.2 Task 2 Author Portal

Task 2 includes three main subtasks:

1. Author Registration
2. Author Login
3. Publish New Book

### 5.2.1 Task 2.1 Author Registration

Requirement summary:

- User enters username, full name, password, and optional bio.
- Username must be unique.
- Password must pass validation.
- User receives feedback.

Files involved by part:

- Model: `User`, `Role`, `AuthorProfile2`
- Repository: `UserRepository`, `AuthorProfileRepository2`
- Service: `AuthorService2.registerAuthor(...)`
- Exception: `ValidationException`
- Security: `PasswordHasher`, `PasswordPolicy`, `SessionManager`
- UI: `LibraryManagementUI.buildAuthorTab()`, `AuthorConsoleUI2.register(Scanner sc)`

Detailed function mapping:

- `AuthorService2.registerAuthor(...)`
  Validates username and full name.
  Validates password strength.
  Checks duplicate username.
  Hashes password.
  Creates AUTHOR user.
  Creates and stores `AuthorProfile2`.

### 5.2.2 Task 2.2 Author Login

Requirement summary:

- Author enters username and password.
- Credentials are validated against repository data.
- System ensures that the account role is AUTHOR.

Files involved by part:

- Model: `User`, `Role`
- Repository: `UserRepository`
- Service: `AuthorService2.loginAuthor(...)`
- Exception: `AuthenticationException`
- Security: `PasswordHasher`, `SessionManager`
- UI: `LibraryManagementUI.buildAuthorTab()`, `AuthorConsoleUI2.login(Scanner sc)`

Detailed function mapping:

- `AuthorService2.loginAuthor(...)`
  Finds user by username.
  Checks that role is AUTHOR.
  Verifies password hash.
  Creates session.

### 5.2.3 Task 2.3 Publish New Book

Requirement summary:

- Author submits title, author name, genre, description, and file.
- The request is sent to librarian for approval.
- User receives submission confirmation.

Files involved by part:

- Model: `BookSubmission2`, `BookDraft2`, `User`
- Repository: `BookSubmissionRepository2`, `BookDraftRepository2`, `UserRepository`
- Service: `AuthorService2.publishBook(...)`, `AuthorService2.previewBook(...)`, `AuthorService2.getSupportedGenres()`, `AuthorDraftService.autoSave(...)`, `AuthorDraftService.loadDraft(...)`, `AuthorDraftService.loadDrafts(...)`, `AuthorDraftService.clearDraft(...)`, `FileService.validateSubmissionFile(...)`
- Exception: `ValidationException`, `AuthenticationException`
- Security: `SecurityConfig.ALLOWED_EXTENSIONS`, `SecurityConfig.MAX_FILE_SIZE_BYTES`
- UI: `LibraryManagementUI.buildAuthorTab()`, `AuthorConsoleUI2.publish(Scanner sc)`, `AuthorConsoleUI2.loadDraft(Scanner sc)`

Detailed function mapping:

- `AuthorService2.publishBook(...)`
  Validates title, genres, description, and file name.
  Validates genre values.
  Validates allowed file format.
  Verifies that the submitting user is an AUTHOR.
  Creates and stores `BookSubmission2` with status `PENDING`.
- `AuthorService2.previewBook(...)`
  Generates a readable preview of title, genres, and description before submission.
- `AuthorService2.getSupportedGenres()`
  Supplies the predefined genre list.
- `AuthorDraftService.autoSave(...)`
  Saves incomplete form data as draft.
- `AuthorDraftService.loadDraft(...)` and `loadDrafts(...)`
  Allow the author to continue unfinished submission work.
- `AuthorDraftService.clearDraft(...)`
  Removes draft after a successful final submission.
- `FileService.validateSubmissionFile(...)`
  Checks file existence, extension, and size.

How the UI handles it:

- JavaFX supports draft save, preview, and final submission in one screen.
- Console UI also supports draft save, preview, and optional submission.

## 5.3 Task 3 Librarian Portal

Task 3 includes three main subtasks:

1. Librarian Registration
2. Librarian Login
3. Librarian New Books Approval Screen and Functionalities

### 5.3.1 Task 3.1 Librarian Registration

Requirement summary:

- User enters username, full name, password, and optional employee id.
- Username must be unique.
- Password must pass validation.
- User receives feedback.

Files involved by part:

- Model: `User`, `Role`, `LibrarianProfile3`
- Repository: `UserRepository`, `LibrarianProfileRepository3`
- Service: `LibrarianService3.registerLibrarian(...)`
- Exception: `ValidationException`
- Security: `PasswordHasher`, `PasswordPolicy`
- UI: `LibraryManagementUI.buildLibrarianTab()`, `LibrarianConsoleUI3.register(Scanner sc)`

Detailed function mapping:

- `LibrarianService3.registerLibrarian(...)`
  Validates fields.
  Validates password strength.
  Checks duplicate username.
  Hashes password.
  Creates LIBRARIAN user.
  Creates and stores `LibrarianProfile3`.

### 5.3.2 Task 3.2 Librarian Login

Requirement summary:

- User enters username and password.
- Credentials are validated.
- System checks that the role is LIBRARIAN.

Files involved by part:

- Model: `User`, `Role`
- Repository: `UserRepository`
- Service: `LibrarianService3.loginLibrarian(...)`
- Exception: `AuthenticationException`
- Security: `PasswordHasher`, `SessionManager`
- UI: `LibraryManagementUI.buildLibrarianTab()`, `LibrarianConsoleUI3.login(Scanner sc)`

Detailed function mapping:

- `LibrarianService3.loginLibrarian(...)`
  Finds user by username.
  Checks librarian role.
  Verifies password hash.
  Creates session.

### 5.3.3 Task 3.3 Librarian New Books Approval Screen and Functionalities

Requirement summary:

- Show pending submissions.
- Show title, author username, author full name, genre, submitted date, and pending status.
- Allow approve or reject with confirmation.
- Update status and provide feedback.

Files involved by part:

- Model: `BookSubmission2`, `SubmissionState`, `Book`
- Repository: `BookSubmissionRepository2`, `BookRepository`
- Service: `LibrarianService3.getPendingSubmissions()`, `LibrarianService3.approveSubmission(...)`, `LibrarianService3.rejectSubmission(...)`, `LibrarianService3.bulkApprove(...)`, `LibrarianService3.bulkReject(...)`, `FileService.getPreviewDetails(...)`
- Exception: `ValidationException`, `NotFoundException`
- Security: file limits and extensions from `SecurityConfig`
- UI: `LibraryManagementUI.buildLibrarianTab()`, `LibrarianConsoleUI3.listPending()`, `approve(Scanner sc)`, `reject(Scanner sc)`, `previewSubmissionFile(Scanner sc)`, `bulkApprove(Scanner sc)`, `bulkReject(Scanner sc)`

Detailed function mapping:

- `LibrarianService3.getPendingSubmissions()`
  Returns only submissions with `PENDING` state.
- `LibrarianService3.approveSubmission(...)`
  Finds the target submission.
  Checks that it is still pending.
  Marks it approved.
  Saves the updated submission.
  Creates a new `Book` from the approved submission.
  Marks the book approved and available.
  Saves the book to the library catalog.
- `LibrarianService3.rejectSubmission(...)`
  Finds the target submission.
  Checks that it is still pending.
  Marks it rejected and stores comment.
- `LibrarianService3.bulkApprove(...)`
  Applies approval to multiple submission ids.
- `LibrarianService3.bulkReject(...)`
  Applies rejection to multiple submission ids.
- `FileService.getPreviewDetails(...)`
  Allows librarian to preview text-based content and file metadata.

How the UI handles it:

- JavaFX uses a table, selection model, preview area, comment field, and action buttons.
- Console UI shows pending data and confirmation prompts.

## 6. Phase 1 Nice-to-have Features

## 6.1 Registration Checks for Task 1.1, 2.1, 3.1

Implemented logic:

- Unique username across all user types through repository lookup.
- Full name empty check.
- Strong password validation including uppercase, lowercase, digit, special character, no spaces, and length control.
- Hashed password storage instead of plain text.

Main functions:

- `AuthService.registerStudentOrStaff(...)`
- `AuthorService2.registerAuthor(...)`
- `LibrarianService3.registerLibrarian(...)`
- `PasswordPolicy.validate(...)`
- `PasswordHasher.hashPassword(...)`

## 6.2 Login Type Checking for Task 1.2, 2.2, 3.2

Implemented logic:

- Role-specific login checking so a user cannot log in through the wrong portal.

Main functions:

- `AuthService.loginStudentOrStaff(...)`
- `AuthorService2.loginAuthor(...)`
- `LibrarianService3.loginLibrarian(...)`

## 6.3 Nice-to-have for Task 1.3

### Reading Summary

Implemented logic:

- Summary is shown in JavaFX selection panel.
- Console quick-read summary is supported.

Main functions:

- `EnhancementHelper.quickReadSummary(Book b)`
- `LibraryManagementUI.buildStudentStaffTab()`

### Limit Borrow

Implemented logic:

- Maximum active borrow count is enforced.

Main functions:

- `BorrowService.borrowBook(...)`
- `SecurityConfig.MAX_BORROW_LIMIT`

### Book Recommendations

Implemented logic:

- Popularity-based recommendation is supported using borrow count.

Main functions:

- `RecommendationService.recommendTopPopular(int limit)`

## 6.4 Nice-to-have for Task 1.4

### Borrow Confirmation with Details

Implemented logic:

- JavaFX confirmation dialog shows book title, duration, due date, and warning.
- Console flow also shows confirmation detail.

Main functions:

- `LibraryManagementUI.handleBorrow()`
- `EnhancementHelper.buildBorrowConfirmation(...)`
- `EnhancementHelper.confirmBorrow(...)`

### Book Availability Coloring

Implemented logic:

- Available books are shown in black.
- Unavailable books are shown in red.

Main functions:

- `EnhancementHelper.getAvailabilityColor(Book b)`
- `LibraryManagementUI.buildStudentStaffTab()`

## 6.5 Nice-to-have for Task 2.3

### Book Preview

Main functions:

- `AuthorService2.previewBook(...)`
- `LibraryManagementUI.buildAuthorTab()`
- `AuthorConsoleUI2.publish(Scanner sc)`

### Multiple Genre Selection

Main functions:

- `AuthorService2.getSupportedGenres()`
- `LibraryManagementUI.parseGenres(String rawGenres)`

### Auto-Save Draft

Main functions:

- `AuthorDraftService.autoSave(...)`
- `AuthorDraftService.loadDraft(...)`
- `AuthorDraftService.loadDrafts(...)`
- `AuthorDraftService.clearDraft(...)`

## 6.6 Nice-to-have for Task 3.3

### Book Content Review

Main functions:

- `FileService.getPreviewDetails(...)`
- `LibraryManagementUI.buildLibrarianTab()`
- `LibrarianConsoleUI3.showSubmissionFilePreview(String submissionId)`

### Bulk Actions

Main functions:

- `LibrarianService3.bulkApprove(...)`
- `LibrarianService3.bulkReject(...)`
- `LibraryManagementUI.buildLibrarianTab()`
- `LibrarianConsoleUI3.bulkApprove(Scanner sc)`
- `LibrarianConsoleUI3.bulkReject(Scanner sc)`

## 7. UI-Focused Explanation

UI is the most important part for demonstration because it is where all tasks become visible and testable.

### 7.1 Why UI Is Central in This Project

- It exposes every task requirement to the user.
- It connects the six architecture parts together.
- It demonstrates whether the project is complete from the grader's perspective.

### 7.2 Most Important UI Class: LibraryManagementUI

`LibraryManagementUI` is the main JavaFX integration class.

It is the most important UI class because it includes:

- Student/Staff registration and login
- available book screen
- borrow action
- recommendation list
- active borrow list
- Author registration and login
- author draft list
- preview and publish form
- Librarian registration and login
- pending submission screen
- approve, reject, preview, and bulk actions

Important methods:

- `createContent()` builds the root UI.
- `buildStudentStaffTab()` maps to Task 1.
- `buildAuthorTab()` maps to Task 2.
- `buildLibrarianTab()` maps to Task 3.
- refresh methods keep data displayed correctly after each operation.
- helper methods centralize common UI behavior.

### 7.3 Console UI Classes

The console UIs are simpler, but they still map directly to the tasks:

- `ConsoleUI` maps to Task 1.
- `AuthorConsoleUI2` maps to Task 2.
- `LibrarianConsoleUI3` maps to Task 3.

They are useful because they prove the business logic is not dependent on JavaFX only.

## 8. End-to-End Mapping from Requirement to Code

### 8.1 Task 1 Mapping

- Task 1.1 -> `AuthService.registerStudentOrStaff(...)`
- Task 1.2 -> `AuthService.loginStudentOrStaff(...)`
- Task 1.3 -> `BookService.listApprovedBooksWithAvailability()`, `BookService.searchApprovedBooks(...)`
- Task 1.4 -> `BorrowService.borrowBook(...)`

### 8.2 Task 2 Mapping

- Task 2.1 -> `AuthorService2.registerAuthor(...)`
- Task 2.2 -> `AuthorService2.loginAuthor(...)`
- Task 2.3 -> `AuthorService2.publishBook(...)`, `AuthorService2.previewBook(...)`, `AuthorDraftService`, `FileService.validateSubmissionFile(...)`

### 8.3 Task 3 Mapping

- Task 3.1 -> `LibrarianService3.registerLibrarian(...)`
- Task 3.2 -> `LibrarianService3.loginLibrarian(...)`
- Task 3.3 -> `LibrarianService3.getPendingSubmissions()`, `approveSubmission(...)`, `rejectSubmission(...)`, `bulkApprove(...)`, `bulkReject(...)`, `FileService.getPreviewDetails(...)`

## 9. Conclusion

This project satisfies the Phase 1 feature structure by implementing the system in both architectural layers and task-based flows.

The most important point is this:

- a class can belong to a package role such as Service
- and at the same time its functions belong to specific course subtasks such as Task 1.1, Task 2.3, or Task 3.3

That is why this document explains both the six project parts and the task breakdown.

From the grading perspective, the clearest mapping is:

- Task 1 is mainly handled by `AuthService`, `BookService`, `BorrowService`, `RecommendationService`, `ConsoleUI`, and the Student/Staff part of `LibraryManagementUI`
- Task 2 is mainly handled by `AuthorService2`, `AuthorDraftService`, `FileService`, `AuthorConsoleUI2`, and the Author part of `LibraryManagementUI`
- Task 3 is mainly handled by `LibrarianService3`, `FileService`, `LibrarianConsoleUI3`, and the Librarian part of `LibraryManagementUI`

The whole project is connected through the six parts:

- Model
- Repository
- Service
- Exception
- Security
- UI