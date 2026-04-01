# S2026_Project Group 21

# COMP3111 Project

## Library Management System (E-Book Library System)

## 1. 项目概要

本项目是 COMP3111 的电子书图书馆管理系统。系统支持 3 个主要门户和 4 种用户角色：

- Student
- Staff
- Author
- Librarian

本系统采用分层设计实现。虽然很多核心功能写在 Service 层中，但这些函数同时也对应课程要求里的 Task 1.x、Task 2.x、Task 3.x，所以本说明文件会从两个角度来介绍整个项目：

- 按系统架构划分
- 按任务需求 Task 1.x、2.x、3.x 划分

这一点很重要。比如 `AuthorService2` 在架构上属于 Service 层，但它的具体函数同时又属于 Task 2.1、Task 2.2 和 Task 2.3。

## 2. 所有任务与所有阶段的共同要求

### 2.1 非功能性要求

本项目对所有任务共同要求的对应方式如下：

- 系统提供了较友好的 JavaFX 图形界面。
- 学生、职员、作者、馆员的操作流程保持一致，基本都遵循注册、登录、执行功能、反馈结果的模式。
- 用户凭据不会以明文形式存储，而是通过哈希方式安全保存。
- 代码按包分层组织，便于阅读、维护和扩展。

### 2.2 技术要求

本项目对技术要求的对应方式如下：

- 通过 Model、Repository、Service、Exception、Security、UI 六层划分，提升可读性和可维护性。
- 对用户、书籍、草稿、投稿、借阅记录使用了合适的数据结构。
- 业务规则集中在 Service 中，而不是散落在 UI 中。
- 异常统一定义，便于复用与错误处理。

### 2.3 提交与 README 要求

本文件可以作为 README 风格的项目说明。内容包括：

- 项目整体说明
- 架构说明
- 各任务拆分
- 主要类与函数说明
- 课程需求与代码实现之间的对应关系

## 3. 系统架构：六大部分

本项目划分为 6 个核心部分：

1. Model
2. Repository
3. Service
4. Exception
5. Security
6. UI

### 3.1 Model

Model 包定义系统中的核心数据对象。

- `User`：保存用户名、全名、密码哈希、角色和创建时间
- `Role`：定义 `STUDENT`、`STAFF`、`AUTHOR`、`LIBRARIAN`
- `Book`：保存已批准进入馆藏的图书
- `BorrowRecord`：保存借阅记录
- `AuthorProfile2`：保存作者简介
- `LibrarianProfile3`：保存馆员员工编号
- `BookDraft2`：保存作者未完成的投稿草稿
- `BookSubmission2`：保存等待馆员审核的正式投稿
- `SubmissionState`：定义 `PENDING`、`APPROVED`、`REJECTED`

### 3.2 Repository

Repository 包负责数据存取。

- `UserRepository` / `MemoryUserRepository`
- `BookRepository` / `MemoryBookRepository`
- `BorrowRepository` / `MemoryBorrowRepository`
- `AuthorProfileRepository2` / `MemoryAuthorProfileRepository2`
- `BookDraftRepository2` / `MemoryBookDraftRepository2`
- `BookSubmissionRepository2` / `MemoryBookSubmissionRepository2`
- `LibrarianProfileRepository3` / `MemoryLibrarianProfileRepository3`

当前项目全部使用内存实现，适合课程项目的演示和阶段性开发。

### 3.3 Service

Service 包包含主要业务逻辑。

- `AuthService` 对应 Task 1 的注册与登录
- `BookService` 对应 Task 1.3 可借图书界面
- `BorrowService` 对应 Task 1.4 借书功能
- `RecommendationService` 对应 Task 1 的推荐增强功能
- `AuthorService2` 对应 Task 2.1、2.2、2.3
- `AuthorDraftService` 对应 Task 2.3 的草稿自动保存
- `FileService` 对应 Task 2.3 与 Task 3.3 的文件校验和预览
- `LibrarianService3` 对应 Task 3.1、3.2、3.3

### 3.4 Exception

Exception 包定义统一的运行时异常。

- `AuthenticationException`
- `BusinessException`
- `NotFoundException`
- `ValidationException`

### 3.5 Security

Security 包集中管理安全与共享规则。

- `PasswordHasher`
- `PasswordPolicy`
- `SecurityConfig`
- `SessionManager`

### 3.6 UI

UI 包负责用户交互。

- `LibraryManagementApp`：JavaFX 程序入口
- `LibraryManagementUI`：主 JavaFX 界面，也是最重要的 UI 类
- `EnhancementHelper`：一些 UI 增强辅助函数

## 4. 按六层架构说明主要类与函数

这一部分先从架构角度简要概括主要类与函数，再进入后面的任务拆分。

### 4.1 Model 主要函数

#### User

