# 1. Project Information

- Course/Project: COMP3111 - S2026 Project - Group 21
- System: Library Management System (E-Book Library System)
- Roles:
  - Student
  - Staff
  - Author
  - Librarian

# 2. Introduction

This report reflects the current implementation of the Library Management System in this repository. It is based on the implemented Java backend, the current HTML and JavaScript portal frontends, and the integration test suite, rather than the earlier workflow draft alone.

Since the original workflow document was prepared, the project has expanded materially. The current system now includes richer borrowing workflows, role-specific profile management, notification handling, reminder generation, user-management features for librarians, and a session snapshot and crash-recovery foundation. This report therefore focuses on what is currently implemented, what is partially implemented, and what is supported in backend logic but not fully exposed in the UI.

# 3. System Overview

The current system is implemented as a Java web application with role-based HTTP APIs and static web portals.

- Java backend: Business logic is implemented in the service layer, with request handling centered in `LibraryApiHandlers.java`.
- Role-based API handlers: Authentication and authorization are enforced per request using session IDs and role checks.
- HTML/JS portal frontends: Separate pages and scripts exist for student/staff, author, and librarian experiences, with shared utilities in `shared.js`.
- In-memory repositories and services: Current persistence is memory-based, with repositories and services covering users, books, borrows, submissions, notifications, reading progress, and session snapshots.
- Integration-test-driven validation: The repository includes a large integration suite that validates many expected workflows across all supported roles.

At a high level, the system supports four user roles with a shared authentication layer and role-specific workflows:

- Student and staff focus on discovering, borrowing, reading, and returning approved books.
- Authors focus on drafting, previewing, submitting, and managing their own content.
- Librarians focus on submission review, approved-book oversight, borrowed-record visibility, and user management.

# 4. Shared Cross-Portal Workflows

## Authentication and session handling

Authentication is currently implemented through HTTP endpoints for registration, login, and logout, with successful login returning a generated session ID. The frontend stores the authenticated user and session ID locally and includes that session ID in subsequent API requests.

This session-based model is the active web-facing authentication mechanism. It is backed by role-aware request checks in the API handlers and is currently used across all supported portals.

## Role-based access control

Role-based access control is currently enforced server-side on each protected endpoint. Student and staff endpoints are limited to student/staff roles, author workflows are limited to authors, and librarian workflows are limited to librarians.

There is also a broader authenticated path for shared capabilities such as session snapshots and the shared notification foundation. Where behavior differs between the dedicated role endpoints and the shared notification endpoints, that difference is noted later in this report.

## Password policy

The system currently enforces password validation during registration and password-change flows. Password rules are validated server-side and mirrored in frontend validation logic where applicable.

This means the system currently treats password complexity as a required security constraint rather than just a UI hint.

## Inactivity auto-logout

Inactivity-based session expiry is currently implemented server-side. Each authenticated request updates the session's last-activity timestamp, and sessions that exceed the configured idle timeout are rejected as expired.

When a session expires, the frontend redirects the user back to the login screen with a session-expired message. This behavior is currently shared across the web portals.

## Password re-authentication

Password re-authentication is currently implemented for sensitive password changes in some portals, but not consistently across all roles.

- Student/staff password changes currently require the current password.
- Librarian password changes currently require the current password.
- Author profile password changes are currently not aligned with that same re-authentication model.

This inconsistency is implemented behavior, not just a documentation gap.

## Notifications foundation

The system currently includes a shared notification foundation with support for:

- notification creation
- unread tracking
- mark as read
- deletion
- priority metadata
- archive/unarchive support
- keyword, read-state, priority, and scope filtering
- sorting by creation time or priority

Some of these capabilities are fully implemented in backend logic but only partially surfaced in current portal UIs. In particular, archive and advanced filtering are stronger in backend support than in the present frontend controls.

## Session snapshot and crash recovery

The system currently implements session snapshot save, get, and clear operations, plus guarded development-only crash simulation hooks. This provides a foundation for saving portal state and offering a restore banner after interruption.

Restore behavior is currently implemented, but restore depth varies by portal:

- some portals restore meaningful filter or notification-page state
- some portals only restore shallow state or banner-level awareness

So the snapshot workflow exists across the project, but deep state restoration is only partial in some UI flows.

## Logout and session cleanup behavior

Logout currently clears the active session and also clears any saved session snapshot tied to that session. Session snapshot cleanup also occurs on inactivity expiry. This helps keep stale portal state from being restored after the session has ended.

# 5. Student/Staff Portal Workflows

The student and staff portals currently share the same core implementation model. In practice, both roles use the same borrowing APIs and closely related frontend logic, with the main difference being role labeling and portal entry pages.

