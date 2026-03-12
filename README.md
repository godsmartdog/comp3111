## 5. Phase 1 Main Features

## 5.1 Task 1 Student/Staff Portal

Task 1 contains four main subtasks:

1. Student/Staff Registration
2. Student/Staff Login
3. Available Book Screen
4. Borrow Book

### 5.1.1 Task 1.1 Student/Staff Registration

Requirements Overview:

- Users input username, full name, password, and role
- Username must be unique
- Password must meet validation rules
- Role can only be Student or Staff
- The system must provide success or failure feedback

Involved files mapped to the six layers are as follows:

- Model: `User`, `Role`
- Repository: `UserRepository`, `MemoryUserRepository`
- Service: `AuthService.registerStudentOrStaff(...)`
- Exception: `ValidationException`
- Security: `PasswordHasher`, `PasswordPolicy`, `SessionManager`
- UI: `LibraryManagementUI.buildStudentStaffTab()`, `ConsoleUI.register(Scanner sc)`

Function Details:

- `AuthService.registerStudentOrStaff(...)`
  Check that the role can only be `STUDENT` or `STAFF`
  Check that the username and full name cannot be empty
  Check password strength
  Check if the username already exists
  Save password using hash
  Create and save the new `User`

UI Implementation:

- JavaFX provides a registration form in `buildStudentStaffTab()` and displays success or failure messages via pop-ups
- Console version reads input via `register(Scanner sc)` and redirects to login upon success

### 5.1.2 Task 1.2 Student/Staff Login

Requirements Overview:

- Users input username and password
- The system verifies if the credentials are correct
- The system verifies if the account belongs to the selected role
- The system returns a success or failure prompt

Involved files mapped to the six layers are as follows:

- Model: `User`, `Role`
- Repository: `UserRepository`, `MemoryUserRepository`
- Service: `AuthService.loginStudentOrStaff(...)`
- Exception: `AuthenticationException`, `ValidationException`
- Security: `PasswordHasher`, `SessionManager`
- UI: `LibraryManagementUI.buildStudentStaffTab()`, `ConsoleUI.login(Scanner sc)`

Function Details:

- `AuthService.loginStudentOrStaff(...)`
  First, find the user by username
  Then check if the selected role matches the account's role
  Verify if the password hash matches
  Create a session upon successful login

UI Implementation:

- After a successful login, JavaFX updates the status label and refreshes the current borrow list
- After a successful login, the Console prints a welcome message and redirects to the book list

### 5.1.3 Task 1.3 Available Book Screen

Requirements Overview:

- Display approved books
- Display title, author, publish date, availability status, and summary

Involved files mapped to the six layers are as follows:

- Model: `Book`
- Repository: `BookRepository`, `MemoryBookRepository`
- Service: `BookService.listApprovedBooksWithAvailability()`, `BookService.searchApprovedBooks(...)`, `RecommendationService.recommendTopPopular(...)`
- Exception: `ValidationException`, `BusinessException`
- Security: Shared rules in `SecurityConfig`
- UI: `LibraryManagementUI.buildStudentStaffTab()`, `ConsoleUI.listBooks()`, `EnhancementHelper`

Function Details:

- `BookService.listApprovedBooksWithAvailability()`
  Returns all approved books, sorted by title
- `BookService.searchApprovedBooks(...)`
  Search by title or author, and only retain approved books
- `EnhancementHelper.getAvailabilityColor(Book b)`
  Implement red/black color display in JavaFX
- `EnhancementHelper.printAvailability(Book b)`
  Output the availability status in the console
- `EnhancementHelper.quickReadSummary(Book b)`
  Implement the quick read summary feature

UI Implementation:

- JavaFX uses `TableView` to display the book list and shows the selected book's summary on the right
- Console version outputs book information and recommendation results line by line

### 5.1.4 Task 1.4 Borrow Book

Requirements Overview:

- Only available books are allowed to be borrowed
- The book's status must be updated after being borrowed
- Users should receive a borrow confirmation

Involved files mapped to the six layers are as follows:

