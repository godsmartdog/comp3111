# Phase 3 Nice-to-Have Coverage

**Coverage status (post-correction):**
- Implemented: 34 / 48 bullets (Phase 2 carryover: 11; Phase 3 slice work: 23)
- Not yet implemented: 12
- Over-claims (polish, not bullet coverage): 6 (relocated to dedicated section)

All Phase 3 slice commits live on branch `phase-3-nice-to-have`. Phase 2
carryover rows reflect features that landed before this branch existed.

| Spec Section | Bullet | Slice | Commit | Files |
|---|---|---|---|---|
| 1.6 / 2.5 / 3.5 | Profile Picture Upload (Optional): Allow users to upload a profile picture with validation for format and size. | Phase 2 carryover | (existing) | `profile-user.{html,js}`, `profile-author.{html,js}`, `profile-librarian.{html,js}`, `LibraryApiHandlers.java` (`/api/profile`, `/api/author/profile`, `/api/librarian/profile` multipart photo) |
| 1.6 / 2.5 / 3.5 | Password Strength Meter: Show real-time feedback when updating passwords. | Phase 2 carryover | (existing) | `shared.js` (`getPasswordStrengthInfo`), `profile-user.{html,js}`, `profile-author.{html,js}`, `profile-librarian.{html,js}`, `style.css` (`.strength-meter`) |
| 1.7 / 2.6 | Unread Notification Counter: Display the number of unread notifications. | Phase 2 carryover | (existing) | `main-staff.js`, `main-author.js`, `main-librarian.js`, `student-main.js`, `portal-books.js`, `shared.js` (`updateNotificationBadge`) |
| 1.7 / 2.6 | Priority Notifications: Highlight urgent notifications (e.g., auto-return books, book deletion by librarian) at the top. | Phase 2 carryover | (existing) | `main-staff.js`, `main-author.js`, `main-librarian.js`, `student-main.js`, `portal-books.js`, `style.css` (`priority-*` classes, HIGH pill) |
| 1.7 / 2.6 | Mark as Read & Delete Notifications: Allow users to mark the notification as read and allow them to delete the notifications as well. | Phase 2 carryover | (existing) | `main-staff.js`, `main-author.js`, `main-librarian.js`, `student-main.js`, `portal-books.js`, `archived-notifications.js`, `LibraryApiHandlers.java` (`/api/notifications/read`, `/api/notifications/delete`) |
| 1.7 / 2.6 | Archive Notifications: Enable users to archive old notifications for better organization. | Phase 2 carryover | (existing) | `archived-notifications.{html,js}`, `LibraryApiHandlers.java` (`/api/notifications/unarchive`) |
| 3.4 | Role-Based Filters: Allow librarians to filter users by role (student, staff, author, librarian). | Phase 2 carryover | (existing) | `librarian-users.html` (`userRoleFilter` `<select>`), `librarian-users.js` |
| 3.4 | Manage Librarians Account: Allow librarians to manage other librarian accounts. | Phase 2 carryover | (existing) | `librarian-users.html` (`librarian` option in `userRoleFilter`), `librarian-user-edit.{html,js}` (LIBRARIAN role branch) |
| 1.10 | Request Tracking: Allow users to track the status of their request (Pending, Approved, Rejected). | Phase 2 carryover | (existing) | `request-new-book.{html,js}` ("My Requests" panel rendering Status from `/api/book-requests`) |
| 1.10 | Request History: Maintain a log of all requests submitted by the user. | Phase 2 carryover | (existing) | `request-new-book.{html,js}` (persistent "My Requests" listing per requester) |
| 1.7 / 2.6 | Search and Filter Notifications: Enable filtering by category (due reminders, announcements, deletions, etc.). | Phase 2 carryover + Slice 12 polish | (existing) + Slice 12 | `mainStaff.html`, `mainAuthor.html`, `mainLibrarian.html`, `mainStudent.html`, `archived-notifications.html`, `staff.html`, `main-staff.js`, `main-author.js`, `main-librarian.js`, `student-main.js`, `archived-notifications.js`, `portal-books.js`, `LibraryApiHandlers.java` (`BASIC_NOTIFICATION_CATEGORIES`, `notificationCategoryLabel`, `auto-return` first-class) |
| 1.9 | Anonymous review submission and display masking | 1 | `6e77839` | `BookReview.java`, `BookReviewService.java`, `LibraryApiHandlers.java`, `author-reviews.js`, `my-reviews.js`, `student-reader.{html,js}`, `staff-reader.{html,js}` |
| 1.9 | Review sort: recent / highest / lowest | 1 | `6e77839` | `BookReviewService.java`, `LibraryApiHandlers.java`, `student-reader.{html,js}`, `staff-reader.{html,js}` |
| 1.10 | Duplicate book-request detection (same title + pending/approved) | 1 / 1.5 | `6e77839`, `4dd9c65` | `BookRequestService.java`, `request-new-book.{html,js}` |
| 1.10 | Librarian priority flag on book requests, sorted top | 1 / 1.5 | `6e77839`, `4dd9c65` | `BookRequest2.java`, `BookRequestService.java`, `LibraryApiHandlers.java`, `librarian-book-requests.{html,js}` |
| 3.4 | Add-new-user form on Manage All Users (any role) | 1 / 1.5 | `6e77839`, `4dd9c65` | `LibrarianService3.java`, `LibraryApiHandlers.java`, `librarian-users.{html,js}` |
| 3.8 | Genre / author / status filters on Manage Published Books | 1 / 1.5 | `6e77839`, `4dd9c65` | `LibraryApiHandlers.java`, `librarian-manage-published.{html,js}` |
| 3.6 | Advanced filter tabs: All / Active / Overdue / Returned | 2 | `afedd57` | `librarian-records.{html,js}`, `style.css` |
| 3.6 | Overdue rows highlighted red with badge | 2 | `afedd57` | `librarian-records.{html,js}`, `style.css` |
| 3.6 | CSV export `/api/librarian/borrowed-records-export` with filtering | 2 / 2.5 | `afedd57`, `91f5735` | `LibraryApiHandlers.java`, `librarian-records.js`, `style.css` |
| 1.8 | Export reading history as CSV (filtered, server-side) | 3 | `12d3413` | `LibraryApiHandlers.java`, `reading-history.{html,js}` |
| 1.8 | Graphical insights: genre doughnut + duration line chart | 3 | `12d3413` | `reading-history.{html,js}`, `style.css` |
| 1.8 | Achievement badges: Bronze / Silver / Gold / Platinum / Variety / Speed / Marathon | 3 | `12d3413` | `reading-history.{html,js}`, `style.css` |
| 2.8 | Trend Analysis: borrows-over-time chart with weekly/monthly toggle | 4 | `10cd02a` | `LibraryApiHandlers.java`, `author-stats.{html,js}` |
| 2.8 | Customizable Dashboard: per-section visibility persisted in localStorage | 4 | `10cd02a` | `author-stats.{html,js}`, `style.css` |
| 2.8 | Download Report: `/api/author/stats-export` → multi-section CSV | 4 | `10cd02a` | `LibraryApiHandlers.java`, `author-stats.js` |
| 2.9 | Sentiment Analysis: Classify reviews as positive, neutral, or negative using keyword, lexicon, and/or TF-IDF based scoring rather than LLM generation. | 5 | `437936e` | `BookReviewService.java`, `author-reviews.{html,js}`, `style.css` |
| 2.9 | Reply Templates dropdown that appends to reply textarea | 5 | `437936e` | `author-reviews.{html,js}` |
| 2.9 | Feedback Analytics: sentiment doughnut + rating-distribution bar chart | 5 | `437936e` | `author-reviews.{html,js}`, `style.css` |
| 3.8 | Bulk Operations: select-all + delete-selected with partial-success reporting | 6 | `49cb72c` | `LibraryApiHandlers.java`, `librarian-manage-published.{html,js}` |
| 3.8 | Version History: in-memory edit ledger (last 50 per book) + history endpoint | 6 | `49cb72c` | `LibraryApiHandlers.java`, `librarian-manage-published.{html,js}` |
| 2.5 | Password Re-authentication: Ask users to re-enter the password if there are any changes to the profile. | 14.1 (banked) | (pre-Slice 14.1) | `AuthService.java` (`validateCurrentPasswordForPasswordChange`), `LibraryApiHandlers.java` (profile update handlers), `profile-user.{html,js}`, `profile-author.{html,js}`, `profile-librarian.{html,js}`. Implemented (pre-Slice 14.1, banked retroactively): AuthService.validateCurrentPasswordForPasswordChange + Current Password field on all profile pages + JS promptForCurrentPassword() re-prompt. |
| 2.5 | Auto logout from system: If the password is changed, the system must automatically logout the current user. | 14.1 (banked) | (pre-Slice 14.1) | `LibraryApiHandlers.java` (`invalidateSessionsByUsername` at lines 818, 2169, 3491), `profile-user.js`, `profile-author.js`, `profile-librarian.js`. Implemented (pre-Slice 14.1, banked retroactively): invalidateSessionsByUsername fires from all profile update handlers when passwordChanged; profile-*.js clears localStorage + redirects to login.html. |
| 1.8 | Bookmark Integration: Link reading history with bookmarks to show where the user left off. | 14.1 (banked) | (pre-Slice 14.1) | `LibraryApiHandlers.java` (`/api/borrows/history` joins reading-progress), `reading-history.{html,js}` (`progressLabel`). Implemented (pre-Slice 14.1, banked retroactively): /api/borrows/history joins reading-progress; reading-history.js progressLabel renders 'Bookmark page N · M highlight(s) · updated YYYY-MM-DD'. Slice 14.1 also fixed a stray-space typo in that label. |