- `User(String username, String fullName, String passwordHash, Role role)`：创建用户对象
- `getUsername()`：返回用户名
- `getFullName()`：返回全名
- `getPasswordHash()`：返回密码哈希
- `getRole()`：返回角色
- `getCreatedAt()`：返回创建时间
- `equals(Object obj)`：按用户名比较两个用户是否相同
- `hashCode()`：基于用户名生成哈希值

#### Book

- `Book(String title, String authorFullName, String summary)`：创建一本尚未批准的书
- `getId()`：返回书本编号
- `getTitle()`：返回书名
- `getAuthorFullName()`：返回作者姓名
- `getPublishDate()`：返回出版或批准日期
- `isApproved()`：检查是否已批准
- `isAvailable()`：检查是否可借
- `getSummary()`：返回摘要
- `approve(LocalDate publishDate)`：将图书标记为已批准并可借
- `setAvailable(boolean available)`：更新图书可借状态

#### BorrowRecord

- `BorrowRecord(String username, String bookId, LocalDate borrowDate, LocalDate dueDate)`：创建借阅记录
- `getId()`：返回借阅记录编号
- `getUsername()`：返回借阅用户
- `getBookId()`：返回书本编号
- `getBorrowDate()`：返回借出日期
- `getDueDate()`：返回到期日期
- `isReturned()`：检查是否已归还
- `markReturned()`：将记录标记为已归还

#### AuthorProfile2

- `AuthorProfile2(String username, String bio)`：创建作者资料
- `getUsername()`：返回作者用户名
- `getBio()`：返回作者简介

#### LibrarianProfile3

- `LibrarianProfile3(String username, String employeeId)`：创建馆员资料
- `getUsername()`：返回馆员用户名
- `getEmployeeId()`：返回员工编号

#### BookDraft2

- `BookDraft2(String authorUsername)`：创建空草稿
- `getAuthorUsername()`：返回草稿所属作者
- `getTitle()`：返回草稿标题
- `getGenres()`：返回类型列表
- `getDescription()`：返回描述
- `getFilePath()`：返回文件路径
- `getLastSavedAt()`：返回最后自动保存时间
- `setTitle(String title)`：更新标题并刷新时间
- `setGenres(List<String> genres)`：更新类型并刷新时间
- `setDescription(String description)`：更新描述并刷新时间
- `setFilePath(String filePath)`：更新文件路径并刷新时间

#### BookSubmission2

- `BookSubmission2(...)`：创建新的待审核投稿
- `getId()`：返回投稿编号
- `getTitle()`：返回书名
- `getAuthorUsername()`：返回作者用户名
- `getAuthorFullName()`：返回作者全名
- `getGenres()`：返回图书类型
- `getDescription()`：返回描述
- `getFileName()`：返回文件路径或文件名
- `getSubmittedDate()`：返回投稿日期
- `getStatus()`：返回投稿状态
- `getLibrarianComment()`：返回馆员评论
- `getApprovedDate()`：返回批准日期
- `approve(String comment)`：将投稿改为批准状态
- `reject(String comment)`：将投稿改为拒绝状态

### 4.2 Repository 主要函数

#### UserRepository / MemoryUserRepository

- `findByUsername(String username)`：根据用户名查找用户
- `save(User user)`：保存用户
- `existsByUsername(String username)`：检查用户名是否重复
- `findAll()`：返回所有用户

#### BookRepository / MemoryBookRepository

- `save(Book book)`：保存图书
- `findById(String id)`：通过 id 查找图书
- `findAll()`：返回所有图书
- `searchByTitleOrAuthor(String keyword)`：按书名或作者搜索

#### BorrowRepository / MemoryBorrowRepository

- `save(BorrowRecord record)`：保存借阅记录
- `findById(String id)`：按 id 查找记录
- `findByUsername(String username)`：返回某个用户的借阅历史
- `findActiveByUsernameAndBookId(String username, String bookId)`：查找某本书当前是否被某用户借出
- `findAll()`：返回所有借阅记录

#### AuthorProfileRepository2 / MemoryAuthorProfileRepository2

- `save(AuthorProfile2 profile)`：保存作者资料
- `findByUsername(String username)`：按用户名查找作者资料

#### BookDraftRepository2 / MemoryBookDraftRepository2

- `save(BookDraft2 draft)`：保存草稿
- `findByAuthorUsernameAndTitle(String authorUsername, String title)`：按作者与标题查找草稿
- `findAllByAuthorUsername(String authorUsername)`：返回作者全部草稿
- `deleteByAuthorUsernameAndTitle(String authorUsername, String title)`：删除草稿

#### BookSubmissionRepository2 / MemoryBookSubmissionRepository2

