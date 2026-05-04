# Phase 3 NTH — Repo Audit

## 1. Outstanding `// TODO(slice…)` comments

```bash
$ grep -rnE 'TODO\(slice' src/ docs/
```

**0 matches.** No slice deferred work via `TODO(slice…)` markers.

## 2. Pre-existing failing integration tests

Captured from `cd src/Library && bash ./run-library.sh --tests`:

```
[FAIL] review submission and book rating summaries -> Book is currently unavailable.
[FAIL] author profile update success -> Current password is required to save profile changes.
[FAIL] author profile update validation -> Exception message did not contain expected fragment: Password must be between 8 and 64 characters
[FAIL] author notifications summary unread count -> author profile update should generate one unread notification | expected=200, actual=400
[FAIL] librarian can view approved books endpoint -> response should include available status
[FAIL] librarian profile validation failures -> Exception message did not contain expected fragment: Current password is required to change password
[FAIL] librarian non-password profile update works without current password -> librarian non-password profile update should work without current password | expected=200, actual=400
[FAIL] librarian notifications list and mark read success -> profile update should succeed | expected=200, actual=400
[FAIL] librarian notification ownership boundary -> owner A profile update should succeed | expected=200, actual=400
[FAIL] student/staff notifications list and mark read success -> profile update should generate a notification | expected=200, actual=400
[FAIL] student/staff notification ownership boundary endpoint -> owner A profile update should generate notification | expected=200, actual=400
[FAIL] student/staff notification archive and unarchive success -> profile update should create a notification to archive | expected=200, actual=400
[FAIL] student/staff notification archive scope filtering and unread consistency -> first profile update should succeed | expected=200, actual=400
[FAIL] student/staff profile update without password does not require current password -> non-password profile update should succeed without current password | expected=200, actual=400
[FAIL] author and librarian forbidden from student/staff notification APIs -> author should be forbidden from student/staff notifications list | expected=401, actual=200
[FAIL] shared filters reject invalid recommendation limits -> invalid numeric limit should be rejected | expected=400, actual=200
```

**16 failures**, all pre-Phase-3, predominantly clustered around profile-update password-validation flow and notification-ownership boundary. None were addressed by Phase 3 NTH work — by spec scope, NTH targets feature enhancements rather than bug-fixing the existing test baseline.

Final tally: `Passed: 90, Failed: 16`.

## 3. Dead code / stub flags

```bash
$ grep -rnE 'XXX|FIXME|HACK' src/Library
```

**0 matches.**

```bash
$ grep -rn 'console.log(' src/Library/Ui/web/
```

**0 matches.** No frontend debug logs left behind.

## 4. In-memory state warning

Slice 6 (`49cb72c`) introduced an in-memory `bookVersions` ledger living
on `LibraryApiHandlers` as `Map<String, List<BookVersion>>`, capped at 50
entries per book. Slice 7 (`9ed8fea`) priority badges (HIGH / MED / NORMAL)
are computed client-side from `requestedDate` age. Both reset on server
restart by design — no DB layer was added during Phase 3 NTH. The version
ledger is also cleared per-book when a book is bulk-deleted in slice 6's
handler.

## 5. Coverage matrix correction (slice 8b)

The original `PHASE3_NTH_COVERAGE.md` matrix conflated three things:
features that already shipped before the `phase-3-nice-to-have` branch,
features built during Phase 3 slice work, and polish that resembles a
spec bullet but is not in the spec word-for-word. Slice 8b reconciles
the matrix with the actual repo state. Ten Phase 2 carryover bullets
(profile photo upload, password strength meter, unread counter, priority
notifications, mark-read & delete, archive notifications, role-based
user filter, manage-librarian accounts, request tracking, request
history) were verified present in the repo and added to the matrix as
`Phase 2 carryover` rows with commit `(existing)`. Six Slice-1-through-7
entries (Slice 2 borrowed-records keyword search; Slice 6 admin-stats
panel; Slice 7 keyword search, multi-column sort, priority badge, and
bulk approve/reject on the book-request queue) were over-claims and
have been relocated to a dedicated "Over-claims" section — SHAs and
file lists preserved for traceability, but flagged as polish, not
bullet coverage. Bullet #11 (auto-logout on password change) was
verified implemented (`invalidateSessionsByUsername` at
`LibraryApiHandlers.java:818`, `:2169`, `:3491`) and removed from the
outstanding list; bullet #10 (re-auth on any profile change) is not
implemented and remains. Sixteen bullets remain outstanding. Plan:
Slice 9+ to chip away at them.

Slice 12 reclassifies 1.7 #7 (Search/Filter Notifications) as Phase 2 carryover; auto-return promoted to first-class category for Slice 9 alignment.
