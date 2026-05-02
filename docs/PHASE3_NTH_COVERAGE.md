# Phase 3 Nice-to-Have Coverage

All slices live on branch `phase-3-nice-to-have`.

| Spec Section | Bullet | Slice | Commit | Files |
|---|---|---|---|---|
| 1.9 | Anonymous review submission and display masking | 1 | `6e77839` | `BookReview.java`, `BookReviewService.java`, `LibraryApiHandlers.java`, `author-reviews.js`, `my-reviews.js`, `student-reader.{html,js}`, `staff-reader.{html,js}` |
| 1.9 | Review sort: recent / highest / lowest | 1 | `6e77839` | `BookReviewService.java`, `LibraryApiHandlers.java`, `student-reader.{html,js}`, `staff-reader.{html,js}` |
| 1.10 | Duplicate book-request detection (same title + pending/approved) | 1 / 1.5 | `6e77839`, `4dd9c65` | `BookRequestService.java`, `request-new-book.{html,js}` |
| 1.10 | Librarian priority flag on book requests, sorted top | 1 / 1.5 | `6e77839`, `4dd9c65` | `BookRequest2.java`, `BookRequestService.java`, `LibraryApiHandlers.java`, `librarian-book-requests.{html,js}` |
| 3.4 | Add-new-user form on Manage All Users (any role) | 1 / 1.5 | `6e77839`, `4dd9c65` | `LibrarianService3.java`, `LibraryApiHandlers.java`, `librarian-users.{html,js}` |
| 3.8 | Genre / author / status filters on Manage Published Books | 1 / 1.5 | `6e77839`, `4dd9c65` | `LibraryApiHandlers.java`, `librarian-manage-published.{html,js}` |
| 3.6 | Keyword search across book title / borrower / borrow ID | 2 | `afedd57` | `LibraryApiHandlers.java`, `librarian-records.{html,js}` |
| 3.6 | Advanced filter tabs: All / Active / Overdue / Returned | 2 | `afedd57` | `librarian-records.{html,js}`, `style.css` |
| 3.6 | Overdue rows highlighted red with badge | 2 | `afedd57` | `librarian-records.{html,js}`, `style.css` |
| 3.6 | CSV export `/api/librarian/borrowed-records-export` with filtering | 2 / 2.5 | `afedd57`, `91f5735` | `LibraryApiHandlers.java`, `librarian-records.js`, `style.css` |
| 1.8 | Export reading history as CSV (filtered, server-side) | 3 | `12d3413` | `LibraryApiHandlers.java`, `reading-history.{html,js}` |
| 1.8 | Graphical insights: genre doughnut + duration line chart | 3 | `12d3413` | `reading-history.{html,js}`, `style.css` |
| 1.8 | Achievement badges: Bronze / Silver / Gold / Platinum / Variety / Speed / Marathon | 3 | `12d3413` | `reading-history.{html,js}`, `style.css` |
| 2.8 | Trend Analysis: borrows-over-time chart with weekly/monthly toggle | 4 | `10cd02a` | `LibraryApiHandlers.java`, `author-stats.{html,js}` |
| 2.8 | Customizable Dashboard: per-section visibility persisted in localStorage | 4 | `10cd02a` | `author-stats.{html,js}`, `style.css` |
| 2.8 | Download Report: `/api/author/stats-export` → multi-section CSV | 4 | `10cd02a` | `LibraryApiHandlers.java`, `author-stats.js` |
| 2.9 | Sentiment Analysis: positive / neutral / negative badge per review | 5 | `437936e` | `author-reviews.{html,js}`, `style.css` |
| 2.9 | Reply Templates dropdown that appends to reply textarea | 5 | `437936e` | `author-reviews.{html,js}` |
| 2.9 | Feedback Analytics: sentiment doughnut + rating-distribution bar chart | 5 | `437936e` | `author-reviews.{html,js}`, `style.css` |
| 3.8 | Bulk Operations: select-all + delete-selected with partial-success reporting | 6 | `49cb72c` | `LibraryApiHandlers.java`, `librarian-manage-published.{html,js}` |
| 3.8 | Version History: in-memory edit ledger (last 50 per book) + history endpoint | 6 | `49cb72c` | `LibraryApiHandlers.java`, `librarian-manage-published.{html,js}` |
| 3.8 | Admin Tools: 4-metric library overview panel | 6 / 6.5 | `49cb72c`, `393f2ac` | `LibraryApiHandlers.java`, `librarian-manage-published.{html,js}` |
| 3.7 | Keyword search across book title / requester / reason | 7 | `9ed8fea` | `librarian-book-requests.{html,js}` |
| 3.7 | Multi-column sort with asc / desc / unsorted cycle | 7 | `9ed8fea` | `librarian-book-requests.{html,js}` |
| 3.7 | Priority badge derived from request age (HIGH / MED / NORMAL) | 7 | `9ed8fea` | `librarian-book-requests.{html,js}`, `style.css` |
| 3.7 | Bulk approve and bulk reject with partial-success reporting | 7 | `9ed8fea` | `librarian-book-requests.{html,js}` |

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