- `save(BookSubmission2 submission)`：保存投稿
- `findById(String id)`：按 id 查找投稿
- `findAll()`：返回所有投稿
- `findByStatus(SubmissionState status)`：按状态筛选投稿
- `findByAuthorUsername(String username)`：返回某作者的全部投稿

#### LibrarianProfileRepository3 / MemoryLibrarianProfileRepository3

- `save(LibrarianProfile3 profile)`：保存馆员资料
- `findByUsername(String username)`：查找馆员资料

### 4.3 Service 主要函数

#### AuthService

- `registerStudentOrStaff(String username, String fullName, String password, Role role)`：对应 Task 1.1 注册
- `loginStudentOrStaff(String username, String password, Role expectedRole)`：对应 Task 1.2 登录
- `logout()`：退出登录
- `isLoggedIn()`：检查是否已登录
- `getCurrentUser()`：获取当前登录用户

#### BookService

- `listApprovedBooksWithAvailability()`：对应 Task 1.3 图书列表
- `searchApprovedBooks(String keyword)`：对应 Task 1.3 图书搜索

#### BorrowService

- `borrowBook(String username, String bookId)`：对应 Task 1.4 默认借阅
- `borrowBook(String username, String bookId, int borrowDays)`：对应 Task 1.4 完整借阅流程
- `returnBook(String username, String bookId)`：处理还书
- `listActiveBorrowsByUser(String username)`：查看当前借阅中图书

#### RecommendationService

- `recommendTopPopular(int limit)`：对应 Task 1 的推荐功能

#### AuthorService2

- `registerAuthor(String username, String fullName, String password, String bio)`：对应 Task 2.1
- `loginAuthor(String username, String password)`：对应 Task 2.2
- `publishBook(String authorUsername, String title, List<String> genres, String description, String fileName)`：对应 Task 2.3
- `getSupportedGenres()`：支持 Task 2.3 多类型选择
- `previewBook(String title, List<String> genres, String description)`：支持 Task 2.3 预览功能

#### AuthorDraftService

- `autoSave(String authorUsername, String title, List<String> genres, String description, String filePath)`：支持 Task 2.3 草稿自动保存
- `loadDraft(String authorUsername, String title)`：读取单个草稿
- `loadDrafts(String authorUsername)`：读取全部草稿
- `clearDraft(String authorUsername, String title)`：正式提交后清除草稿

#### FileService

- `validateSubmissionFile(String filePath)`：提交前校验文件
- `getPreviewDetails(String filePath)`：支持馆员内容预览

#### LibrarianService3

- `registerLibrarian(String username, String fullName, String password, String employeeId)`：对应 Task 3.1
- `loginLibrarian(String username, String password)`：对应 Task 3.2
- `getPendingSubmissions()`：对应 Task 3.3 待审核列表
- `approveSubmission(String submissionId, String comment)`：对应 Task 3.3 批准流程
- `rejectSubmission(String submissionId, String comment)`：对应 Task 3.3 拒绝流程
- `bulkApprove(List<String> submissionIds, String comment)`：对应 Task 3.3 批量批准
- `bulkReject(List<String> submissionIds, String comment)`：对应 Task 3.3 批量拒绝

### 4.4 Exception 主要函数

- `AuthenticationException(String message)` 与 `AuthenticationException(String message, Throwable cause)`：用于登录失败
- `BusinessException(String message)` 与 `BusinessException(String message, Throwable cause)`：用于业务规则错误
- `NotFoundException(String message)`：用于目标图书或投稿不存在
- `ValidationException(String message)`：用于输入数据无效

### 4.5 Security 主要函数

#### PasswordHasher

- `sha256(String rawPassword)`：兼容旧版逻辑
- `hashPassword(String rawPassword)`：对密码进行哈希
- `matches(String rawPassword, String storedHash)`：验证密码是否匹配

#### PasswordPolicy

- `validate(String password)`：检查密码强度

#### SecurityConfig

- `MAX_BORROW_LIMIT`：最大借阅数限制
- `DEFAULT_BORROW_DAYS`：默认借阅天数
- `MAX_FILE_SIZE_BYTES`：上传文件大小限制
- `ALLOWED_EXTENSIONS`：允许的文件格式

#### SessionManager

- `getInstance()`：获取单例实例
- `createSession(User user)`：创建登录会话
- `destroySession()`：清除会话
- `getCurrentUser()`：获取当前用户
- `isAuthenticated()`：判断是否已登录
- `getLoginTime()`：获取登录时间
- `setAttribute(String key, Object value)`：保存会话数据
- `getAttribute(String key)`：读取会话数据

### 4.6 UI 主要函数

#### LibraryManagementApp

- `start(Stage stage)`：启动 JavaFX 界面
- `main(String[] args)`：程序入口
- `createContext()`：组装 Repository、Service 与演示数据

#### LibraryManagementUI

