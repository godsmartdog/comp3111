# 图书管理系统（电子书图书馆系统）- COMP3111项目S2026 Group 21

## 一、需求详细说明

### 项目概述
开发一个电子书图书馆管理系统，支持学生/教职工、作者、图书管理员三类用户，实现图书借阅、电子书发布、审核管理等核心功能。

---

### 任务1：学生/教职工门户

#### 任务1.1 学生/教职工注册
**功能描述**：为学生和教职工提供账号注册界面。

**输入字段**：
- 用户名（必须唯一）- 系统需检查是否已被任何类型用户使用
- 全名（不能为空）
- 密码（需符合强度要求）- 至少8位，包含大小写字母、数字、特殊字符
- 角色（学生/教职工下拉选择）

**处理逻辑**：
- 用户名唯一性校验（跨所有用户类型）
- 密码强度校验
- 密码加密存储（如SHA-256或BCrypt）
- 注册成功/失败提示信息

**输出**：注册成功/失败提示，成功后自动跳转登录页

---

#### 任务1.2 学生/教职工登录
**功能描述**：已注册用户登录系统。

**输入字段**：
- 用户名
- 密码

**处理逻辑**：
- 验证用户名是否存在
- 验证密码是否正确
- 验证用户类型是否匹配（学生不能登录教职工账号）
- 创建用户会话

**输出**：登录成功/失败提示，成功后跳转至可用图书界面

---

#### 任务1.3 可用图书界面
**功能描述**：展示所有可借阅的图书（需是图书管理员已审核通过的）。

**展示信息**：
- 书名
- 作者
- 出版日期（图书管理员审核通过日期）
- 可用状态（可借阅/已借出）
- 图书摘要/简介

**增强功能（Nice to have）**：
- 阅读摘要：点击可弹出窗口显示完整摘要
- 借阅数量限制：每个用户最多同时借阅5本书
- 图书推荐：基于借阅历史、借阅次数、流行度、类型相似度、评分信号或关键词/TF-IDF相似度推荐，不使用LLM推荐
- 图书标记：可用书籍用黑色显示，已借出用红色显示

---

#### 任务1.4 借阅图书
**功能描述**：用户在可用图书界面选择图书进行借阅。

**处理逻辑**：
- 检查图书可用状态（未被借出）
- 检查用户当前借阅数量是否已达上限
- 更新图书状态为"已借出"
- 创建借阅记录（包含借阅日期、应还日期）

**增强功能（Nice to have）**：
- 借阅确认弹窗：显示所选图书、借阅时长、应还日期、借阅数量限制提醒
- 借阅成功确认提示

---

### 任务2：作者门户

#### 任务2.1 作者注册
**功能描述**：为作者提供账号注册界面。

**输入字段**：
- 用户名（必须唯一）- 跨用户类型检查
- 全名（不能为空）
- 密码（强度要求）
- 个人简介（可选）

**处理逻辑**：同学生注册，增加Bio字段存储

---

#### 任务2.2 作者登录
**功能描述**：作者用户登录系统。

**处理逻辑**：同学生登录，需验证用户类型为作者

---

#### 任务2.3 发布新书
**功能描述**：作者提交新书发布请求，待图书管理员审核。

**输入字段**：
- 书名
- 作者名（自动填充注册时的全名）
- 类型/流派
- 简介/摘要
- 图书文件上传（PDF格式推荐）

**处理逻辑**：
- 表单数据验证
- 文件上传保存（限制文件大小、格式）
- 创建待审核记录，状态为"待审核"
- 提交成功提示

**增强功能（Nice to have）**：
- 预览功能：提交前预览图书信息格式
- 多类型选择：可从预定义列表选择多个类型
- 草稿自动保存：填写过程中自动保存进度

---

### 任务3：图书管理员门户

#### 任务3.1 管理员注册
**功能描述**：为图书管理员提供注册界面。

**输入字段**：
- 用户名（唯一）
- 全名
- 密码（强度要求）
- 员工ID（可选）

**处理逻辑**：同其他用户注册，需验证用户类型为管理员

---

#### 任务3.2 管理员登录
**功能描述**：管理员用户登录系统。

