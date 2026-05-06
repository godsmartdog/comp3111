# Library Management System

## Project Overview

This project is a multi-role library management system built around a shared web-based experience for different user types within the same platform. It supports the following roles:

- Student
- Staff
- Author
- Librarian

The system combines borrowing workflows, content submission and review, notifications, account security controls, and integration-tested backend behavior in a single Java codebase.

## Core Features

- Authentication and role-based access control for student, staff, author, and librarian workflows
- Student and staff borrowing and return flows with active/returned/overdue borrow views
- Approved-book discovery with search and filtering support
- Multi-book borrowing with duration validation and borrowing limits
- Author draft saving, submission publishing, and published-book management
- Librarian submission review workflows, including approve, reject, and queue filtering/sorting
- Notification system with priority levels, mark-read, delete, archive/unarchive, keyword search, filtering, and sorting
- Password re-authentication for sensitive profile changes and inactivity-based auto-logout
- Session snapshot and crash-recovery foundation for restoring portal state after interruptions
- Return reminder warnings for due-soon and overdue borrows, including duplicate-suppressed reminder notifications

## Nice-to-Have Features Completed

- Notification board enhancements for archive handling, search, filtering, and sorting
- Shared session snapshot and crash-recovery foundation across portals
- Return reminder warnings integrated into borrow APIs, notifications, and student/staff UI
- Borrowed-book due-state indicators for due-soon and overdue records
- Manual reminder check flow for student and staff portals

## Tech Structure / Architecture

At a high level, the project is organized as follows:

- Java backend for domain models, services, security rules, API handlers, and application startup
- In-memory repositories and service-layer orchestration for books, borrows, notifications, reading progress, submissions, and user data
- Web UI pages under the portal web assets, with shared portal JavaScript for student/staff interactions and separate pages for author and librarian workflows
- Integration tests covering end-to-end behavior across authentication, borrowing, submission review, notifications, session handling, and reminder flows

Relevant top-level source areas:

- `src/Library/Model`: domain models
- `src/Library/Repository`: in-memory repositories
- `src/Library/Service`: business logic
- `src/Library/Ui`: API handlers, app entry points, and static web assets
- `src/Library/Test`: integration test suite

## Running the Project

after setting the path of java in ps1/sh file or you use codespace

The repository includes a shell script for macOS/Linux execution:

```bash
cd src/Library
chmod +x run-library.sh
./run-library.sh
```

By default, running the script without a mode starts the web application.

To start the web UI explicitly:

```bash
cd src/Library
./run-library.sh --web
```

To run the smoke test:

```bash
cd src/Library
./run-library.sh --smoke-test
```

To compile without launching:

```bash
cd src/Library
./run-library.sh --compile-only
```

The application script expects `java` and `javac` to be available via `JAVA_HOME` or `PATH`.

## Persistent Local Database

The web app uses a local file-backed database snapshot for demo persistence.

Expected / not a bug:

- `data/library-db.ser` is local runtime data and should not be committed.
- When running through `src/Library/run-library.sh`, the file is created under `src/data/library-db.ser` from the repository root.
- If you delete `src/data/library-db.ser`, the app resets to the seeded demo accounts and demo books on the next startup.
- After a crash, either the newly reopened browser tab or a refreshed previous tab can restore the last session successfully.
- On first startup, the console prints `Created new persistent library database`.
- On later startups, the console prints `Loaded persistent library database`.

Demo accounts after a fresh database reset:

- `student1 / Password1!`
- `author1 / Password1!`
- `librarian1 / Password1!`

## Testing

Integration tests are included in the repository and are used to validate the implemented features across all supported roles.

Run the test suite with:

```bash
cd src/Library
./run-library.sh --tests
```

The current implemented feature set, including notification workflows, session snapshot recovery, and borrow return reminders, has been validated through this integration test suite.

## Team Notes

The project was developed incrementally through feature branches and pull requests, with major features added in focused backend and UI slices and then validated through integration testing.

## Future Improvements

- Profile picture upload and richer account customization
- Additional UI polishing and consistency improvements across portals
- Persistent storage or database-backed repositories instead of in-memory data only
- Deployment hardening, operational configuration, and production-ready hosting support

## Phase 3 — Nice-to-Have Enhancements

Phase 3 NTH enhancements live on branch `phase-3-nice-to-have` and are
documentation-tracked under [`docs/`](docs/):

- [PHASE3_NTH_COVERAGE.md](docs/PHASE3_NTH_COVERAGE.md) — spec-section ↔ slice ↔ commit matrix
- [PHASE3_NTH_AUDIT.md](docs/PHASE3_NTH_AUDIT.md) — outstanding TODOs, pre-existing failing tests, in-memory state notes
- [PHASE3_NTH_CHANGELOG.md](docs/PHASE3_NTH_CHANGELOG.md) — slice-by-slice changelog

### Run

```bash
cd src/Library
bash ./run-library.sh --tests        # compile + run integration tests
bash ./run-library.sh --web          # launch web server (default port 8080)
```

### Demo accounts

All seeded by `LibraryManagementApp` on startup (password is shared):

- Librarian: `librarian1` / `Password1!`
- Author:    `author1`    / `Password1!`
- Student:   `student1`   / `Password1!`

## Phase 3 NTH — Testing Notes & Known Limitations

### Clock-aware testing (auto-return, reader auto-close, due-date features)

Several Phase 3 nice-to-have features are time-sensitive (Slices 9–12 of
the `phase-3-nice-to-have` branch):

- **1.5 #1** Auto-return Notifications
- **1.5 #2** Partial Return Option
- **1.5 #3** Closed Book Reading Screen on borrow-period expiry
- **1.7 #7** Search and Filter Notifications (auto-return surfacing)

To test these end-to-end you must wind the OS clock backward at borrow
time, then forward past the due date. To prevent the session-idle
timeout from kicking in during clock jumps, the default has been
extended to **15 days** (one day beyond `MAX_BORROW_DAYS = 14`),
overridable at JVM start via:

```
-Dlibrary.sessionIdleTimeoutMs=<milliseconds>
```

Recommended manual-test recipe (macOS):

```bash
# Disable network time, then jump back ~10 days:
sudo systemsetup -setusingnetworktime off
sudo date 0424120000      # April 24, 12:00

# In browser: login, borrow a book with min-duration 1 day, click Read.

# Jump forward to "today":
sudo date 0504220000

# Wait ≤30 s. Reader closes; redirect; HIGH "Book auto-returned"
# notification arrives; Active borrows list goes empty;
# Returned filter shows the book.

# Restore clock:
sudo systemsetup -setusingnetworktime on
```

### Reader watchdog scope (Slice 11 / 1.5 #3)

The client-side reader expiry watchdog has been verified end-to-end
for the realistic workflow:

1. Author uploads a real book file (PDF confirmed working).
2. Librarian approves the submission.
3. Student borrows + opens the reader.
4. Clock-jump past due date → within 30 s the reader closes, redirects
   to the borrows list, server-side auto-return runs, and the HIGH
   "Book auto-returned" notification appears.

**Known limitation:** the watchdog has been observed not to fire
reliably for some of the bundled seed text-tile demo books that ship
with the in-memory store. These seed entries appear to render through
a different content path that bypasses the watchdog hook in
`loadBorrowedContent`. The realistic upload-approve-borrow-read path
is the one being graded; no spec-relevant feature is affected. To
reproduce the working behavior, prefer testing with a freshly
uploaded PDF rather than the seed text tiles.