- `createContent()`：创建主界面
- `buildStudentStaffTab()`：构建 Task 1 界面
- `buildAuthorTab()`：构建 Task 2 界面
- `buildLibrarianTab()`：构建 Task 3 界面
- `handleBorrow()`：执行借书逻辑
- `refreshBookResults()`：刷新书籍列表
- `refreshRecommendations()`：刷新推荐列表
- `refreshActiveBorrows()`：刷新借阅中图书列表
- `refreshDrafts()`：刷新作者草稿列表
- `refreshPendingSubmissions()`：刷新待审核投稿列表
- `updateBorrowButtonState()`：更新借书按钮状态
- `populateDraft(BookDraft2 draft)`：将草稿填回表单
- `parseGenres(String rawGenres)`：解析多类型输入
- `showError(String header, String message)`：显示错误提示框
- `showInfo(String header, String message)`：显示成功提示框

#### ConsoleUI

- `start()` 与 `start(Scanner sc)`：运行学生和职员控制台门户
- `register(Scanner sc)`：对应 Task 1.1
- `login(Scanner sc)`：对应 Task 1.2
- `listBooks()`：对应 Task 1.3
- `borrow(Scanner sc)`：对应 Task 1.4

#### AuthorConsoleUI2

- `start(Scanner sc)`：运行作者控制台门户
- `register(Scanner sc)`：对应 Task 2.1
- `login(Scanner sc)`：对应 Task 2.2
- `loadDraft(Scanner sc)`：对应 Task 2.3 草稿载入
- `publish(Scanner sc)`：对应 Task 2.3 投稿

#### LibrarianConsoleUI3

- `start(Scanner sc)`：运行馆员控制台门户
- `register(Scanner sc)`：对应 Task 3.1
- `login(Scanner sc)`：对应 Task 3.2
- `listPending()`：对应 Task 3.3
- `approve(Scanner sc)`：执行批准
- `reject(Scanner sc)`：执行拒绝
- `previewSubmissionFile(Scanner sc)`：预览文件
- `bulkApprove(Scanner sc)`：批量批准
- `bulkReject(Scanner sc)`：批量拒绝

#### EnhancementHelper

- `printAvailability(Book b)`：输出图书可借状态
- `quickReadSummary(Book b)`：支持快速阅读摘要
- `confirmBorrow(String bookTitle, int durationDays)`：输出借阅确认信息
- `getAvailabilityColor(Book b)`：支持红黑颜色区分
- `buildBorrowConfirmation(String bookTitle, int durationDays)`：构建借阅确认弹窗文本
- `printBorrowResult(BorrowRecord record)`：输出借阅成功结果

## 5. Phase 1 主要功能

## 5.1 Task 1 Student/Staff Portal

Task 1 包含四个主要子任务：

1. Student/Staff Registration
2. Student/Staff Login
3. Available Book Screen
4. Borrow Book

### 5.1.1 Task 1.1 Student/Staff Registration

需求概要：

- 用户输入用户名、全名、密码和角色
- 用户名必须唯一
- 密码必须满足校验规则
- 角色只能是 Student 或 Staff
- 系统必须提供成功或失败反馈

涉及文件按六层对应如下：

- Model：`User`、`Role`
- Repository：`UserRepository`、`MemoryUserRepository`
- Service：`AuthService.registerStudentOrStaff(...)`
- Exception：`ValidationException`
- Security：`PasswordHasher`、`PasswordPolicy`、`SessionManager`
- UI：`LibraryManagementUI.buildStudentStaffTab()`、`ConsoleUI.register(Scanner sc)`

函数细节说明：

- `AuthService.registerStudentOrStaff(...)`
  检查角色只能是 `STUDENT` 或 `STAFF`
  检查用户名和全名不能为空
  检查密码强度
  检查用户名是否已存在
  使用哈希保存密码
  创建并保存新的 `User`

UI 如何体现：

- JavaFX 在 `buildStudentStaffTab()` 中提供注册表单，并通过弹窗显示成功或失败信息
- Console 版本通过 `register(Scanner sc)` 读取输入并在成功后跳转登录

### 5.1.2 Task 1.2 Student/Staff Login

需求概要：

- 用户输入用户名和密码
- 系统校验凭据是否正确
- 系统校验账号是否属于所选角色
- 系统返回成功或失败提示

涉及文件按六层对应如下：

- Model：`User`、`Role`
- Repository：`UserRepository`、`MemoryUserRepository`
- Service：`AuthService.loginStudentOrStaff(...)`
- Exception：`AuthenticationException`、`ValidationException`
- Security：`PasswordHasher`、`SessionManager`
- UI：`LibraryManagementUI.buildStudentStaffTab()`、`ConsoleUI.login(Scanner sc)`

函数细节说明：