**处理逻辑**：同其他登录，需验证用户类型为管理员

---

#### 任务3.3 新书审核界面
**功能描述**：显示所有待审核的新书提交，支持审核操作。

**展示信息**：
- 书名
- 作者用户名
- 作者全名
- 类型
- 提交日期
- 状态（待审核）

**操作功能**：
- 批准：将图书状态更新为"已批准"，图书进入可用图书列表，出版日期设为当前日期
- 拒绝：更新状态为"已拒绝"，可填写拒绝原因（可选）

**增强功能（Nice to have）**：
- 内容预览：可直接预览/下载上传的图书文件
- 批量操作：多选批量批准/拒绝，需二次确认

---

## 二、项目架构设计

基于分层架构，我将整个系统分为6个核心包：

```
com.library
├── LibraryManagementApp.java // JavaFX 主入口文件
├── security/                  // 安全相关包
├── exception/                 // 异常处理包  
├── model/                     // 实体模型包
├── repository/                // 数据访问包
├── service/                   // 业务逻辑包
└── ui/                        // 用户界面包
```

---

## 三、详细类及方法定义

### 3.1 model包 - 实体模型类

#### User.java（用户基类）
```java
public abstract class User {
    protected String username;      // 用户名
    protected String fullName;       // 全名
    protected String passwordHash;   // 密码哈希
    protected UserRole role;         // 用户角色
    protected LocalDateTime createdAt; // 创建时间
    
    // 构造方法
    public User(String username, String fullName, String passwordHash, UserRole role) 
    
    // Getter/Setter方法
    public String getUsername()
    public void setUsername(String username)
    public String getFullName()
    public void setFullName(String fullName)
    public String getPasswordHash()
    public void setPasswordHash(String passwordHash)
    public UserRole getRole()
    public void setRole(UserRole role)
    public LocalDateTime getCreatedAt()
    public void setCreatedAt(LocalDateTime createdAt)
}
```

#### UserRole.java（用户角色枚举）
```java
public enum UserRole {
    STUDENT,    // 学生
    STAFF,      // 教职工
    AUTHOR,     // 作者
    LIBRARIAN   // 图书管理员
}
```

#### StudentStaff.java（学生/教职工类）
```java
public class StudentStaff extends User {
    private int borrowedCount;           // 当前借阅数量
    private List<Integer> borrowedBookIds; // 已借阅图书ID列表
    private List<Integer> borrowHistory;   // 借阅历史
    
    // 构造方法
    public StudentStaff(String username, String fullName, String passwordHash, UserRole role)
    
    // 特有方法
    public boolean canBorrow() // 检查是否可继续借阅（不超过5本）
    public void addBorrowedBook(int bookId)
    public void removeBorrowedBook(int bookId)
    public List<Integer> getBorrowedBookIds()
    public List<Integer> getBorrowHistory()
}
```

#### Author.java（作者类）
```java
public class Author extends User {
    private String bio;                     // 个人简介
    private List<Integer> publishedBookIds;  // 已发布图书ID
    private List<Integer> pendingBookIds;    // 待审核图书ID
    
    // 构造方法
    public Author(String username, String fullName, String passwordHash, String bio)
    
    // 特有方法
    public void addPublishedBook(int bookId)
    public void addPendingBook(int bookId)
    public void removePendingBook(int bookId)
    public String getBio()
    public void setBio(String bio)
}
```

#### Librarian.java（图书管理员类）
```java
public class Librarian extends User {
    private String employeeId;          // 员工ID
    private List<Integer> approvedBookIds; // 已审核图书ID
    private List<Integer> rejectedBookIds; // 已拒绝图书ID
    
    // 构造方法
    public Librarian(String username, String fullName, String passwordHash, String employeeId)
    
    // 特有方法
    public String getEmployeeId()
    public void setEmployeeId(String employeeId)
    public void addApprovedBook(int bookId)
    public void addRejectedBook(int bookId)
}
```