## Registration and login

Student and staff users can currently register through the shared registration flow and log in through the role-aware login page. The system validates role, username, and password requirements before creating the account.

After login, the user is redirected into the appropriate student or staff main page.

## Browse available books

Student and staff users can currently view approved books that are available through the library catalog. Books shown through this workflow are filtered to approved titles only.

The UI currently presents book title, author, and availability status, with selection controls for borrowing.

## Search/filter approved books

The catalog currently supports searching approved books by keyword and filtering by availability state. This is an implemented backend and UI workflow.

At present, this is a practical discovery workflow rather than a very advanced catalog search system.

## Borrow one or more books

Student and staff users can currently:

- borrow a single approved and available book
- borrow multiple approved and available books in one bulk action

The bulk-borrow workflow validates the full selection before committing the borrow operation, so the action is currently treated as all-or-nothing.

## Borrow duration validation

Borrow duration is currently validated server-side. The implementation enforces a minimum and maximum duration, with the present limit set to 1-14 days.

The system also enforces a maximum active-borrow limit per user.

## View borrowed books

Student and staff users can currently view their borrow records through active, returned, overdue, and all-record views depending on the filters used. The system exposes borrow dates, due dates, returned status, and due-state indicators.

This workflow is currently stronger than a minimal borrow-history view because it includes filtering and sorting behavior.

## Read borrowed books

Borrowed books can currently be opened for reading only when the user has an active borrow for that book.

The implementation currently supports:

- PDF viewing
- DOCX preview conversion for reading
- text fallback when no richer readable file is attached

This is an access-controlled workflow rather than an open catalog-reading workflow.

## Bookmark/highlight and reading progress

Student and staff users can currently save reading progress for actively borrowed books. The current implementation supports:

- bookmark page tracking
- highlight text list persistence

This progress is stored and can be reloaded when the user returns to the reading view.

## Return books

Returning a currently borrowed book is implemented. When a return succeeds, the borrow record is updated, the book becomes available again, and a notification is generated for the user.

## Borrowed-book filtering/sorting

Borrow records currently support filtering and sorting through implemented request parameters and frontend controls. The current workflow is strongest for:

- status filtering
- borrow-date and due-date range filtering
- due-date and borrow-date sorting

This gives the current portal a practical operational borrow-management view.

## Due-soon and overdue reminders

The student/staff implementation now includes a due-soon and overdue reminder workflow.

Currently implemented behavior includes:

- due-soon detection
- overdue detection
- reminder notification generation
- duplicate suppression for same-day reminder generation
- due-state highlighting in the UI
- manual reminder-check action in current student/staff pages

This is one of the clearest areas where the current implementation goes beyond a basic original borrowing workflow.

## Notifications

Student and staff users currently have access to notification lists showing unread and read state, message details, and priority display. The currently exposed UI supports:

- viewing notifications
- unread count visibility
- mark as read
- delete

Advanced notification capabilities also exist in backend logic, but not all of them are surfaced as equally strong UI workflows yet.

## Profile update

Student and staff users can currently update their own profile details, including full name and optionally password. Successful updates generate a profile-updated notification.

## Password re-auth

Student/staff password change currently requires the current password. This is enforced in backend logic and also reflected in the current profile UI.

## Inactivity auto-logout

Student and staff sessions are currently subject to inactivity timeout. Once expired, requests are rejected and the frontend redirects the user back to login.

## Student/Staff limitations / partial items

- Borrowed-book filtering is currently more status, date, and sort oriented than a richer keyword-based borrowed-book search workflow.
- Some advanced notification features, especially archive and richer filtering/sorting, are currently implemented more strongly in backend support than in the exposed student/staff UI.

# 6. Author Portal Workflows

The author portal is currently implemented as a content-submission and author-owned content-management workflow rather than a simple one-time upload flow.

## Registration and login

Authors can currently register and log in through author-specific frontend flows backed by the shared registration/login infrastructure.

## Draft save/reload

Authors can currently save draft submission state and reload saved drafts later. Drafts can include title, genres, description, and file-path information.

This is currently implemented as a draft persistence workflow rather than just a temporary browser-side form state.

## Submit new book

Authors can currently submit a new book for librarian review. The submission flow supports:

- metadata submission
- genre validation
- file validation
- multipart upload or validated file-path reference depending on the submission mode

After submission, the draft is cleared and the submission enters pending review.

## Preview before submission

The current author portal supports previewing submission content before final submission.

Currently implemented preview behavior includes:

- text preview of submission metadata
- local file preview for supported file types before upload
- server-side preview for saved submission or published-book files