## Over-claims — implemented but not in spec word-for-word

The following entries previously appeared in the coverage matrix but do
not correspond to a literal Phase 3 NTH bullet. They are preserved here
(SHAs intact) because they add demo value, but they are polish / UX
improvements, not bullet coverage.

| Spec Section | Entry | Slice | Commit | Files | Note |
|---|---|---|---|---|---|
| 3.6 | Keyword search across book title / borrower / borrow ID | 2 | `afedd57` | `LibraryApiHandlers.java`, `librarian-records.{html,js}` | Polish / UX improvement, not a Phase 3 NTH bullet. |
| 3.8 | Admin Tools: 4-metric library overview panel | 6 / 6.5 | `49cb72c`, `393f2ac` | `LibraryApiHandlers.java`, `librarian-manage-published.{html,js}` | Polish / UX improvement, not a Phase 3 NTH bullet. |
| 3.7 | Keyword search across book title / requester / reason | 7 | `9ed8fea` | `librarian-book-requests.{html,js}` | Polish / UX improvement, not a Phase 3 NTH bullet. |
| 3.7 | Multi-column sort with asc / desc / unsorted cycle | 7 | `9ed8fea` | `librarian-book-requests.{html,js}` | Polish / UX improvement, not a Phase 3 NTH bullet. |
| 3.7 | Priority badge derived from request age (HIGH / MED / NORMAL) | 7 | `9ed8fea` | `librarian-book-requests.{html,js}`, `style.css` | Polish / UX improvement, not a Phase 3 NTH bullet. |
| 3.7 | Bulk approve and bulk reject with partial-success reporting | 7 | `9ed8fea` | `librarian-book-requests.{html,js}` | Polish / UX improvement, not a Phase 3 NTH bullet. |

