# Library Management System (E-Book Library System) - Phase 1 设计框架

## 目标
实现四类用户：STUDENT / STAFF / AUTHOR / LIBRARIAN 的注册、登录与业务功能：
- 学生/职员：查看可借书籍、借书（含借阅上限与确认）
- 作者：提交新书（待馆员审批）
- 馆员：审批新书（通过/拒绝，含批量操作与文件预览）

## 分层结构
- ui：控制台/JavaFX 层，负责输入输出与页面流程，不写业务规则
- service：业务逻辑（注册、登录、借书、提交、审批、推荐、限制）
- repository：数据访问层（Phase1 用内存实现，后续可替换文件/数据库）
- model：实体类与枚举
- security：密码哈希、密码策略
- exception：业务异常类型（Validation / Authentication / NotFound 等）

## 关键业务规则映射
- 用户名全局唯一：UserRepository.existsByUsername(username)
- 角色登录限制：AuthService.login(username, password, expectedRole)
- 密码强度：PasswordPolicy.validate(password)
- 书籍可借状态：Book.availability == AVAILABLE 才可借
- 借阅上限：BorrowService.canBorrow(user) (max=5)
- 作者提交：Submission.status = PENDING
- 馆员通过：Submission.status=APPROVED + Book 创建 + publishDate=approvedDate
- 馆员拒绝：Submission.status=REJECTED + comment 保存

## 数据结构建议
- UserRepository: Map<String, User>
- AuthorProfileRepository: Map<String, AuthorProfile>
- LibrarianProfileRepository: Map<String, LibrarianProfile>
- SubmissionRepository: Map<String, BookSubmission>
- BookRepository: Map<String, Book>
- BorrowRepository: List<BorrowRecord> 或 Map<username, List<BorrowRecord>>

## UI 页面建议
- MainMenu: 选择角色入口
- Register/Login 页面：每个角色一致的提示与流程
- Student/Staff: AvailableBooksScreen + BorrowFlow
- Author: PublishNewBookScreen
- Librarian: PendingSubmissionsScreen (approve/reject/bulk + preview file)

# Library Management System (E-Book Library System) - Phase 1 Design Framework

## Goal
Implement registration, login, and core business features for four user roles: **STUDENT / STAFF / AUTHOR / LIBRARIAN**.

- **Student/Staff**: View available books, borrow books (with borrow limit and confirmation).
- **Author**: Submit new books for publication (pending librarian approval).
- **Librarian**: Review and approve/reject submitted books (including bulk actions and file preview/download).

---

## Layered Architecture
- **ui**: Console / JavaFX layer. Handles input/output and page flow only. No business rules here.
- **service**: Business logic (registration, login, borrowing, submissions, approvals, recommendations, limits).
- **repository**: Data access layer (Phase 1 uses in-memory storage; later can be replaced by file/database).
- **model**: Entity classes and enums.
- **security**: Password hashing and password policy validation.
- **exception**: Business exceptions (Validation / Authentication / NotFound, etc.).

---

## Key Business Rules Mapping
- **Global unique username**: `UserRepository.existsByUsername(username)`
- **Role-based login restriction**: `AuthService.login(username, password, expectedRole)`
- **Password strength validation**: `PasswordPolicy.validate(password)`
- **Book borrow availability**: Only allow borrow when `Book.availability == AVAILABLE`
- **Borrow limit**: `BorrowService.canBorrow(user)` (max = 5)
- **Author submission**: `Submission.status = PENDING`
- **Librarian approval**: `Submission.status = APPROVED` + create `Book` + set `publishDate = approvedDate`
- **Librarian rejection**: `Submission.status = REJECTED` + save rejection comment

---

## Recommended Data Structures
- **UserRepository**: `Map<String, User>`
- **AuthorProfileRepository**: `Map<String, AuthorProfile>`
- **LibrarianProfileRepository**: `Map<String, LibrarianProfile>`
- **SubmissionRepository**: `Map<String, BookSubmission>`
- **BookRepository**: `Map<String, Book>`
- **BorrowRepository**: `List<BorrowRecord>` or `Map<String, List<BorrowRecord>>` keyed by username

---

## Suggested UI Pages / Flows
- **MainMenu**: Entry screen to select user role/portal
- **Register/Login pages**: Consistent UI flow across all roles
- **Student/Staff portal**: AvailableBooksScreen + BorrowFlow
- **Author portal**: PublishNewBookScreen
- **Librarian portal**: PendingSubmissionsScreen (approve/reject/bulk + file preview/download)