- Model: `Book`, `BorrowRecord`
- Repository: `BookRepository`, `BorrowRepository`
- Service: `BorrowService.borrowBook(...)`, `BorrowService.listActiveBorrowsByUser(...)`
- Exception: `BusinessException`, `NotFoundException`, `ValidationException`
- Security: `SecurityConfig.MAX_BORROW_LIMIT`, `SecurityConfig.DEFAULT_BORROW_DAYS`
- UI: `LibraryManagementUI.handleBorrow()`, `ConsoleUI.borrow(Scanner sc)`, `EnhancementHelper`

Function Details:

- `BorrowService.borrowBook(String username, String bookId, int borrowDays)`
  Find the target book
  Check if the book exists and is approved
  Check if the book is available for borrowing
  Check if the user has exceeded the borrow limit
  Check if the number of borrow days is valid
  Create `BorrowRecord`
  Save the borrow record
  Mark the book as unavailable
- `EnhancementHelper.buildBorrowConfirmation(...)`
  Build the content for the JavaFX confirmation pop-up
- `EnhancementHelper.confirmBorrow(...)`
  Output the borrow confirmation message in the console
- `EnhancementHelper.printBorrowResult(...)`
  Output the successful borrow result

UI Implementation:

- JavaFX will pop up a confirmation box before actually borrowing the book
- Console version confirms whether to continue borrowing via `Y/N`

## 5.2 Task 2 Author Portal

Task 2 contains three main subtasks:

1. Author Registration
2. Author Login
3. Publish New Book

### 5.2.1 Task 2.1 Author Registration

Requirements Overview:

- Users input username, full name, password, and optional bio
- Username must be unique
- Password must meet validation rules
- The system provides registration feedback

Involved files mapped to the six layers are as follows:

- Model: `User`, `Role`, `AuthorProfile2`
- Repository: `UserRepository`, `AuthorProfileRepository2`
- Service: `AuthorService2.registerAuthor(...)`
- Exception: `ValidationException`
- Security: `PasswordHasher`, `PasswordPolicy`, `SessionManager`
- UI: `LibraryManagementUI.buildAuthorTab()`, `AuthorConsoleUI2.register(Scanner sc)`

Function Details:

- `AuthorService2.registerAuthor(...)`
  Validate username and full name
  Validate password strength
  Check if the username is duplicated
  Hash the password
  Create an AUTHOR user
  Create and save `AuthorProfile2`

### 5.2.2 Task 2.2 Author Login

Requirements Overview:

- Author inputs username and password
- The system verifies the account information
- The system confirms that the account role is AUTHOR

Involved files mapped to the six layers are as follows:

- Model: `User`, `Role`
- Repository: `UserRepository`
- Service: `AuthorService2.loginAuthor(...)`
- Exception: `AuthenticationException`
- Security: `PasswordHasher`, `SessionManager`
- UI: `LibraryManagementUI.buildAuthorTab()`, `AuthorConsoleUI2.login(Scanner sc)`

Function Details:

- `AuthorService2.loginAuthor(...)`
  Find the user by username
  Confirm the role must be AUTHOR
  Verify the password hash
  Create a login session

### 5.2.3 Task 2.3 Publish New Book

Requirements Overview:

- Author submits book title, author name, genre, description, and file
- The submission will be sent to the librarian for review
- Feedback needs to be provided to the author upon successful submission

Involved files mapped to the six layers are as follows:

- Model: `BookSubmission2`, `BookDraft2`, `User`
- Repository: `BookSubmissionRepository2`, `BookDraftRepository2`, `UserRepository`
- Service: `AuthorService2.publishBook(...)`, `AuthorService2.previewBook(...)`, `AuthorService2.getSupportedGenres()`, `AuthorDraftService.autoSave(...)`, `AuthorDraftService.loadDraft(...)`, `[...]`
- Exception: `ValidationException`, `AuthenticationException`
- Security: `SecurityConfig.ALLOWED_EXTENSIONS`, `SecurityConfig.MAX_FILE_SIZE_BYTES`
- UI: `LibraryManagementUI.buildAuthorTab()`, `AuthorConsoleUI2.publish(Scanner sc)`, `AuthorConsoleUI2.loadDraft(Scanner sc)`

Function Details:

- `AuthorService2.publishBook(...)`
  Validate title, genre, description, and filename
  Validate if the genre is in the supported list
  Validate if the file format is legal
  Verify that the submitter must be an AUTHOR
  Create and save a `BookSubmission2` with the status `PENDING`