- `AuthService.loginStudentOrStaff(...)`
  先按用户名查找用户
  再检查所选角色是否与账号角色一致
  验证密码哈希是否匹配
  登录成功后创建 session

UI 如何体现：

- JavaFX 登录成功后更新状态标签，并刷新当前借阅列表
- Console 登录成功后会打印欢迎信息并跳转到图书列表

### 5.1.3 Task 1.3 Available Book Screen

需求概要：

- 显示已批准图书
- 显示书名、作者、出版日期、可借状态和摘要

涉及文件按六层对应如下：

- Model：`Book`
- Repository：`BookRepository`、`MemoryBookRepository`
- Service：`BookService.listApprovedBooksWithAvailability()`、`BookService.searchApprovedBooks(...)`、`RecommendationService.recommendTopPopular(...)`
- Exception：`ValidationException`、`BusinessException`
- Security：`SecurityConfig` 中的共享规则
- UI：`LibraryManagementUI.buildStudentStaffTab()`、`ConsoleUI.listBooks()`、`EnhancementHelper`

函数细节说明：

- `BookService.listApprovedBooksWithAvailability()`
  返回所有已批准图书，并按标题排序
- `BookService.searchApprovedBooks(...)`
  按书名或作者搜索，并只保留已批准图书
- `EnhancementHelper.getAvailabilityColor(Book b)`
  在 JavaFX 中实现红黑颜色显示
- `EnhancementHelper.printAvailability(Book b)`
  在控制台中输出可借状态
- `EnhancementHelper.quickReadSummary(Book b)`
  实现摘要快速阅读功能

UI 如何体现：

- JavaFX 使用 `TableView` 显示书单，并在右侧显示所选图书摘要
- Console 版本逐行输出图书信息和推荐结果

### 5.1.4 Task 1.4 Borrow Book

需求概要：

- 只有可借图书才允许借阅
- 借出后图书状态必须更新
- 用户应收到借阅确认

涉及文件按六层对应如下：

- Model：`Book`、`BorrowRecord`
- Repository：`BookRepository`、`BorrowRepository`
- Service：`BorrowService.borrowBook(...)`、`BorrowService.listActiveBorrowsByUser(...)`
- Exception：`BusinessException`、`NotFoundException`、`ValidationException`
- Security：`SecurityConfig.MAX_BORROW_LIMIT`、`SecurityConfig.DEFAULT_BORROW_DAYS`
- UI：`LibraryManagementUI.handleBorrow()`、`ConsoleUI.borrow(Scanner sc)`、`EnhancementHelper`

函数细节说明：

- `BorrowService.borrowBook(String username, String bookId, int borrowDays)`
  查找目标图书
  检查图书是否存在且已批准
  检查图书是否可借
  检查用户是否超出借阅上限
  检查借阅天数是否合法
  创建 `BorrowRecord`
  保存借阅记录
  将图书标记为不可借
- `EnhancementHelper.buildBorrowConfirmation(...)`
  构建 JavaFX 的确认弹窗内容
- `EnhancementHelper.confirmBorrow(...)`
  在控制台中输出借阅确认信息
- `EnhancementHelper.printBorrowResult(...)`
  输出借阅成功结果

UI 如何体现：

- JavaFX 在真正借书前会弹出确认框
- Console 版本通过 `Y/N` 确认是否继续借阅

## 5.2 Task 2 Author Portal

Task 2 包含三个主要子任务：

1. Author Registration
2. Author Login
3. Publish New Book

### 5.2.1 Task 2.1 Author Registration

需求概要：

- 用户输入用户名、全名、密码和可选 bio
- 用户名必须唯一
- 密码必须符合校验规则
- 系统提供注册反馈

涉及文件按六层对应如下：

- Model：`User`、`Role`、`AuthorProfile2`
- Repository：`UserRepository`、`AuthorProfileRepository2`
- Service：`AuthorService2.registerAuthor(...)`
- Exception：`ValidationException`
- Security：`PasswordHasher`、`PasswordPolicy`、`SessionManager`
- UI：`LibraryManagementUI.buildAuthorTab()`、`AuthorConsoleUI2.register(Scanner sc)`

函数细节说明：

- `AuthorService2.registerAuthor(...)`
  校验用户名与全名
  校验密码强度
  检查用户名是否重复
  对密码做哈希
  创建 AUTHOR 用户
  创建并保存 `AuthorProfile2`

### 5.2.2 Task 2.2 Author Login

需求概要：

- 作者输入用户名和密码
- 系统验证账号信息
- 系统确认该账号角色为 AUTHOR

涉及文件按六层对应如下：