#### Book.java（图书类）
```java
public class Book {
    private int id;                     // 图书ID
    private String title;                // 书名
    private String authorName;            // 作者姓名
    private int authorId;                 // 作者用户ID
    private String genre;                 // 类型
    private String description;            // 简介/摘要
    private String filePath;               // 文件存储路径
    private BookStatus status;             // 状态（待审核/已批准/已拒绝/已借出）
    private LocalDateTime submittedDate;    // 提交日期
    private LocalDateTime publishDate;      // 出版日期（审核通过日期）
    private int borrowedByUserId;           // 当前借阅用户ID（-1表示未借出）
    private LocalDateTime borrowDate;       // 借阅日期
    private LocalDateTime dueDate;          // 应还日期
    private int borrowCount;                 // 借阅次数（用于推荐）
    
    // 构造方法
    public Book(String title, String authorName, int authorId, String genre, 
                String description, String filePath)
    
    // 核心方法
    public boolean isAvailable() // 是否可借阅
    public boolean borrow(int userId) // 借阅操作
    public boolean returnBook() // 归还操作
    public void approve(LocalDateTime publishDate) // 批准
    public void reject() // 拒绝
    
    // Getter/Setter
    public int getId()
    public void setId(int id)
    public String getTitle()
    public String getAuthorName()
    public int getAuthorId()
    public String getGenre()
    public String getDescription()
    public String getFilePath()
    public BookStatus getStatus()
    public LocalDateTime getSubmittedDate()
    public LocalDateTime getPublishDate()
    public int getBorrowedByUserId()
    public LocalDateTime getBorrowDate()
    public LocalDateTime getDueDate()
    public int getBorrowCount()
}
```

#### BookStatus.java（图书状态枚举）
```java
public enum BookStatus {
    PENDING,      // 待审核
    APPROVED,     // 已批准
    REJECTED,     // 已拒绝
    BORROWED      // 已借出
}
```

#### BorrowRecord.java（借阅记录类）
```java
public class BorrowRecord {
    private int id;                     // 记录ID
    private int bookId;                  // 图书ID
    private int userId;                   // 用户ID
    private LocalDateTime borrowDate;      // 借阅日期
    private LocalDateTime dueDate;         // 应还日期
    private LocalDateTime returnDate;      // 实际归还日期
    private boolean isReturned;            // 是否已归还
    
    // 构造方法
    public BorrowRecord(int bookId, int userId, LocalDateTime borrowDate, LocalDateTime dueDate)
    
    // 方法
    public void markReturned(LocalDateTime returnDate)
    public boolean isOverdue() // 检查是否逾期
    
    // Getter/Setter
    public int getId()
    public int getBookId()
    public int getUserId()
    public LocalDateTime getBorrowDate()
    public LocalDateTime getDueDate()
    public LocalDateTime getReturnDate()
    public boolean isReturned()
}
```

---

### 3.2 repository包 - 数据访问层

#### UserRepository.java（用户数据访问）
```java
public class UserRepository {
    // 存储结构
    private Map<String, User> userMap;          // username -> User
    private Map<UserRole, List<User>> roleMap;   // role -> Users
    
    public UserRepository()
    
    // 用户操作
    public boolean addUser(User user) // 添加用户（检查用户名唯一性）
    public User findByUsername(String username)
    public List<User> findByRole(UserRole role)
    public boolean existsByUsername(String username)
    public boolean updateUser(User user)
    public boolean deleteUser(String username)
    
    // 借阅相关
    public List<StudentStaff> getAllBorrowers()
    public StudentStaff findBorrowerById(int userId)
    public boolean updateBorrower(StudentStaff user)
}
```

#### BookRepository.java（图书数据访问）
```java
public class BookRepository {
    // 存储结构
    private Map<Integer, Book> bookMap;            // id -> Book
    private Map<BookStatus, List<Book>> statusMap; // status -> Books
    private Map<Integer, List<Book>> authorMap;    // authorId -> Books
    private int nextId;                             // 下一个可用ID
    
    public BookRepository()
    
    // 图书操作
    public int addBook(Book book) // 添加图书，返回生成的ID
    public Book findById(int id)
    public List<Book> findAll()
    public List<Book> findByStatus(BookStatus status)
    public List<Book> findByAuthor(int authorId)
    public List<Book> findAvailableBooks() // 查找可借阅的图书（APPROVED状态且未被借出）
    public List<Book> searchByTitle(String keyword) // 按标题搜索
    public List<Book> searchByAuthor(String authorName) // 按作者搜索
    public boolean updateBook(Book book)
    public boolean deleteBook(int id)
    
    // 借阅相关
    public boolean borrowBook(int bookId, int userId)
    public boolean returnBook(int bookId)
    public List<Book> findBooksBorrowedByUser(int userId)
    
    // 统计推荐相关
    public List<Book> getMostBorrowedBooks(int limit) // 获取借阅次数最多的图书
    public List<Book> getRecommendationsForUser(int userId, int limit) // 为用户推荐
}
```