## Section 2.7 — Style Customization

Skipped. No `author-style*` scaffolding exists in `src/Library/Ui/web/` —
the screen referenced by spec section 2.7 was never created in earlier
phases. Implementing it from scratch was out of scope for the
nice-to-have phase, which targets enhancements to existing screens.

## Test baseline

All 11 slice commits maintain the pre-Phase-3 test baseline of
**Passed: 90, Failed: 16**. The 16 pre-existing failures were not
introduced by Phase 3 NTH work. See
[PHASE3_NTH_AUDIT.md](PHASE3_NTH_AUDIT.md) for the full failing-test list.

## Final Sprint Test Policy

During the final demo sprint, remaining nice-to-have slices are verified by compile-only checks plus focused manual browser/API smoke tests unless an integration test is cheap and isolated.

LLM is used only for book summary generation. Sentiment analysis uses deterministic keyword and lexicon scoring; recommendations use borrow counts, popularity, genre similarity, rating signals, or keyword/TF-IDF similarity; similar-book matching uses deterministic title, author, and genre overlap scoring with stopword/generic-token filtering; analytics use aggregate counts, trends, rankings, filters, and deterministic scoring.

Avoid adding or running default integration tests that trigger slow summary-generation LLM, PDF, or network paths. Review sentiment and analytics paths are deterministic and should not load local model files.

## Not Yet Implemented (12 bullets)

Spec wording verbatim. Bullets #10, #11, #25 were verified pre-existing
implementations and banked retroactively in Slice 14.1; see the Implemented
table above for details.

| # | Spec wording | Section |
|---|---|---|
| 1 | Auto-return Notifications: Send the notifications to the users once the book is auto-returned after the borrowing period expires. Send the notification even if the user is not login into the system. | 1.5 |
| 2 | Partial Return Option: If multiple books are borrowed, allow users to return selected ones early. Users can select one book or multiple books to return them back. | 1.5 |
| 3 | Closed Book Reading Screen: If the user is reading a book and borrowing period expires, the system must close the reading screen automatically before auto-return. | 1.5 |
| 8 | Modify/Edit Book Details: Allow authors to modify the book only if the book is under pending approval (not published) OR not borrowed by any students/staff (if published). | 2.4 |
| 9 | Bulk Delete: Allow authors to manage multiple books at once with confirmation dialogs. | 2.4 |
| 28 | Allow sorting reviews by most recent, or most helpful. (Slice 1 covered recent/highest/lowest; "most helpful" still missing.) | 1.9 |
| 33 | Multiple Summary Styles: Provide options for short, medium, or detailed summaries. | 2.7 |
| 40 | Bulk Edit/Delete (Slice 6 covered Delete only; Edit still missing). | 3.8 |
| 44 | Auto-Suggest Alternatives: If requested book is unavailable, suggest similar titles. | 3.9 |
| 45 | Notify the user: If the similar title books from authors are downloaded, inform the user who made the book request. | 3.9 |
| 46 | Download Progress Indicator: Show progress bar when downloading requested books. | 3.9 |
| 47 | Request Analytics: Provide statistics on most requested genres/authors. | 3.9 |
| 48 | Downloaded book stats: Same as author dashboard book stats screen, make a similar book stats screen where librarian can view the stats for downloaded books only. | 3.9 |