- Model：`User`、`Role`
- Repository：`UserRepository`
- Service：`AuthorService2.loginAuthor(...)`
- Exception：`AuthenticationException`
- Security：`PasswordHasher`、`SessionManager`
- UI：`LibraryManagementUI.buildAuthorTab()`、`AuthorConsoleUI2.login(Scanner sc)`

函数细节说明：

- `AuthorService2.loginAuthor(...)`
  按用户名查找用户
  确认角色必须是 AUTHOR
  验证密码哈希
  创建登录会话

### 5.2.3 Task 2.3 Publish New Book

需求概要：

- 作者提交书名、作者名、类型、描述和文件
- 投稿会发送给馆员审核
- 提交成功后需要反馈给作者

涉及文件按六层对应如下：

- Model：`BookSubmission2`、`BookDraft2`、`User`
- Repository：`BookSubmissionRepository2`、`BookDraftRepository2`、`UserRepository`
- Service：`AuthorService2.publishBook(...)`、`AuthorService2.previewBook(...)`、`AuthorService2.getSupportedGenres()`、`AuthorDraftService.autoSave(...)`、`AuthorDraftService.loadDraft(...)`、`AuthorDraftService.loadDrafts(...)`、`AuthorDraftService.clearDraft(...)`、`FileService.validateSubmissionFile(...)`
- Exception：`ValidationException`、`AuthenticationException`
- Security：`SecurityConfig.ALLOWED_EXTENSIONS`、`SecurityConfig.MAX_FILE_SIZE_BYTES`
- UI：`LibraryManagementUI.buildAuthorTab()`、`AuthorConsoleUI2.publish(Scanner sc)`、`AuthorConsoleUI2.loadDraft(Scanner sc)`

函数细节说明：

- `AuthorService2.publishBook(...)`
  校验标题、类型、描述和文件名
  校验类型是否在支持列表中
  校验文件格式是否合法
  验证提交者必须是 AUTHOR
  创建并保存状态为 `PENDING` 的 `BookSubmission2`
- `AuthorService2.previewBook(...)`
  在正式提交前生成预览文本
- `AuthorService2.getSupportedGenres()`
  提供预定义类型列表
- `AuthorDraftService.autoSave(...)`
  自动保存未完成表单内容
- `AuthorDraftService.loadDraft(...)` 与 `loadDrafts(...)`
  支持恢复未完成投稿
- `AuthorDraftService.clearDraft(...)`
  成功提交后清除草稿
- `FileService.validateSubmissionFile(...)`
  校验文件存在、格式和大小是否合法

UI 如何体现：

- JavaFX 将保存草稿、预览、最终提交整合在同一界面中
- Console 版本也支持草稿、预览和最终提交

## 5.3 Task 3 Librarian Portal

Task 3 包含三个主要子任务：

1. Librarian Registration
2. Librarian Login
3. Librarian New Books Approval Screen and Functionalities

### 5.3.1 Task 3.1 Librarian Registration

需求概要：

- 用户输入用户名、全名、密码和可选 employee id
- 用户名必须唯一
- 密码必须满足校验规则
- 系统提供反馈

涉及文件按六层对应如下：

- Model：`User`、`Role`、`LibrarianProfile3`
- Repository：`UserRepository`、`LibrarianProfileRepository3`
- Service：`LibrarianService3.registerLibrarian(...)`
- Exception：`ValidationException`
- Security：`PasswordHasher`、`PasswordPolicy`
- UI：`LibraryManagementUI.buildLibrarianTab()`、`LibrarianConsoleUI3.register(Scanner sc)`

函数细节说明：

- `LibrarianService3.registerLibrarian(...)`
  校验字段
  校验密码强度
  检查用户名是否重复
  对密码做哈希
  创建 LIBRARIAN 用户
  创建并保存 `LibrarianProfile3`

### 5.3.2 Task 3.2 Librarian Login

需求概要：

- 用户输入用户名和密码
- 系统验证凭据
- 系统检查账号角色必须是 LIBRARIAN

涉及文件按六层对应如下：

- Model：`User`、`Role`
- Repository：`UserRepository`
- Service：`LibrarianService3.loginLibrarian(...)`
- Exception：`AuthenticationException`
- Security：`PasswordHasher`、`SessionManager`
- UI：`LibraryManagementUI.buildLibrarianTab()`、`LibrarianConsoleUI3.login(Scanner sc)`

函数细节说明：

- `LibrarianService3.loginLibrarian(...)`
  按用户名查找用户
  检查角色必须是 LIBRARIAN
  验证密码哈希
  创建会话

### 5.3.3 Task 3.3 Librarian New Books Approval Screen and Functionalities

需求概要：

- 显示待审核投稿列表
- 显示标题、作者用户名、作者全名、类型、提交日期和状态
- 支持批准或拒绝，并在最终执行前确认
- 更新状态并给馆员反馈

涉及文件按六层对应如下：