#### BorrowRecordRepository.java（借阅记录数据访问）
```java
public class BorrowRecordRepository {
    private Map<Integer, BorrowRecord> recordMap;  // id -> Record
    private Map<Integer, List<BorrowRecord>> userRecordMap; // userId -> Records
    private Map<Integer, List<BorrowRecord>> bookRecordMap; // bookId -> Records
    private int nextId;
    
    public BorrowRecordRepository()
    
    // 记录操作
    public int addRecord(BorrowRecord record)
    public BorrowRecord findById(int id)
    public List<BorrowRecord> findByUser(int userId)
    public List<BorrowRecord> findByBook(int bookId)
    public List<BorrowRecord> findActiveRecords() // 当前借出未还的记录
    public List<BorrowRecord> findOverdueRecords() // 逾期未还记录
    public boolean updateRecord(BorrowRecord record)
    
    // 统计
    public int countActiveBorrowsByUser(int userId) // 用户当前借阅数量
    public boolean hasUserBorrowedBook(int userId, int bookId) // 用户是否曾借过某书
}
```

---

### 3.3 service包 - 业务逻辑层

#### PasswordService.java（密码服务）
```java
public class PasswordService {
    private static final int MIN_LENGTH = 8;
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    
    // 密码强度检查
    public PasswordStrength checkStrength(String password)
    public boolean isValidPassword(String password) // 符合最低要求
    
    // 密码加密
    public String hashPassword(String password) // 使用SHA-256或BCrypt
    
    // 密码验证
    public boolean verifyPassword(String password, String hash)
    
    // 内部枚举
    public enum PasswordStrength {
        WEAK, MEDIUM, STRONG
    }
}
```

#### AuthService.java（认证服务）
```java
public class AuthService {
    private UserRepository userRepo;
    private PasswordService passwordService;
    private User currentUser; // 当前登录用户
    
    public AuthService(UserRepository userRepo, PasswordService passwordService)
    
    // 注册
    public boolean register(String username, String fullName, String password, 
                            UserRole role, String optionalField) // optionalField：Bio/EmployeeID
                            
    // 作者注册重载
    public boolean registerAuthor(String username, String fullName, String password, String bio)
    
    // 管理员注册重载
    public boolean registerLibrarian(String username, String fullName, String password, String employeeId)
    
    // 登录
    public boolean login(String username, String password, UserRole expectedRole)
    
    // 登出
    public void logout()
    
    // 获取当前用户
    public User getCurrentUser()
    public boolean isLoggedIn()
    
    // 检查用户名唯一性
    public boolean isUsernameUnique(String username)
}
```

#### BookService.java（图书服务）
```java
public class BookService {
    private BookRepository bookRepo;
    private UserRepository userRepo;
    private BorrowRecordRepository borrowRecordRepo;
    
    public BookService(BookRepository bookRepo, UserRepository userRepo, 
                      BorrowRecordRepository borrowRecordRepo)
    
    // 学生/教职工相关
    public List<Book> getAvailableBooks() // 获取可借阅图书
    public Book getBookDetails(int bookId)
    public String getBookSummary(int bookId) // 获取摘要
    
    // 借阅相关
    public boolean borrowBook(int bookId, int userId) // 检查并执行借阅
    public boolean returnBook(int bookId, int userId)
    public List<Book> getBooksBorrowedByUser(int userId)
    public int getBorrowCountForUser(int userId) // 当前借阅数量
    
    // 作者相关
    public boolean submitBook(String title, String authorName, int authorId, 
                              String genre, String description, String filePath)
    public List<Book> getBooksByAuthor(int authorId)
    public List<Book> getPendingBooksByAuthor(int authorId)
    
    // 管理员相关
    public List<Book> getPendingBooks() // 获取待审核图书
    public boolean approveBook(int bookId, int librarianId) // 批准
    public boolean rejectBook(int bookId, int librarianId) // 拒绝
    
    // 搜索
    public List<Book> searchBooks(String keyword) // 按标题/作者搜索
    
    // 推荐（Nice to have）
    public List<Book> getRecommendedBooksForUser(int userId, int limit)
    public List<Book> getPopularBooks(int limit)
}
```