## Manage submitted books

Authors can currently view the list of their own submissions along with current submission status, submission date, and file reference information.

## Edit eligible submissions

Authors can currently edit their own pending submissions. This is currently limited to submissions that remain in pending state.

Approved or rejected submissions are no longer editable under the current implementation.

## Delete eligible submissions

Authors can currently delete their own pending submissions. This workflow is ownership-protected and state-restricted in the backend.

## Manage published books

Authors can currently view and manage their own published books after approval. The current implementation supports:

- listing author-owned published books
- updating published metadata
- deleting published books when allowed

Deletion is currently blocked if the published book has active borrows.

## Read/preview uploaded files

Authors can currently preview both submission files and published-book files. Supported rendering depends on file type, with text, PDF, DOCX, and image-oriented handling currently implemented.

## Notifications

Authors currently have notification support through both author-specific notification endpoints and current shared dashboard notification usage. In practice, authors can currently:

- view notifications
- see unread counts
- mark notifications as read

Notification exposure differs somewhat depending on which author page is used.

## Profile update

Authors can currently update their own full name, bio, and optional password through the author profile workflow.

## Author limitations / partial items

- Current-password re-authentication for author password changes is not aligned with the stricter student/staff and librarian behavior.
- Profile picture upload was not found in the current implementation.

# 7. Librarian Portal Workflows

The librarian portal is currently implemented as an administrative review and oversight portal, with workflows spanning content approval, library oversight, and user management.

## Registration and login

Librarians can currently register and log in through the shared role-aware authentication system.

## Review submissions

Librarians can currently view the submission review queue and act on pending submissions. This is an implemented end-to-end workflow from author submission through librarian decision.

## Approve/reject with reason

Librarians can currently approve or reject pending submissions. Rejection can include a reason, and that reason is preserved and used in author notification messaging.

Approval currently converts the submission into a published and borrowable approved book.

## Submission queue search/filter/sort

The current librarian review queue supports:

- keyword search
- status filtering
- submitted-date sorting

This is currently a solid implemented workflow rather than a placeholder queue.

## View approved books

Librarians can currently view the approved-book list, including publish date and availability status. This gives librarians visibility into the currently approved catalog.

## View borrowed record list

Librarians can currently view a borrowed-record list spanning the system. The current view includes borrower, book, borrow date, due date, return date, and overdue highlighting.

## Manage users

Librarians currently have a user-management workflow that supports:

- user search
- role and status filtering
- full-name editing
- active/inactive status management
- recent activity visibility
- borrow count visibility

This is one of the significant workflow expansions beyond a minimal librarian review role.

## Activate/deactivate and bulk actions

Librarians can currently activate or deactivate individual users and perform bulk activate/deactivate actions on selected users.

The implementation also guards against self-deactivation by the currently acting librarian.

## Notifications

Librarians currently have notification access both through dedicated librarian notification endpoints and through the current shared dashboard notification usage. The present UI supports at least list viewing and mark-as-read behavior.

## Profile update

Librarians can currently update their own full name, employee ID, and optionally password.

## Password re-auth

Librarian password change currently requires the current password. This is enforced in the implemented backend and reflected in the current profile UI.

## Inactivity auto-logout

Librarian sessions are currently subject to the same inactivity timeout model as other authenticated web users.

## Librarian limitations / partial items

- The borrowed-record workflow is currently implemented and useful, but richer search/filter controls suggested by earlier expectations appear partial or not yet surfaced.

# 8. Notification and Reminder Workflows

The project currently has a broader notification system than a simple alert list.

## Notification list

Notification listing is currently implemented through shared endpoints and some role-specific endpoints. Users can retrieve their own notifications, with current portal pages showing message details and read state.

## Unread counters

Unread counts are currently displayed in several UI flows, especially on current dashboard and portal notification views.

## Mark as read

Mark-as-read is currently implemented and exposed in UI workflows across student/staff, author, and librarian pages.

## Delete

Notification deletion is currently implemented in backend logic and exposed in current shared notification UIs.

## Priority

Notification priority is currently implemented in the data model and backend logic. Student/staff-facing notification pages currently expose priority most clearly in the UI.

## Archive/unarchive

Archive and unarchive are currently implemented in backend notification logic and supported by API endpoints. However, current frontend exposure is weaker than the backend support. This means archive behavior is currently backend-supported but not consistently surfaced as a strong UI workflow.

## Search/filter/sort support

The notification system currently supports:

- keyword filtering
- read/unread filtering
- priority filtering
- scope selection for active, archived, or all
- sorting by creation time or priority