- `AuthorService2.previewBook(...)`
  Generate a preview text before formal submission
- `AuthorService2.getSupportedGenres()`
  Provide a predefined list of genres
- `AuthorDraftService.autoSave(...)`
  Auto-save incomplete form content
- `AuthorDraftService.loadDraft(...)` and `loadDrafts(...)`
  Support restoring incomplete submissions
- `AuthorDraftService.clearDraft(...)`
  Clear the draft after successful submission
- `FileService.validateSubmissionFile(...)`
  Validate if the file exists, and if its format and size are legal

UI Implementation:

- JavaFX integrates saving drafts, previewing, and final submission into the same interface
- Console version also supports drafts, previews, and final submissions

## 5.3 Task 3 Librarian Portal

Task 3 contains three main subtasks:

1. Librarian Registration
2. Librarian Login
3. Librarian New Books Approval Screen and Functionalities

### 5.3.1 Task 3.1 Librarian Registration

Requirements Overview:

- Users input username, full name, password, and optional employee id
- Username must be unique
- Password must meet validation rules
- The system provides feedback

Involved files mapped to the six layers are as follows:

- Model: `User`, `Role`, `LibrarianProfile3`
- Repository: `UserRepository`, `LibrarianProfileRepository3`
- Service: `LibrarianService3.registerLibrarian(...)`
- Exception: `ValidationException`
- Security: `PasswordHasher`, `PasswordPolicy`
- UI: `LibraryManagementUI.buildLibrarianTab()`, `LibrarianConsoleUI3.register(Scanner sc)`

Function Details:

- `LibrarianService3.registerLibrarian(...)`
  Validate fields
  Validate password strength
  Check if the username is duplicated
  Hash the password
  Create a LIBRARIAN user
  Create and save `LibrarianProfile3`

### 5.3.2 Task 3.2 Librarian Login

Requirements Overview:

- Users input username and password
- The system verifies the credentials
- The system checks that the account role must be LIBRARIAN

Involved files mapped to the six layers are as follows:

- Model: `User`, `Role`
- Repository: `UserRepository`
- Service: `LibrarianService3.loginLibrarian(...)`
- Exception: `AuthenticationException`
- Security: `PasswordHasher`, `SessionManager`
- UI: `LibraryManagementUI.buildLibrarianTab()`, `LibrarianConsoleUI3.login(Scanner sc)`

Function Details:

- `LibrarianService3.loginLibrarian(...)`
  Find the user by username
  Check that the role must be LIBRARIAN
  Verify the password hash
  Create a session

### 5.3.3 Task 3.3 Librarian New Books Approval Screen and Functionalities

Requirements Overview:

- Display a list of pending submissions
- Display title, author username, author full name, genre, submission date, and status
- Support approving or rejecting, and confirm before final execution
- Update status and provide feedback to the librarian

Involved files mapped to the six layers are as follows:

- Model: `BookSubmission2`, `SubmissionState`, `Book`
- Repository: `BookSubmissionRepository2`, `BookRepository`
- Service: `LibrarianService3.getPendingSubmissions()`, `LibrarianService3.approveSubmission(...)`, `LibrarianService3.rejectSubmission(...)`, `LibrarianService3.bulkApprove(...)`, `LibrarianServ[...]`
- Exception: `ValidationException`, `NotFoundException`
- Security: File restrictions and format restrictions in `SecurityConfig`
- UI: `LibraryManagementUI.buildLibrarianTab()`, `LibrarianConsoleUI3.listPending()`, `approve(Scanner sc)`, `reject(Scanner sc)`, `previewSubmissionFile(Scanner sc)`, `bulkApprove(Scanner sc)[...]`

Function Details:

- `LibrarianService3.getPendingSubmissions()`
  Returns all submissions with the `PENDING` status
- `LibrarianService3.approveSubmission(...)`
  Find the target submission
  Check if it is still in the pending status
  Mark it as approved
  Save the updated submission
  Create a new `Book` based on the submission content
  Mark the book as approved and available for borrowing
  Save it to the library catalog
- `LibrarianService3.rejectSubmission(...)`
  Find the submission
  Check if it is still in the pending status
  Mark it as rejected and save comments
