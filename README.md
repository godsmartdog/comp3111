# COMP3111 Library Management System

Console-based Phase 1 implementation for the COMP3111 e-book library system.

## Setup

Compile:

```bash
cd /home/runner/work/comp3111/comp3111
mkdir -p /tmp/comp3111-out
find src -name '*.java' -print0 | xargs -0 javac -d /tmp/comp3111-out
```

Run:

```bash
cd /home/runner/work/comp3111/comp3111
java -cp /tmp/comp3111-out Main
```

For author submission and librarian file preview, provide a real file path such as a `.txt`, `.pdf`, `.doc`, or `.docx` file.

## Implemented Features

- Student/staff registration and login with shared username uniqueness and password policy validation
- Available book listing with publish date, availability status, summary preview, and popular-title recommendations
- Borrow flow with availability checks, borrow-limit enforcement, duration/due-date confirmation, and borrow record creation
- Author registration and login
- Author publish-book flow with multi-genre selection, preview, draft auto-save, and file validation
- Librarian registration and login
- Librarian pending-submission listing, file preview, approve/reject actions, and bulk approve/reject confirmations