- Model：`BookSubmission2`、`SubmissionState`、`Book`
- Repository：`BookSubmissionRepository2`、`BookRepository`
- Service：`LibrarianService3.getPendingSubmissions()`、`LibrarianService3.approveSubmission(...)`、`LibrarianService3.rejectSubmission(...)`、`LibrarianService3.bulkApprove(...)`、`LibrarianService3.bulkReject(...)`、`FileService.getPreviewDetails(...)`
- Exception：`ValidationException`、`NotFoundException`
- Security：`SecurityConfig` 中的文件限制与格式限制
- UI：`LibraryManagementUI.buildLibrarianTab()`、`LibrarianConsoleUI3.listPending()`、`approve(Scanner sc)`、`reject(Scanner sc)`、`previewSubmissionFile(Scanner sc)`、`bulkApprove(Scanner sc)`、`bulkReject(Scanner sc)`

函数细节说明：

- `LibrarianService3.getPendingSubmissions()`
  返回所有状态为 `PENDING` 的投稿
- `LibrarianService3.approveSubmission(...)`
  查找目标投稿
  检查是否仍然是待审核状态
  将其标记为批准
  保存更新后的投稿
  根据投稿内容创建新的 `Book`
  将图书标记为已批准且可借
  保存到馆藏目录中
- `LibrarianService3.rejectSubmission(...)`
  查找投稿
  检查其是否仍是待审核状态
  将其标记为拒绝并保存评论
- `LibrarianService3.bulkApprove(...)`
  对多个投稿批量执行批准
- `LibrarianService3.bulkReject(...)`
  对多个投稿批量执行拒绝
- `FileService.getPreviewDetails(...)`
  支持馆员查看文本内容与文件元数据

UI 如何体现：

- JavaFX 使用表格、选择模型、预览区域、评论输入框和操作按钮
- Console 版本通过列表和确认提示完成相同流程

## 6. Phase 1 增强功能

## 6.1 Task 1.1、2.1、3.1 的注册检查

已实现内容：

- 所有用户类型共用唯一用户名检查
- Full Name 非空检查
- 强密码校验，包括大写、小写、数字、特殊字符、不能有空格、长度限制
- 密码哈希存储而不是明文存储

主要函数：

- `AuthService.registerStudentOrStaff(...)`
- `AuthorService2.registerAuthor(...)`
- `LibrarianService3.registerLibrarian(...)`
- `PasswordPolicy.validate(...)`
- `PasswordHasher.hashPassword(...)`

## 6.2 Task 1.2、2.2、3.2 的登录类型检查

已实现内容：

- 不同门户会检查账号角色，避免用户从错误入口登录

主要函数：

- `AuthService.loginStudentOrStaff(...)`
- `AuthorService2.loginAuthor(...)`
- `LibrarianService3.loginLibrarian(...)`

## 6.3 Task 1.3 的增强功能

### 阅读摘要

已实现内容：

- JavaFX 中选中图书后可以直接查看摘要
- 控制台支持快速阅读摘要

主要函数：

- `EnhancementHelper.quickReadSummary(Book b)`
- `LibraryManagementUI.buildStudentStaffTab()`

### 借阅数量限制

已实现内容：

- 系统限制单个用户同时借阅的图书数量

主要函数：

- `BorrowService.borrowBook(...)`
- `SecurityConfig.MAX_BORROW_LIMIT`

### 图书推荐

已实现内容：

- 根据借阅次数统计热门图书并给出推荐

主要函数：

- `RecommendationService.recommendTopPopular(int limit)`

## 6.4 Task 1.4 的增强功能

### 带详细信息的借阅确认

已实现内容：

- JavaFX 弹窗会显示书名、借阅时长、到期日和提醒信息
- Console 版本也会显示确认信息

主要函数：

- `LibraryManagementUI.handleBorrow()`
- `EnhancementHelper.buildBorrowConfirmation(...)`
- `EnhancementHelper.confirmBorrow(...)`

### 红黑颜色区分图书可借状态

已实现内容：

- 可借图书显示黑色
- 不可借图书显示红色

主要函数：

- `EnhancementHelper.getAvailabilityColor(Book b)`
- `LibraryManagementUI.buildStudentStaffTab()`

## 6.5 Task 2.3 的增强功能

### 投稿预览

主要函数：

- `AuthorService2.previewBook(...)`
- `LibraryManagementUI.buildAuthorTab()`
- `AuthorConsoleUI2.publish(Scanner sc)`

### 多类型选择

主要函数：

- `AuthorService2.getSupportedGenres()`
- `LibraryManagementUI.parseGenres(String rawGenres)`

### 自动保存草稿

主要函数：

- `AuthorDraftService.autoSave(...)`
- `AuthorDraftService.loadDraft(...)`
- `AuthorDraftService.loadDrafts(...)`
- `AuthorDraftService.clearDraft(...)`

