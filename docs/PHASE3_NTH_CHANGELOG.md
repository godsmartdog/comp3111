# Phase 3 NTH — Slice-by-Slice Changelog

Oldest slice first. Dates are commit author dates (Asia/Hong_Kong).

---

## Slice 1 — feat(phase3-nth): slice 1 — anon reviews, sort, dup-request, priority, filters, add-user (`6e77839`)

**Date:** 2026-05-02 16:56:36 +0800
**Files touched:** `src/Library/Model/BookRequest2.java`, `src/Library/Model/BookReview.java`, `src/Library/Service/BookRequestService.java`, `src/Library/Service/BookReviewService.java`, `src/Library/Service/LibrarianService3.java`, `src/Library/Ui/LibraryApiHandlers.java`, `src/Library/Ui/web/author-reviews.js`, `src/Library/Ui/web/librarian-book-requests.html`, `src/Library/Ui/web/librarian-book-requests.js`, `src/Library/Ui/web/librarian-manage-published.html`, `src/Library/Ui/web/librarian-manage-published.js`, `src/Library/Ui/web/librarian-users.html`, `src/Library/Ui/web/librarian-users.js`, `src/Library/Ui/web/my-reviews.js`, `src/Library/Ui/web/request-new-book.html`, `src/Library/Ui/web/request-new-book.js`, `src/Library/Ui/web/shared.js`, `src/Library/Ui/web/staff-reader.html`, `src/Library/Ui/web/staff-reader.js`, `src/Library/Ui/web/student-reader.html`, `src/Library/Ui/web/student-reader.js`, `src/Library/Ui/web/style.css`
**Lines:** +488 / −32

**Bullets covered:**
- 1.9 Anonymous review submission and display masking
- 1.9 Review sort: recent / highest / lowest
- 1.10 Duplicate book-request detection (same title + pending/approved)
- 1.10 Librarian priority flag on book requests, sorted top
- 3.8 Genre / author / status filters on Manage Published Books
- 3.4 Add-new-user form on Manage All Users (any role)

**Summary:**
> No DB, no new dependencies. In-memory repositories only.

---

## Slice 1.5 — fix(phase3-nth): slice 1.5 — routing 405s, dup-request normalization, filter reset, genre list (`4dd9c65`)

**Date:** 2026-05-02 17:49:13 +0800
**Files touched:** `src/Library/Service/BookRequestService.java`, `src/Library/Ui/LibraryApiHandlers.java`, `src/Library/Ui/web/librarian-book-requests.js`, `src/Library/Ui/web/librarian-manage-published.html`, `src/Library/Ui/web/librarian-manage-published.js`, `src/Library/Ui/web/librarian-users.js`
**Lines:** +27 / −12

**Bullets covered:**
- (Slice 1 follow-ups; no new bullets)

**Summary:**
> - Rename /api/librarian/book-request/priority → /api/librarian/book-request-priority
>   (avoid HttpServer prefix-context collision with /api/librarian/book-request)
> - Rename /api/librarian/users/create → /api/librarian/users-create
>   (avoid collision with /api/librarian/users)
> - Normalize requesterUsername in dup-request title check
> - Reset button on Manage Published Books also clears keyword search input
> - Add Education / Business / Science / Art / Self-Help to filterGenre dropdown

---

## Slice 2 — feat(phase3-nth): slice 2 — borrowed records search, tabs, CSV export, overdue red (`afedd57`)

**Date:** 2026-05-02 19:25:15 +0800
**Files touched:** `src/Library/Ui/LibraryApiHandlers.java`, `src/Library/Ui/web/librarian-records.html`, `src/Library/Ui/web/librarian-records.js`, `src/Library/Ui/web/style.css`
**Lines:** +195 / −6

**Bullets covered:**
- 3.6 Keyword search across book title / borrower / borrow ID
- 3.6 Advanced filter tabs: All / Active / Overdue / Returned
- 3.6 Overdue rows highlighted red with badge
- 3.6 CSV export endpoint /api/librarian/borrowed-records-export with filtering

**Summary:**
> No DB. In-memory repositories. No new dependencies.

---

## Slice 2.5 — fix(phase3-nth): slice 2.5 — CSV export uses fetch+blob (auth-correct), tab button color (`91f5735`)

**Date:** 2026-05-02 19:42:31 +0800
**Files touched:** `src/Library/Ui/web/librarian-records.js`, `src/Library/Ui/web/style.css`
**Lines:** +24 / −7

**Bullets covered:**
- (Slice 2 follow-up; no new bullets)

**Summary:**
> - Export CSV: replace window.location.href with fetch+blob+anchor click so
>   the same auth headers used by api() are sent. Fixes 401 "Missing session".
> - Tab buttons: explicit text color so they're readable on white/blue background.

