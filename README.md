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