## 6.6 Task 3.3 的增强功能

### 图书内容预览

主要函数：

- `FileService.getPreviewDetails(...)`
- `LibraryManagementUI.buildLibrarianTab()`
- `LibrarianConsoleUI3.showSubmissionFilePreview(String submissionId)`

### 批量操作

主要函数：

- `LibrarianService3.bulkApprove(...)`
- `LibrarianService3.bulkReject(...)`
- `LibraryManagementUI.buildLibrarianTab()`
- `LibrarianConsoleUI3.bulkApprove(Scanner sc)`
- `LibrarianConsoleUI3.bulkReject(Scanner sc)`

## 7. 以 UI 为核心的说明

UI 是本项目展示层面最重要的部分，因为所有任务最终都要通过界面表现出来并可供测试。

### 7.1 为什么 UI 是项目核心

- 它直接体现每一个 task requirement
- 它把六层架构真正串联起来
- 从助教和评分角度看，UI 最能体现项目是否完整可用

### 7.2 最重要的 UI 类：LibraryManagementUI

`LibraryManagementUI` 是本项目最核心的 JavaFX 集成界面。

它之所以最重要，是因为它同时包含：

- Student/Staff 注册与登录
- 图书可用列表
- 借书动作
- 推荐列表
- 当前借阅列表
- Author 注册与登录
- 作者草稿列表
- 预览与投稿表单
- Librarian 注册与登录
- 待审核投稿界面
- 批准、拒绝、预览、批量操作

最重要的方法包括：

- `createContent()`：创建整个主界面
- `buildStudentStaffTab()`：对应 Task 1
- `buildAuthorTab()`：对应 Task 2
- `buildLibrarianTab()`：对应 Task 3
- 各种 `refresh...()` 方法：保证每次操作后界面数据同步更新
- 其他 helper 方法：集中处理公共 UI 行为

### 7.3 Console UI 的作用

虽然控制台界面没有 JavaFX 界面直观，但它们仍然与任务直接对应：

- `ConsoleUI` 对应 Task 1
- `AuthorConsoleUI2` 对应 Task 2
- `LibrarianConsoleUI3` 对应 Task 3

它们说明本项目的业务逻辑并不依赖某一种具体界面框架，而是可以被多个前端复用。

## 8. 从课程需求到代码的最终映射

### 8.1 Task 1 映射

- Task 1.1 -> `AuthService.registerStudentOrStaff(...)`
- Task 1.2 -> `AuthService.loginStudentOrStaff(...)`
- Task 1.3 -> `BookService.listApprovedBooksWithAvailability()`、`BookService.searchApprovedBooks(...)`
- Task 1.4 -> `BorrowService.borrowBook(...)`

### 8.2 Task 2 映射

- Task 2.1 -> `AuthorService2.registerAuthor(...)`
- Task 2.2 -> `AuthorService2.loginAuthor(...)`
- Task 2.3 -> `AuthorService2.publishBook(...)`、`AuthorService2.previewBook(...)`、`AuthorDraftService`、`FileService.validateSubmissionFile(...)`

### 8.3 Task 3 映射

- Task 3.1 -> `LibrarianService3.registerLibrarian(...)`
- Task 3.2 -> `LibrarianService3.loginLibrarian(...)`
- Task 3.3 -> `LibrarianService3.getPendingSubmissions()`、`approveSubmission(...)`、`rejectSubmission(...)`、`bulkApprove(...)`、`bulkReject(...)`、`FileService.getPreviewDetails(...)`

## 9. 总结

本项目按照 Phase 1 的课程要求，实现了完整的任务结构，并通过分层架构加以组织。

最关键的一点是：

- 一个类在架构上可以属于 Service、Repository 或 UI
- 但这个类中的具体函数又同时属于 Task 1.1、Task 2.3、Task 3.3 这样的课程子任务

所以这个中文版本文档同时保留了两种说明方式：

- 六层架构说明
- 按 Task 1、Task 2、Task 3 的任务说明

从评分角度看，可以最清楚地理解为：

- Task 1 主要由 `AuthService`、`BookService`、`BorrowService`、`RecommendationService`、`ConsoleUI` 和 `LibraryManagementUI` 中的 Student/Staff 界面完成
- Task 2 主要由 `AuthorService2`、`AuthorDraftService`、`FileService`、`AuthorConsoleUI2` 和 `LibraryManagementUI` 中的 Author 界面完成
- Task 3 主要由 `LibrarianService3`、`FileService`、`LibrarianConsoleUI3` 和 `LibraryManagementUI` 中的 Librarian 界面完成

整个项目则由以下六部分共同组成：

- Model
- Repository
- Service
- Exception
- Security
- UI