---

## Slice 3 — feat(phase3-nth): slice 3 — reading history CSV export, Chart.js insights, badges (`12d3413`)

**Date:** 2026-05-02 20:00:52 +0800
**Files touched:** `src/Library/Ui/LibraryApiHandlers.java`, `src/Library/Ui/web/reading-history.html`, `src/Library/Ui/web/reading-history.js`, `src/Library/Ui/web/style.css`
**Lines:** +354 / −1

**Bullets covered:**
- 1.8 Export reading history as CSV (filtered, server-side)
- 1.8 Graphical insights: genre doughnut + duration line chart (Chart.js via CDN)
- 1.8 Achievement badges: 7 badges derived from history (Bronze/Silver/Gold/Platinum/Variety/Speed/Marathon)

**Summary:**
> No DB. No npm/Maven. Chart.js loaded from cdn.jsdelivr.net pinned 4.4.0.

---

## Slice 4 — feat(phase3-nth): slice 4 — author stats trend chart, customizable dashboard, CSV report (`10cd02a`)

**Date:** 2026-05-02 20:24:33 +0800
**Files touched:** `src/Library/Ui/LibraryApiHandlers.java`, `src/Library/Ui/web/author-stats.html`, `src/Library/Ui/web/author-stats.js`, `src/Library/Ui/web/style.css`
**Lines:** +344 / −11

**Bullets covered:**
- 2.8 Trend Analysis: borrows-over-time line chart with weekly/monthly toggle
- 2.8 Customizable Dashboard: per-section checkbox visibility, persisted in localStorage
- 2.8 Download Report: GET /api/author/stats-export → multi-section CSV

**Summary:**
> Reuses Chart.js 4.4.x CDN. No DB. No new dependencies.

---

## Slice 5 — feat(phase3-nth): slice 5 — review sentiment badges, reply templates, feedback analytics (`437936e`)

**Date:** 2026-05-02 20:59:45 +0800
**Files touched:** `src/Library/Ui/web/author-reviews.html`, `src/Library/Ui/web/author-reviews.js`, `src/Library/Ui/web/style.css`
**Lines:** +181 / −9

**Bullets covered:**
- 2.9 Sentiment Analysis: rule-based positive/negative/neutral badge per review
- 2.9 Reply Templates: dropdown of 3 hardcoded templates that append to reply textarea
- 2.9 Feedback Analytics: sentiment doughnut + rating distribution bar chart

**Summary:**
> Frontend-only. No backend changes. No new dependencies.

---

## Slice 6 — feat(phase3-nth): slice 6 — manage published bulk delete, version history, admin stats (`49cb72c`)

**Date:** 2026-05-02 21:24:15 +0800
**Files touched:** `src/Library/Ui/LibraryApiHandlers.java`, `src/Library/Ui/web/librarian-manage-published.html`, `src/Library/Ui/web/librarian-manage-published.js`
**Lines:** +322 / −1

**Bullets covered:**
- 3.8 Bulk Operations: select-all + delete-selected with partial-success reporting
- 3.8 Version History: in-memory edit ledger (last 50 per book) + history endpoint
- 3.8 Admin Tools: 4-metric library overview panel

**Summary:**
> No DB. No new dependencies. In-memory ledger.

---

## Slice 6.5 — fix(phase3-nth): slice 6.5 — admin stats counts authors by full name (`393f2ac`)

**Date:** 2026-05-02 21:39:39 +0800
**Files touched:** `src/Library/Ui/LibraryApiHandlers.java`
**Lines:** +2 / −1

**Bullets covered:**
- (Slice 6 follow-up; no new bullets)

**Summary:**
> The `Authors with Published Books` metric counted distinct authorUsername,
> but preloaded demo books share one seed username while showing distinct
> authorFullName in the table. Switch to counting by authorFullName (lowercase
> trimmed), falling back to username when full name is empty.

---

## Slice 7 — feat(phase3-nth): slice 7 — book request admin (search/sort/priority/bulk) + 2.7 styles (`9ed8fea`)

**Date:** 2026-05-02 22:01:37 +0800
**Files touched:** `src/Library/Ui/web/librarian-book-requests.html`, `src/Library/Ui/web/librarian-book-requests.js`, `src/Library/Ui/web/style.css`
**Lines:** +198 / −9

**Bullets covered:**
- 3.7 Keyword search across book title / requester / reason (Enter-to-apply added; existing input retained)
- 3.7 Multi-column sort with asc/desc/unsorted cycle
- 3.7 Priority badge derived from request age (HIGH/MED/NORMAL)
- 3.7 Bulk approve and bulk reject with partial-success reporting
- 2.7 skipped: no author-style customization screen exists yet

**Summary:**
> No DB. No new dependencies.