#### RecommendationService.java（推荐服务 - Nice to have）
```java
public class RecommendationService {
    private BookRepository bookRepo;
    private BorrowRecordRepository borrowRecordRepo;
    
    public RecommendationService(BookRepository bookRepo, BorrowRecordRepository borrowRecordRepo)
    
    // 基于借阅次数的推荐
    public List<Book> recommendByPopularity(int limit)
    
    // 基于用户借阅历史的推荐
    public List<Book> recommendByUserHistory(int userId, int limit)
    
    // 基于类型的推荐
    public List<Book> recommendByGenre(String genre, int excludeBookId, int limit)
    
    // 非LLM推荐：可基于借阅次数、流行度、类型相似度、评分信号或关键词/TF-IDF相似度
    public List<Book> recommendByDeterministicSignals(int userId, int limit)
}
```

#### FileService.java（文件服务）
```java
public class FileService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".pdf", ".txt", ".doc", ".docx");
    private String uploadDirectory;
    
    public FileService(String uploadDirectory)
    
    // 文件保存
    public String saveFile(InputStream fileStream, String originalFileName, String username)
        throws FileTooLargeException, InvalidFileTypeException
        
    // 文件读取
    public InputStream loadFile(String filePath) throws FileNotFoundException
    
    // 文件删除
    public boolean deleteFile(String filePath)
    
    // 文件预览（Nice to have）
    public String getFilePreview(String filePath) // 返回可预览的内容
}
```

---

### 3.4 ui包 - 用户界面层

#### MainUI.java（主界面）
```java
public class MainUI {
    private AuthService authService;
    private Scanner scanner;
    
    public MainUI(AuthService authService)
    
    public void start() // 程序入口
    private void showWelcomeScreen() // 欢迎界面
    private void handleLoginChoice() // 处理登录选择
    private void handleRegisterChoice() // 处理注册选择
}
```

#### StudentStaffUI.java（学生/教职工界面）
```java
public class StudentStaffUI {
    private AuthService authService;
    private BookService bookService;
    private StudentStaff currentUser;
    private Scanner scanner;
    
    public StudentStaffUI(AuthService authService, BookService bookService)
    
    public void showMenu() // 显示主菜单
    private void showAvailableBooks() // 显示可用图书
    private void showBookDetails(int bookId) // 显示图书详情
    private void borrowBook(int bookId) // 借阅图书
    private void showBorrowedBooks() // 显示已借阅图书
    private void showRecommendations() // 显示推荐图书（Nice to have）
    private void searchBooks() // 搜索图书
}
```

#### AuthorUI.java（作者界面）
```java
public class AuthorUI {
    private AuthService authService;
    private BookService bookService;
    private FileService fileService;
    private Author currentUser;
    private Scanner scanner;
    
    public AuthorUI(AuthService authService, BookService bookService, FileService fileService)
    
    public void showMenu() // 显示主菜单
    private void publishNewBook() // 发布新书
    private void showMyBooks() // 查看我的图书
    private void showPendingBooks() // 查看待审核图书
    private void showPublishedBooks() // 查看已批准图书
    private String selectGenre() // 选择类型（多选支持）
    private void autoSaveDraft() // 草稿自动保存（Nice to have）
}
```