These capabilities are implemented in backend logic and covered in integration tests, but current UI exposure is partial.

## Due-soon and overdue borrow reminders

The notification system also currently supports borrow reminder generation for student/staff users. These reminders:

- distinguish due-soon from overdue conditions
- use priority levels
- avoid duplicate reminder creation for the same borrow on the same day
- appear as part of the current notification workflow

## Role differences in UI exposure vs backend support

Notification capability is currently broader in backend support than in UI exposure:

- student/staff pages show the richest current notification presentation
- author and librarian pages support notifications, but some current pages expose simpler notification interactions
- archive/filter/sort behavior is implemented more strongly in backend than in current role UIs

# 9. Crash Recovery / Session Snapshot Workflows

The repository currently includes a session snapshot and crash-recovery foundation.

## Save/get/clear snapshot

The implementation supports saving, fetching, and clearing per-session portal snapshot state. This is currently used to preserve some UI state across interruptions.

## Guarded dev crash-test hook

Development-only crash-test endpoints are currently implemented and guarded by a required header. These endpoints support testing snapshot capture, simulated session loss, and recovery behavior.

## Restore banner behavior

When snapshot data exists for the current session and portal, the frontend can currently show a restore banner. The user may then restore saved state or dismiss the stored snapshot.

## Logout and session-expiry cleanup

Logout currently clears the saved snapshot for the active session. Session-expiry logic also clears snapshot state when the session becomes invalid.

## Current partial restore depth depending on portal

Restore depth is currently uneven across portals:

- student/staff notification views restore pagination state meaningfully
- librarian review state restores search and filter settings more meaningfully
- author restore support currently appears shallower and less state-rich

So the snapshot workflow is currently implemented project-wide, but deep portal restoration remains partial depending on the specific UI.

# 10. Changes from the Original Workflow Specification

## Fully Implemented from Original Spec

- Role-based registration and login across student, staff, author, and librarian workflows
- Student/staff approved-book browsing, borrowing, and returning
- Author submission workflow from creation to librarian review
- Librarian approve/reject review workflow
- Role-specific profile management

## Expanded Beyond Original Spec

- Bulk borrowing with validation and all-or-nothing behavior
- Reading-progress persistence with bookmarks and highlights
- Due-soon and overdue reminder notifications with duplicate suppression
- Richer notification model with priority, archive support, filtering, and sorting
- Librarian user-management workflows with activation, deactivation, bulk actions, and activity summaries
- Session snapshot and crash-recovery foundation with restore banner behavior
- Author published-book management after approval, including preview and controlled deletion rules

## Missing / Partial / Changed

- Student and staff currently share one implementation path more than distinct role-specific workflows
- Borrowed-book search is currently more filter/sort oriented than rich keyword-oriented search
- Notification archive/filter/sort are backend-supported but not fully surfaced in current UI flows
- Author password re-authentication is not aligned with student/staff and librarian behavior
- Profile picture upload was not found in the current implementation
- Librarian borrowed-record workflow is implemented, but richer filtering/search remains partial
- Shared notification authorization behavior appears changed relative to one older test expectation

# 11. Known Gaps / Open Issues

- Notification authorization mismatch: current code allows broader shared-notification access than one integration test still expects.
- Author password re-auth inconsistency: author password-change flow is not aligned with the current-password requirement used elsewhere.
- Notification archive/filter/sort are not fully surfaced in current UI flows even though backend support exists.
- Profile picture upload was not found in the current implementation.
- Librarian borrowed-record filtering appears partial compared with stronger earlier expectations.

# 12. Validation Status

This report was prepared by inspecting the current source code and by running the integration test suite included in the repository.

Validation basis:

- current Java backend implementation inspected
- current portal HTML/JS implementation inspected
- integration tests executed from the repository test runner

One audit run found:

- 102 passing tests
- 1 failing test

The failing case appears tied to a stale authorization expectation on shared notification APIs, where current implementation behavior and one older integration-test expectation are no longer aligned.

This means the report should be read as implementation-based and current-state focused, not as a restatement of older expected behavior.

# 13. Conclusion

The project now goes significantly beyond the original workflow draft. The current implementation includes broader portal behavior, more complete operational flows, stronger validation, a richer notification model, and infrastructure for session restoration and reminder generation.

At the same time, the implementation is not uniform in every area. Some capabilities are currently backend-supported more strongly than they are UI-exposed, and a few cross-role behaviors remain inconsistent or only partially surfaced.

Overall, this report reflects the actual implementation status of the current repository rather than the originally planned scope. It should therefore be used as the more accurate workflow reference for teammates, reviewers, and assessors working from the current codebase.