- `LibrarianService3.bulkApprove(...)`
  Perform bulk approval on multiple submissions
- `LibrarianService3.bulkReject(...)`
  Perform bulk rejection on multiple submissions
- `FileService.getPreviewDetails(...)`
  Support librarians in viewing text content and file metadata

UI Implementation:

- JavaFX uses tables, selection models, preview areas, comment input boxes, and action buttons
- Console version completes the same process through lists and confirmation prompts

## 6. Phase 1 Enhancements

## 6.1 Task 1.1, 2.1, 3.1 Registration Checks

Implemented Content:

- All user types share a unique username check
- Full Name non-empty check
- Strong password validation, including uppercase, lowercase, numbers, special characters, no spaces allowed, and length limits
- Password hash storage instead of plain text storage

Main Functions:

- `AuthService.registerStudentOrStaff(...)`
- `AuthorService2.registerAuthor(...)`
- `LibrarianService3.registerLibrarian(...)`
- `PasswordPolicy.validate(...)`
- `PasswordHasher.hashPassword(...)`

## 6.2 Task 1.2, 2.2, 3.2 Login Type Checks

Implemented Content:

- Different portals will check the account role to prevent users from logging in through the wrong entrance

Main Functions:

- `AuthService.loginStudentOrStaff(...)`
- `AuthorService2.loginAuthor(...)`
- `LibrarianService3.loginLibrarian(...)`

## 6.3 Task 1.3 Enhancements

### Read Summary

Implemented Content:

- In JavaFX, users can directly view the summary after selecting a book
- The console supports quick reading of summaries

Main Functions:

- `EnhancementHelper.quickReadSummary(Book b)`
- `LibraryManagementUI.buildStudentStaffTab()`

### Borrow Quantity Limit

Implemented Content:

- The system limits the number of books a single user can borrow simultaneously

Main Functions:

- `BorrowService.borrowBook(...)`
- `SecurityConfig.MAX_BORROW_LIMIT`

### Book Recommendations

Implemented Content:

- Recommend top popular books based on borrow count statistics

Main Functions:

- `RecommendationService.recommendTopPopular(int limit)`

## 6.4 Task 1.4 Enhancements

### Borrow Confirmation with Detailed Information

Implemented Content:

- The JavaFX pop-up displays the book title, borrow duration, due date, and reminder information
- The console version also displays confirmation information

Main Functions:

- `LibraryManagementUI.handleBorrow()`
- `EnhancementHelper.buildBorrowConfirmation(...)`
- `EnhancementHelper.confirmBorrow(...)`

### Red and Black Colors to Distinguish Book Availability

Implemented Content:

- Available books are displayed in black
- Unavailable books are displayed in red

Main Functions:

- `EnhancementHelper.getAvailabilityColor(Book b)`
- `LibraryManagementUI.buildStudentStaffTab()`

## 6.5 Task 2.3 Enhancements

### Submission Preview

Main Functions:

- `AuthorService2.previewBook(...)`
- `LibraryManagementUI.buildAuthorTab()`
- `AuthorConsoleUI2.publish(Scanner sc)`

### Multiple Genre Selection

Main Functions:

- `AuthorService2.getSupportedGenres()`
- `LibraryManagementUI.parseGenres(String rawGenres)`

### Auto-Save Drafts

Main Functions:

- `AuthorDraftService.autoSave(...)`
- `AuthorDraftService.loadDraft(...)`
- `AuthorDraftService.loadDrafts(...)`
- `AuthorDraftService.clearDraft(...)`

## 6.6 Task 3.3 Enhancements

### Book Content Preview

Main Functions:

- `FileService.getPreviewDetails(...)`
- `LibraryManagementUI.buildLibrarianTab()`
- `LibrarianConsoleUI3.showSubmissionFilePreview(String submissionId)`

### Bulk Operations

Main Functions:

- `LibrarianService3.bulkApprove(...)`
- `LibrarianService3.bulkReject(...)`
- `LibraryManagementUI.buildLibrarianTab()`
- `LibrarianConsoleUI3.bulkApprove(Scanner sc)`
- `LibrarianConsoleUI3.bulkReject(Scanner sc)`