#### LibrarianUI.java（管理员界面）
```java
public class LibrarianUI {
    private AuthService authService;
    private BookService bookService;
    private FileService fileService;
    private Librarian currentUser;
    private Scanner scanner;
    
    public LibrarianUI(AuthService authService, BookService bookService, FileService fileService)
    
    public void showMenu() // 显示主菜单
    private void showPendingBooks() // 显示待审核图书
    private void reviewBook(int bookId) // 审核单本图书
    private void approveBook(int bookId) // 批准
    private void rejectBook(int bookId) // 拒绝
    private void bulkApprove() // 批量批准（Nice to have）
    private void previewBookFile(int bookId) // 预览图书文件（Nice to have）
}
```

#### RegisterUI.java（注册界面）
```java
public class RegisterUI {
    private AuthService authService;
    private PasswordService passwordService;
    private Scanner scanner;
    
    public RegisterUI(AuthService authService, PasswordService passwordService)
    
    public void showStudentStaffRegister() // 学生/教职工注册
    public void showAuthorRegister() // 作者注册
    public void showLibrarianRegister() // 管理员注册
    
    private String inputUsername()
    private String inputFullName()
    private String inputPassword()
    private String inputBio() // 作者专用
    private String inputEmployeeId() // 管理员专用
    private void showRegistrationResult(boolean success, String message)
}
```

#### LoginUI.java（登录界面）
```java
public class LoginUI {
    private AuthService authService;
    private Scanner scanner;
    
    public LoginUI(AuthService authService)
    
    public boolean showStudentStaffLogin() // 学生/教职工登录
    public boolean showAuthorLogin() // 作者登录
    public boolean showLibrarianLogin() // 管理员登录
    
    private String inputUsername()
    private String inputPassword()
    private void showLoginResult(boolean success, String message)
}
```

---

### 3.5 exception包 - 异常处理

#### BaseException.java（异常基类）
```java
public abstract class BaseException extends Exception {
    private String errorCode;
    
    public BaseException(String message, String errorCode)
    public String getErrorCode()
}
```

#### UsernameExistsException.java（用户名已存在）
```java
public class UsernameExistsException extends BaseException {
    public UsernameExistsException(String username)
}
```

#### WeakPasswordException.java（密码强度不足）
```java
public class WeakPasswordException extends BaseException {
    public WeakPasswordException(String reason)
}
```

#### InvalidCredentialsException.java（登录凭证错误）
```java
public class InvalidCredentialsException extends BaseException {
    public InvalidCredentialsException()
}
```

#### BookNotAvailableException.java（图书不可借阅）
```java
public class BookNotAvailableException extends BaseException {
    public BookNotAvailableException(int bookId)
}
```

#### BorrowLimitExceededException.java（借阅数量超限）
```java
public class BorrowLimitExceededException extends BaseException {
    public BorrowLimitExceededException(int currentCount, int limit)
}
```

#### FileTooLargeException.java（文件过大）
```java
public class FileTooLargeException extends BaseException {
    public FileTooLargeException(long size, long maxSize)
}
```

#### InvalidFileTypeException.java（文件类型不支持）
```java
public class InvalidFileTypeException extends BaseException {
    public InvalidFileTypeException(String fileType)
}
```

---

### 3.6 security包 - 安全相关

#### SessionManager.java（会话管理）
```java
public class SessionManager {
    private static SessionManager instance;
    private User currentUser;
    private LocalDateTime loginTime;
    private Map<String, Object> sessionData;
    
    private SessionManager()
    public static SessionManager getInstance()
    
    public void createSession(User user)
    public void destroySession()
    public User getCurrentUser()
    public boolean isAuthenticated()
    public void setAttribute(String key, Object value)
    public Object getAttribute(String key)
    public LocalDateTime getLoginTime()
}
```

#### SecurityConfig.java（安全配置）
```java
public class SecurityConfig {
    // 密码策略
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final boolean REQUIRE_UPPERCASE = true;
    public static final boolean REQUIRE_LOWERCASE = true;
    public static final boolean REQUIRE_DIGIT = true;
    public static final boolean REQUIRE_SPECIAL = true;
    public static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    
    // 借阅限制
    public static final int MAX_BORROW_LIMIT = 5;
    public static final int BORROW_DAYS = 14; // 借阅天数
    
    // 文件上传限制
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final List<String> ALLOWED_EXTENSIONS = 
        Arrays.asList(".pdf", ".txt", ".doc", ".docx");
    
    // 加密算法
    public static final String HASH_ALGORITHM = "SHA-256";
}
```

