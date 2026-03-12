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