---

### 3.7 LibraryManagementApp.java（JavaFX 主入口）

```java
public class Main {
    public static void main(String[] args) {
        // 初始化各组件
        initializeApplication();
        
        // 启动主界面
        MainUI mainUI = createMainUI();
        mainUI.start();
    }
    
    private static void initializeApplication() {
        // 创建存储目录
        createUploadDirectory();
        
        // 初始化Repository
        UserRepository userRepo = new UserRepository();
        BookRepository bookRepo = new BookRepository();
        BorrowRecordRepository recordRepo = new BorrowRecordRepository();
        
        // 初始化Service
        PasswordService passwordService = new PasswordService();
        AuthService authService = new AuthService(userRepo, passwordService);
        BookService bookService = new BookService(bookRepo, userRepo, recordRepo);
        FileService fileService = new FileService("./uploads");
        
        // 存储为单例或静态引用供UI使用
        ApplicationContext.initialize(userRepo, bookRepo, recordRepo, 
                                      authService, bookService, fileService);
    }
    
    private static MainUI createMainUI() {
        return new MainUI(ApplicationContext.getAuthService());
    }
}
```

---

## 四、数据存储设计

### 内存存储方案（Phase 1简化版）

对于Phase 1，可以使用内存存储（如ArrayList/HashMap）实现：

```
数据存储结构：
- UserRepository: HashMap<String, User> 用户名索引
- BookRepository: HashMap<Integer, Book> ID索引 + 状态索引
- BorrowRecordRepository: HashMap<Integer, BorrowRecord> ID索引 + 用户索引
```

### 后续扩展考虑

Phase 2/3可迁移至数据库（如MySQL）：
- users表（用户信息）
- books表（图书信息）
- borrow_records表（借阅记录）
- book_requests表（图书发布请求）

---

## 五、核心业务流程

### 1. 图书发布审核流程
作者提交新书 → 状态PENDING → 管理员审核 → APPROVED/REJECTED → 若批准，图书进入可用列表

### 2. 图书借阅流程
用户查看可用图书 → 选择图书 → 检查借阅数量限制 → 检查图书状态 → 创建借阅记录 → 更新图书状态

### 3. 用户注册流程
输入信息 → 用户名唯一性校验 → 密码强度校验 → 加密存储 → 创建用户

---

## 六、项目文件结构

```
LibraryManagementSystem/
├── src/
│   └── com/
│       └── library/
│           ├── Main.java
│           ├── security/
│           │   ├── SessionManager.java
│           │   └── SecurityConfig.java
│           ├── exception/
│           │   ├── BaseException.java
│           │   ├── UsernameExistsException.java
│           │   ├── WeakPasswordException.java
│           │   ├── InvalidCredentialsException.java
│           │   ├── BookNotAvailableException.java
│           │   ├── BorrowLimitExceededException.java
│           │   ├── FileTooLargeException.java
│           │   └── InvalidFileTypeException.java
│           ├── model/
│           │   ├── User.java
│           │   ├── UserRole.java
│           │   ├── StudentStaff.java
│           │   ├── Author.java
│           │   ├── Librarian.java
│           │   ├── Book.java
│           │   ├── BookStatus.java
│           │   └── BorrowRecord.java
│           ├── repository/
│           │   ├── UserRepository.java
│           │   ├── BookRepository.java
│           │   └── BorrowRecordRepository.java
│           ├── service/
│           │   ├── PasswordService.java
│           │   ├── AuthService.java
│           │   ├── BookService.java
│           │   ├── RecommendationService.java
│           │   └── FileService.java
│           └── ui/
│               ├── MainUI.java
│               ├── StudentStaffUI.java
│               ├── AuthorUI.java
│               ├── LibrarianUI.java
│               ├── RegisterUI.java
│               └── LoginUI.java
├── uploads/                    # 图书文件存储目录
├── resources/                   # 配置文件
│   └── application.properties
├── README.md                    # 项目说明
└── pom.xml                      # Maven依赖（如使用）
```
