package Library.Js;

import Library.Exception.AuthenticationException;
import Library.Exception.BusinessException;
import Library.Exception.ValidationException;
import Library.Model.Book;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.Role;
import Library.Model.User;
import Library.Security.SecurityConfig;
import Library.Service.AuthService;
import Library.Service.AuthorDraftService;
import Library.Service.AuthorService2;
import Library.Service.BookService;
import Library.Service.BorrowService;
import Library.Service.FileService;
import Library.Service.LibrarianService3;
import Library.Service.RecommendationService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// Main UI class that builds the JavaFX interface and connects it to the service layer
public class LibraryManagementUI {
    private final AuthService authService;
    private final BookService bookService;
    private final BorrowService borrowService;
    private final RecommendationService recommendationService;
    private final AuthorService2 authorService;
    private final AuthorDraftService authorDraftService;
    private final FileService fileService;
    private final LibrarianService3 librarianService;

    private User currentStudentStaff;
    private User currentAuthor;
    private User currentLibrarian;

    private final ObservableList<Book> bookResults = FXCollections.observableArrayList();
    private final ObservableList<String> recommendationItems = FXCollections.observableArrayList();
    private final ObservableList<String> activeBorrowItems = FXCollections.observableArrayList();
    private final ObservableList<BookDraft2> draftItems = FXCollections.observableArrayList();
    private final ObservableList<BookSubmission2> pendingSubmissionItems = FXCollections.observableArrayList();

    private BorderPane root;
    private TabPane portalTabs;

    private TableView<Book> bookTable;
    private TextField searchField;
    private Spinner<Integer> borrowDaysSpinner;
    private Button borrowButton;
    private Label studentStatusLabel;
    private TextArea selectedBookSummary;
    private ListView<String> recommendationList;
    private ListView<String> activeBorrowList;

    private Label authorStatusLabel;
    private TextField authorTitleField;
    private ListView<String> authorGenreListView;
    private TextField authorFileField;
    private TextArea authorDescriptionArea;
    private TextArea authorPreviewArea;
    private ListView<BookDraft2> draftListView;

    private Label librarianStatusLabel;
    private TableView<BookSubmission2> pendingSubmissionTable;
    private TextArea librarianPreviewArea;
    private TextField librarianCommentField;

    public LibraryManagementUI(AuthService authService,
                               BookService bookService,
                               BorrowService borrowService,
                               RecommendationService recommendationService,
                               AuthorService2 authorService,
                               AuthorDraftService authorDraftService,
                               FileService fileService,
                               LibrarianService3 librarianService) {
        this.authService = authService;
        this.bookService = bookService;
        this.borrowService = borrowService;
        this.recommendationService = recommendationService;
        this.authorService = authorService;
        this.authorDraftService = authorDraftService;
        this.fileService = fileService;
        this.librarianService = librarianService;
    }

    // Method to create the main content of the UI, called from the `start` method of the application
    public Parent createContent() {
        root = new BorderPane();
        root.setPadding(new Insets(16));

        refreshBookResults();
        refreshRecommendations();
        refreshDrafts();
        refreshPendingSubmissions();
        refreshActiveBorrows();
        showLandingPage();
        return root;
    }

    // Helper method to display the landing page with login and registration options
    private void showLandingPage() {
        root.setTop(null);
        root.setCenter(buildLandingPage());
    }

    // Helper method to build the landing page UI components
    private Parent buildLandingPage() {
        Label heading = new Label("COMP3111 Library Management System");
        heading.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");

        Label subtitle = new Label("Start from the home page, then login or register before entering the main library content.");
        subtitle.setWrapText(true);

        Label demoAccounts = new Label("Demo accounts: student1 / author1 / librarian1, password: Password1!");
        demoAccounts.setWrapText(true);

        TextField loginUsername = new TextField();
        PasswordField loginPassword = new PasswordField();
        ComboBox<Role> loginRole = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        loginRole.getSelectionModel().select(Role.STUDENT);
        Button loginButton = new Button("Login");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(event -> handleAction(() -> {
            User loggedInUser = loginFromLandingPage(
                    loginUsername.getText(),
                    loginPassword.getText(),
                    loginRole.getValue()
            );
            setActiveUser(loggedInUser);
            showMainPortal(loggedInUser.getRole());
            showInfo("Login successful.", "Welcome, " + loggedInUser.getFullName() + ".");
        }));

        // Build the login form using a helper method to reduce boilerplate
        GridPane loginPane = createForm(
                "Login",
                new String[]{"Username", "Password", "Role"},
                loginUsername, loginPassword, loginRole
        );
        loginPane.add(loginButton, 1, 4);

        // Build the registration form with dynamic extra field based on role selection
        TextField registerUsername = new TextField();
        // Note: reusing `registerName` variable name for simplicity, but it serves different purposes based on role (full name for student/staff, bio for author, employee ID for librarian)
        TextField registerName = new TextField();
        // Note: reusing `registerPassword` variable name for simplicity, but it serves the same purpose for all roles
        PasswordField registerPassword = new PasswordField();
        ComboBox<Role> registerRole = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        registerRole.getSelectionModel().select(Role.STUDENT);
        TextField registerExtraField = new TextField();
        Label registerExtraHint = new Label();
        registerExtraHint.setWrapText(true);
        updateLandingRegisterHint(registerRole.getValue(), registerExtraField, registerExtraHint);
        registerRole.valueProperty().addListener((obs, oldRole, newRole) ->
                updateLandingRegisterHint(newRole, registerExtraField, registerExtraHint)
        );

        // Build the registration form using a helper method to reduce boilerplate
        Button registerButton = new Button("Register");
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setOnAction(event -> handleAction(() -> {
            registerFromLandingPage(
                    registerUsername.getText(),
                    registerName.getText(),
                    registerPassword.getText(),
                    registerRole.getValue(),
                    registerExtraField.getText()
            );
            showInfo("Registration successful.", "Account created. Please login to enter the main page.");
            registerUsername.clear();
            registerName.clear();
            registerPassword.clear();
            registerExtraField.clear();
        }));

        // Note: reusing `registerName` variable name for simplicity, but it serves different purposes based on role (full name for student/staff, bio for author, employee ID for librarian)
        GridPane registerPane = createForm(
                "Register",
                new String[]{"Username", "Full Name", "Password", "Account Type", "Bio / Employee ID"},
                registerUsername, registerName, registerPassword, registerRole, registerExtraField
        );
        registerPane.add(registerExtraHint, 1, 6);
        registerPane.add(registerButton, 1, 7);

        // Layout the login and registration forms side by side
        HBox cards = new HBox(18, loginPane, registerPane);
        cards.setAlignment(Pos.TOP_CENTER);

        // Combine everything into a single VBox for the landing page
        VBox page = new VBox(14, heading, subtitle, demoAccounts, cards);
        page.setAlignment(Pos.TOP_CENTER);
        page.setPadding(new Insets(28));
        VBox.setVgrow(cards, Priority.ALWAYS);
        return page;
    }

    // Helper method to create a form layout given labels and input fields, to reduce repetitive code for login and registration forms
    private void showMainPortal(Role preferredRole) {
        if (portalTabs == null) {
            portalTabs = new TabPane(
                    buildStudentStaffTab(),
                    buildAuthorTab(),
                    buildLibrarianTab()
            );
            portalTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        }

        syncPortalStatusLabels();
        refreshBookResults();
        refreshRecommendations();
        refreshDrafts();
        refreshPendingSubmissions();
        refreshActiveBorrows();
        selectPortalTab(preferredRole);

        Label heading = new Label("Library Main Page");
        heading.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        User activeUser = getActiveUser();
        String welcomeText = activeUser == null
                ? "Browse the main portal."
                : "Welcome, " + activeUser.getFullName() + " (" + activeUser.getRole() + ")";
        Label subtitle = new Label(welcomeText);
        subtitle.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(event -> {
            authService.logout();
            clearActiveUsers();
            syncPortalStatusLabels();
            refreshActiveBorrows();
            refreshDrafts();
            showLandingPage();
        });

        HBox topBar = new HBox(12, new VBox(4, heading, subtitle), spacer, logoutButton);
        topBar.setAlignment(Pos.CENTER_LEFT);

        root.setTop(topBar);
        root.setCenter(portalTabs);
    }

    // Helper method to select the appropriate portal tab based on the user's role after login
    private void selectPortalTab(Role role) {
        if (portalTabs == null || role == null) {
            return;
        }

        int tabIndex = switch (role) {
            case STUDENT, STAFF -> 0;
            case AUTHOR -> 1;
            case LIBRARIAN -> 2;
        };
        portalTabs.getSelectionModel().select(tabIndex);
    }

    // Helper method to handle registration logic from the landing page, routing to the appropriate service method based on role
    private void registerFromLandingPage(String username, String fullName, String password, Role role, String extraDetail) {
        switch (role) {
            case STUDENT, STAFF -> authService.registerStudentOrStaff(username, fullName, password, role);
            case AUTHOR -> authorService.registerAuthor(username, fullName, password, extraDetail);
            case LIBRARIAN -> librarianService.registerLibrarian(username, fullName, password, extraDetail);
        }
    }

    // Helper method to handle login logic from the landing page, routing to the appropriate service method based on role and returning the logged-in user
    private User loginFromLandingPage(String username, String password, Role role) {
        return switch (role) {
            case STUDENT, STAFF -> authService.loginStudentOrStaff(username, password, role);
            case AUTHOR -> authorService.loginAuthor(username, password);
            case LIBRARIAN -> librarianService.loginLibrarian(username, password);
        };
    }

    // Helper method to update the registration form's extra field and hint label dynamically based on the selected role, improving user experience by providing contextual guidance
    private void updateLandingRegisterHint(Role role, TextField extraField, Label hintLabel) {
        if (role == null) {
            extraField.clear();
            extraField.setPromptText("");
            hintLabel.setText("");
            return;
        }

        switch (role) {
            case STUDENT, STAFF -> {
                extraField.clear();
                extraField.setPromptText("Optional for this role");
                hintLabel.setText("No extra detail is required for student or staff registration.");
            }
            case AUTHOR -> {
                extraField.setPromptText("Short author bio");
                hintLabel.setText("For authors, this field is used as the bio.");
            }
            case LIBRARIAN -> {
                extraField.setPromptText("Employee ID");
                hintLabel.setText("For librarians, this field is used as the employee ID.");
            }
        }
    }

    // Helper method to clear all active user references upon logout, ensuring that the UI correctly reflects the logged-out state and prevents access to user-specific features
    private void setActiveUser(User user) {
        clearActiveUsers();
        if (user == null) {
            syncPortalStatusLabels();
            return;
        }

        switch (user.getRole()) {
            case STUDENT, STAFF -> currentStudentStaff = user;
            case AUTHOR -> currentAuthor = user;
            case LIBRARIAN -> currentLibrarian = user;
        }
        syncPortalStatusLabels();
    }

    // Helper method to clear all active user references upon logout, ensuring that the UI correctly reflects the logged-out state and prevents access to user-specific features
    private void clearActiveUsers() {
        currentStudentStaff = null;
        currentAuthor = null;
        currentLibrarian = null;
    }

    // Helper method to get the currently active user, which can be a student/staff, author, or librarian, used for displaying personalized information and controlling access to features based on the logged-in user
    private User getActiveUser() {
        if (currentStudentStaff != null) {
            return currentStudentStaff;
        }
        if (currentAuthor != null) {
            return currentAuthor;
        }
        return currentLibrarian;
    }

    // Helper method to synchronize the status labels in each portal tab based on the currently active user, ensuring that the UI consistently reflects the logged-in state across all tabs and provides clear feedback to the user about their login status
    private void syncPortalStatusLabels() {
        if (studentStatusLabel != null) {
            studentStatusLabel.setText(currentStudentStaff == null
                    ? "Not logged in."
                    : "Logged in as " + currentStudentStaff.getFullName() + " (" + currentStudentStaff.getRole() + ")");
        }
        if (authorStatusLabel != null) {
            authorStatusLabel.setText(currentAuthor == null
                    ? "Not logged in."
                    : "Logged in as " + currentAuthor.getFullName());
        }
        if (librarianStatusLabel != null) {
            librarianStatusLabel.setText(currentLibrarian == null
                    ? "Not logged in."
                    : "Logged in as " + currentLibrarian.getFullName());
        }
    }

    // Helper method to build the student/staff portal tab, which includes login status, book search and results, book summary display, borrowing functionality, recommendations, and active borrows, providing a comprehensive interface for students and staff to interact with the library system
    private Tab buildStudentStaffTab() {
        studentStatusLabel = new Label("Not logged in.");

        // Note: reusing `registerUsername`, `registerName`, and `registerPassword` variable names for simplicity, but they serve different purposes in this context (login form instead of registration form)
        TextField registerUsername = new TextField();
        TextField registerName = new TextField();
        PasswordField registerPassword = new PasswordField();
        ComboBox<Role> registerRole = new ComboBox<>(FXCollections.observableArrayList(Role.STUDENT, Role.STAFF));
        registerRole.getSelectionModel().select(Role.STUDENT);
        Button registerButton = new Button("Register");
        registerButton.setOnAction(event -> handleAction(() -> {
            authService.registerStudentOrStaff(
                    registerUsername.getText(),
                    registerName.getText(),
                    registerPassword.getText(),
                    registerRole.getValue()
            );
            showInfo("Registration successful.", "Student/Staff account created successfully.");
            registerUsername.clear();
            registerName.clear();
            registerPassword.clear();
        }));

        // Build the registration form using a helper method to reduce boilerplate
        GridPane registerPane = createForm(
                "Student/Staff Registration",
                new String[]{"Username", "Full Name", "Password", "Role"},
                registerUsername, registerName, registerPassword, registerRole
        );
        registerPane.add(registerButton, 1, 5);

        // Login form is built in the landing page for better user experience, but we can also provide it here for convenience
        TextField loginUsername = new TextField();
        PasswordField loginPassword = new PasswordField();
        ComboBox<Role> loginRole = new ComboBox<>(FXCollections.observableArrayList(Role.STUDENT, Role.STAFF));
        loginRole.getSelectionModel().select(Role.STUDENT);
        Button loginButton = new Button("Login");
        loginButton.setOnAction(event -> handleAction(() -> {
            currentStudentStaff = authService.loginStudentOrStaff(
                    loginUsername.getText(),
                    loginPassword.getText(),
                    loginRole.getValue()
            );
            studentStatusLabel.setText("Logged in as " + currentStudentStaff.getFullName() + " (" + currentStudentStaff.getRole() + ")");
            refreshActiveBorrows();
            updateBorrowButtonState();
            showInfo("Login successful.", "Student/Staff portal is ready.");
        }));

        // Build the login form using a helper method to reduce boilerplate
        GridPane loginPane = createForm(
                "Student/Staff Login",
                new String[]{"Username", "Password", "Role"},
                loginUsername, loginPassword, loginRole
        );
        loginPane.add(loginButton, 1, 4);

        // Layout the login and registration forms side by side
        VBox left = new VBox(12, studentStatusLabel, registerPane, loginPane);
        left.setPadding(new Insets(0, 12, 0, 0));

        searchField = new TextField();
        searchField.setPromptText("Search by title or author");
        Button searchButton = new Button("Search");
        searchButton.setOnAction(event -> refreshBookResults());
        Button showAllButton = new Button("Show All");
        showAllButton.setOnAction(event -> {
            searchField.clear();
            refreshBookResults();
        });

        // Layout the search bar with the search field and buttons, providing an intuitive interface for users to search for books by title or author, and to easily reset the search to show all books
        HBox searchBar = new HBox(8, new Label("Search"), searchField, searchButton, showAllButton);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        bookTable = new TableView<>(bookResults);
        bookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        bookTable.getSelectionModel().selectedItemProperty().addListener((obs, oldBook, newBook) -> {
            if (newBook == null) {
                selectedBookSummary.clear();
            } else {
                selectedBookSummary.setText(newBook.getSummary() == null ? "" : newBook.getSummary());
            }
            updateBorrowButtonState();
        });

        TableColumn<Book, String> idColumn = new TableColumn<>("Book ID");
        idColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getId()));

        TableColumn<Book, String> titleColumn = new TableColumn<>("Title");
        titleColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getTitle()));
        titleColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setTextFill(null);
                    setTooltip(null);
                    return;
                }
                Book book = getTableView().getItems().get(getIndex());
                setText(item);
                setTextFill(EnhancementHelper.getAvailabilityColor(book));
                setTooltip(new Tooltip(book.isAvailable() ? "Available to borrow" : "Unavailable to borrow"));
            }
        });

        TableColumn<Book, String> authorColumn = new TableColumn<>("Author");
        authorColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getAuthorFullName()));

        TableColumn<Book, String> dateColumn = new TableColumn<>("Publish Date");
        dateColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(String.valueOf(data.getValue().getPublishDate())));

        TableColumn<Book, String> statusColumn = new TableColumn<>("Availability");
        statusColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().isAvailable() ? "Available" : "Unavailable"));

        bookTable.getColumns().addAll(idColumn, titleColumn, authorColumn, dateColumn, statusColumn);

        selectedBookSummary = new TextArea();
        selectedBookSummary.setEditable(false);
        selectedBookSummary.setWrapText(true);
        selectedBookSummary.setPromptText("Select a book to view its summary.");
        selectedBookSummary.setPrefRowCount(5);

        borrowDaysSpinner = new Spinner<>(1, SecurityConfig.MAX_BORROW_DAYS, SecurityConfig.DEFAULT_BORROW_DAYS);
        borrowDaysSpinner.setEditable(true);
        borrowButton = new Button("Borrow Selected Book");
        borrowButton.setOnAction(event -> handleBorrow());
        updateBorrowButtonState();

        HBox borrowBar = new HBox(8, new Label("Borrow Days"), borrowDaysSpinner, borrowButton);
        borrowBar.setAlignment(Pos.CENTER_LEFT);

        recommendationList = new ListView<>(recommendationItems);
        recommendationList.setPrefHeight(120);

        activeBorrowList = new ListView<>(activeBorrowItems);
        activeBorrowList.setPrefHeight(120);

        VBox right = new VBox(
                10,
                searchBar,
                bookTable,
                new Label("Selected Book Summary"),
                selectedBookSummary,
                borrowBar,
                new Label("Popular Recommendations"),
                recommendationList,
                new Label("My Active Borrows"),
                activeBorrowList
        );
        VBox.setVgrow(bookTable, Priority.ALWAYS);
        right.setPadding(new Insets(0, 0, 0, 12));

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.33);

        Tab tab = new Tab("Student / Staff", splitPane);
        tab.setClosable(false);
        return tab;
    }

    // Helper method to build the author portal tab, which includes login status, author registration and login forms, draft management with auto-saving, book submission form with file upload and genre selection, and a preview area for the submission, providing a comprehensive interface for authors to manage their book submissions and drafts
    private Tab buildAuthorTab() {
        authorStatusLabel = new Label("Not logged in.");

        TextField registerUsername = new TextField();
        TextField registerName = new TextField();
    PasswordField registerPassword = new PasswordField();
        TextField registerBio = new TextField();
        Button registerButton = new Button("Register Author");
        registerButton.setOnAction(event -> handleAction(() -> {
            authorService.registerAuthor(
                    registerUsername.getText(),
                    registerName.getText(),
                    registerPassword.getText(),
                    registerBio.getText()
            );
            showInfo("Registration successful.", "Author account created successfully.");
            registerUsername.clear();
            registerName.clear();
            registerPassword.clear();
            registerBio.clear();
        }));

        GridPane registerPane = createForm(
                "Author Registration",
                new String[]{"Username", "Full Name", "Password", "Bio"},
                registerUsername, registerName, registerPassword, registerBio
        );
        registerPane.add(registerButton, 1, 5);

        TextField loginUsername = new TextField();
        PasswordField loginPassword = new PasswordField();
        Button loginButton = new Button("Login Author");
        loginButton.setOnAction(event -> handleAction(() -> {
            currentAuthor = authorService.loginAuthor(loginUsername.getText(), loginPassword.getText());
            authorStatusLabel.setText("Logged in as " + currentAuthor.getFullName());
            clearAuthorForm();
            refreshDrafts();
            showInfo("Login successful.", "Author portal is ready.");
        }));

        GridPane loginPane = createForm(
                "Author Login",
                new String[]{"Username", "Password"},
                loginUsername, loginPassword
        );
        loginPane.add(loginButton, 1, 3);

        draftListView = new ListView<>(draftItems);
        draftListView.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(BookDraft2 item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getTitle() + " (saved " + item.getLastSavedAt() + ")");
            }
        });
        draftListView.getSelectionModel().selectedItemProperty().addListener((obs, oldDraft, draft) -> populateDraft(draft));
        Button loadDraftButton = new Button("Load Selected Draft");
        loadDraftButton.setOnAction(event -> populateDraft(draftListView.getSelectionModel().getSelectedItem()));

        VBox left = new VBox(12, authorStatusLabel, registerPane, loginPane, new Label("Saved Drafts"), draftListView, loadDraftButton);
        VBox.setVgrow(draftListView, Priority.ALWAYS);
        left.setPadding(new Insets(0, 12, 0, 0));

        authorTitleField = new TextField();
    authorGenreListView = new ListView<>(FXCollections.observableArrayList(authorService.getSupportedGenres()));
    authorGenreListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    authorGenreListView.setPrefHeight(140);
    authorGenreListView.setMaxWidth(Double.MAX_VALUE);
    authorGenreListView.setTooltip(new Tooltip("Hold Ctrl to select multiple genres."));
        authorFileField = new TextField();
    Button browseAuthorFileButton = new Button("Browse...");
    browseAuthorFileButton.setOnAction(event -> chooseAuthorFile());
    HBox authorFileBox = new HBox(8, authorFileField, browseAuthorFileButton);
    HBox.setHgrow(authorFileField, Priority.ALWAYS);
        authorDescriptionArea = new TextArea();
        authorDescriptionArea.setWrapText(true);
        authorDescriptionArea.setPrefRowCount(8);
        authorPreviewArea = new TextArea();
        authorPreviewArea.setEditable(false);
        authorPreviewArea.setWrapText(true);

        // Helper method to save the current draft automatically
        Button saveDraftButton = new Button("Auto-Save Draft");
        saveDraftButton.setOnAction(event -> handleAction(() -> {
            ensureAuthorLogin();
            authorDraftService.autoSave(
                    currentAuthor.getUsername(),
                    authorTitleField.getText(),
                selectedAuthorGenres(),
                    authorDescriptionArea.getText(),
                    authorFileField.getText()
            );
            refreshDrafts();
            showInfo("Draft saved.", "The draft was saved successfully.");
        }));

        Button previewButton = new Button("Preview Submission");
        previewButton.setOnAction(event -> handleAction(() ->
                authorPreviewArea.setText(authorService.previewBook(
                        authorTitleField.getText(),
                selectedAuthorGenres(),
                        authorDescriptionArea.getText()
                ))
        ));

        // Helper method to get the list of selected genres for the author submission form, which is used when saving drafts and publishing submissions, ensuring that the selected genres are correctly captured and processed by the service layer
        Button publishButton = new Button("Submit for Review");
        publishButton.setOnAction(event -> handleAction(() -> {
            ensureAuthorLogin();
            fileService.validateSubmissionFile(authorFileField.getText());
            BookSubmission2 submission = authorService.publishBook(
                    currentAuthor.getUsername(),
                    authorTitleField.getText(),
                    selectedAuthorGenres(),
                    authorDescriptionArea.getText(),
                    authorFileField.getText()
            );
            authorDraftService.clearDraft(currentAuthor.getUsername(), authorTitleField.getText());
            refreshDrafts();
            refreshPendingSubmissions();
            clearAuthorForm();
            showInfo("Submission sent.", "Submission ID: " + submission.getId());
        }));

        GridPane form = createForm(
                "Author Submission",
        new String[]{"Title", "File Path"},
        authorTitleField, authorFileBox
        );
        form.add(new Label("Genres"), 0, 3);
        form.add(authorGenreListView, 1, 3);
        form.add(new Label("Description"), 0, 4);
        form.add(authorDescriptionArea, 1, 4);
        form.add(new HBox(8, saveDraftButton, previewButton, publishButton), 1, 5);

        VBox right = new VBox(10, form, new Label("Preview"), authorPreviewArea);
        VBox.setVgrow(authorPreviewArea, Priority.ALWAYS);
        right.setPadding(new Insets(0, 0, 0, 12));

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.33);

        Tab tab = new Tab("Author", splitPane);
        tab.setClosable(false);
        return tab;
    }

    // Helper method to get the list of selected genres for the author submission form, which is used when saving drafts and publishing submissions, ensuring that the selected genres are correctly captured and processed by the service layer
    private Tab buildLibrarianTab() {
        librarianStatusLabel = new Label("Not logged in.");

        TextField registerUsername = new TextField();
        TextField registerName = new TextField();
        PasswordField registerPassword = new PasswordField();
        TextField registerEmployeeId = new TextField();
        Button registerButton = new Button("Register Librarian");
        registerButton.setOnAction(event -> handleAction(() -> {
            librarianService.registerLibrarian(
                    registerUsername.getText(),
                    registerName.getText(),
                    registerPassword.getText(),
                    registerEmployeeId.getText()
            );
            showInfo("Registration successful.", "Librarian account created successfully.");
            registerUsername.clear();
            registerName.clear();
            registerPassword.clear();
            registerEmployeeId.clear();
        }));

        GridPane registerPane = createForm(
                "Librarian Registration",
                new String[]{"Username", "Full Name", "Password", "Employee ID"},
                registerUsername, registerName, registerPassword, registerEmployeeId
        );
        registerPane.add(registerButton, 1, 5);

        TextField loginUsername = new TextField();
        PasswordField loginPassword = new PasswordField();
        Button loginButton = new Button("Login Librarian");
        loginButton.setOnAction(event -> handleAction(() -> {
            currentLibrarian = librarianService.loginLibrarian(loginUsername.getText(), loginPassword.getText());
            librarianStatusLabel.setText("Logged in as " + currentLibrarian.getFullName());
            librarianPreviewArea.clear();
            librarianCommentField.clear();
            refreshPendingSubmissions();
            showInfo("Login successful.", "Librarian portal is ready.");
        }));

        GridPane loginPane = createForm(
                "Librarian Login",
                new String[]{"Username", "Password"},
                loginUsername, loginPassword
        );
        loginPane.add(loginButton, 1, 3);

        VBox left = new VBox(12, librarianStatusLabel, registerPane, loginPane);
        left.setPadding(new Insets(0, 12, 0, 0));

        pendingSubmissionTable = new TableView<>(pendingSubmissionItems);
        pendingSubmissionTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        pendingSubmissionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<BookSubmission2, String> idColumn = new TableColumn<>("Submission ID");
        idColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getId()));

        TableColumn<BookSubmission2, String> titleColumn = new TableColumn<>("Title");
        titleColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getTitle()));

        TableColumn<BookSubmission2, String> authorColumn = new TableColumn<>("Author");
        authorColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getAuthorFullName()));

        TableColumn<BookSubmission2, String> genreColumn = new TableColumn<>("Genres");
        genreColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(String.join(", ", data.getValue().getGenres())));

        TableColumn<BookSubmission2, String> fileColumn = new TableColumn<>("File");
        fileColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getFileName()));

        pendingSubmissionTable.getColumns().addAll(idColumn, titleColumn, authorColumn, genreColumn, fileColumn);
        pendingSubmissionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSubmission, submission) -> {
            if (submission != null) {
                try {
                    librarianPreviewArea.setText(fileService.getPreviewDetails(submission.getFileName()));
                } catch (ValidationException e) {
                    librarianPreviewArea.setText(e.getMessage());
                }
            } else {
                librarianPreviewArea.clear();
            }
        });

        librarianCommentField = new TextField();
        librarianCommentField.setPromptText("Optional librarian comment");
        librarianPreviewArea = new TextArea();
        librarianPreviewArea.setEditable(false);
        librarianPreviewArea.setWrapText(true);

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(event -> refreshPendingSubmissions());

        Button previewButton = new Button("Preview File");
        previewButton.setOnAction(event -> handleAction(() -> {
            ensureLibrarianLogin();
            BookSubmission2 submission = requireSelectedSubmission();
            librarianPreviewArea.setText(fileService.getPreviewDetails(submission.getFileName()));
        }));

        Button approveButton = new Button("Approve Selected");
        approveButton.setOnAction(event -> handleAction(() -> {
            ensureLibrarianLogin();
            BookSubmission2 submission = requireSelectedSubmission();
            librarianService.approveSubmission(submission.getId(), librarianCommentField.getText());
            afterSubmissionUpdate("Submission approved.");
        }));

        Button rejectButton = new Button("Reject Selected");
        rejectButton.setOnAction(event -> handleAction(() -> {
            ensureLibrarianLogin();
            BookSubmission2 submission = requireSelectedSubmission();
            librarianService.rejectSubmission(submission.getId(), librarianCommentField.getText());
            afterSubmissionUpdate("Submission rejected.");
        }));

        Button bulkApproveButton = new Button("Bulk Approve");
        bulkApproveButton.setOnAction(event -> handleAction(() -> {
            ensureLibrarianLogin();
            List<String> ids = pendingSubmissionTable.getSelectionModel().getSelectedItems().stream()
                    .map(BookSubmission2::getId)
                    .toList();
            if (ids.isEmpty()) {
                throw new ValidationException("Select at least one submission.");
            }
            librarianService.bulkApprove(ids, librarianCommentField.getText());
            afterSubmissionUpdate("Selected submissions approved.");
        }));

        Button bulkRejectButton = new Button("Bulk Reject");
        bulkRejectButton.setOnAction(event -> handleAction(() -> {
            ensureLibrarianLogin();
            List<String> ids = pendingSubmissionTable.getSelectionModel().getSelectedItems().stream()
                    .map(BookSubmission2::getId)
                    .toList();
            if (ids.isEmpty()) {
                throw new ValidationException("Select at least one submission.");
            }
            librarianService.bulkReject(ids, librarianCommentField.getText());
            afterSubmissionUpdate("Selected submissions rejected.");
        }));

        HBox actions = new HBox(8, refreshButton, previewButton, approveButton, rejectButton, bulkApproveButton, bulkRejectButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox right = new VBox(10,
                pendingSubmissionTable,
                new Label("Comment"),
                librarianCommentField,
                actions,
                new Label("Submission File Preview"),
                librarianPreviewArea
        );
        VBox.setVgrow(pendingSubmissionTable, Priority.ALWAYS);
        VBox.setVgrow(librarianPreviewArea, Priority.ALWAYS);
        right.setPadding(new Insets(0, 0, 0, 12));

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.33);

        Tab tab = new Tab("Librarian", splitPane);
        tab.setClosable(false);
        return tab;
    }

    // Helper method to create a form layout given labels and input fields, to reduce repetitive code for login and registration forms, improving code maintainability and readability by abstracting common UI patterns into a reusable method
    private GridPane createForm(String title, String[] labels, javafx.scene.Node... controls) {
        GridPane pane = new GridPane();
        pane.setHgap(8);
        pane.setVgap(8);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-border-color: lightgray; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        pane.add(titleLabel, 0, 0, 2, 1);

        for (int i = 0; i < labels.length; i++) {
            pane.add(new Label(labels[i]), 0, i + 1);
            pane.add(controls[i], 1, i + 1);
            if (controls[i] instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
            }
        }
        return pane;
    }

    // Helper method to handle the borrow action when the user clicks the "Borrow Selected Book" button, including validation of login status, book selection, and availability, as well as confirmation dialog and updating the UI after a successful borrow
    private void handleBorrow() {
        handleAction(() -> {
            if (currentStudentStaff == null) {
                throw new ValidationException("Please login as STUDENT or STAFF before borrowing.");
            }
            Book selectedBook = bookTable.getSelectionModel().getSelectedItem();
            if (selectedBook == null) {
                throw new ValidationException("Select a book first.");
            }
            if (!selectedBook.isAvailable()) {
                throw new BusinessException("Only available books can be borrowed.");
            }

            int durationDays = borrowDaysSpinner.getValue();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Borrow");
            confirm.setHeaderText("Borrow " + selectedBook.getTitle());
            confirm.setContentText(EnhancementHelper.buildBorrowConfirmation(selectedBook.getTitle(), durationDays));
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
                return;
            }

            BorrowRecord record = borrowService.borrowBook(currentStudentStaff.getUsername(), selectedBook.getId(), durationDays);
            showInfo("Borrow successful.", "Borrowed until " + record.getDueDate() + ".");
            refreshBookResults();
            refreshRecommendations();
            refreshActiveBorrows();
        });
    }

    // Helper method to handle actions that may throw exceptions, showing an error dialog if an exception occurs, to centralize error handling logic and provide consistent feedback to the user across different operations
    private void handleAction(Runnable action) {
        try {
            action.run();
        } catch (ValidationException | AuthenticationException | BusinessException e) {
            showError("Operation failed", e.getMessage());
        }
    }

    // Helper method to refresh the book search results based on the current search keyword, which is called after actions that may affect the book list (such as approving a submission), ensuring that the displayed book list is always up-to-date with the latest data from the service layer
    private void refreshBookResults() {
        String keyword = searchField == null ? "" : searchField.getText().trim();
        List<Book> books = keyword.isEmpty()
                ? bookService.listApprovedBooksWithAvailability()
                : bookService.searchApprovedBooks(keyword);
        bookResults.setAll(books);
        updateBorrowButtonState();
    }

    // Helper method to refresh the popular book recommendations for the student/staff portal, which is called after actions that may affect book availability (such as borrowing a book), ensuring that the recommendations reflect the most current popular books based on borrow history
    private void refreshRecommendations() {
        recommendationItems.setAll(
                recommendationService.recommendTopPopular(3).stream()
                        .map(book -> book.getTitle() + " by " + book.getAuthorFullName())
                        .toList()
        );
    }

    // Helper method to refresh the list of active borrows for the currently logged-in student/staff user, which is called after borrowing a book and when the user logs in, ensuring that the displayed list of active borrows is accurate and up-to-date with the latest borrow records from the service layer
    private void refreshActiveBorrows() {
        if (currentStudentStaff == null) {
            activeBorrowItems.clear();
            return;
        }
        activeBorrowItems.setAll(
                borrowService.listActiveBorrowsByUser(currentStudentStaff.getUsername()).stream()
                        .map(record -> bookService.findBookById(record.getBookId())
                                .map(book -> book.getTitle() + " (due " + record.getDueDate() + ")")
                                .orElse(record.getBookId() + " (due " + record.getDueDate() + ")"))
                        .toList()
        );
        updateBorrowButtonState();
    }

    // Helper method to refresh the list of drafts for the currently logged-in author, which is called after saving a draft and when the author logs in, ensuring that the displayed list of drafts is accurate and up-to-date with the latest draft data from the service layer
    private void refreshDrafts() {
        if (currentAuthor == null) {
            draftItems.clear();
            return;
        }
        draftItems.setAll(authorDraftService.loadDrafts(currentAuthor.getUsername()));
    }

    // Helper method to refresh the list of pending submissions for the librarian portal, which is called after approving or rejecting a submission and when the librarian logs in, ensuring that the displayed list of pending submissions is accurate and up-to-date with the latest submission data from the service layer
    private void refreshPendingSubmissions() {
        pendingSubmissionItems.setAll(librarianService.getPendingSubmissions());
        refreshBookResults();
    }

    // Helper method to update the enabled/disabled state of the "Borrow Selected Book" button based on the current login status, book selection, book availability, and borrow limit, ensuring that the user can only attempt to borrow a book when all conditions are met and providing immediate feedback on why the button may be disabled
    private void updateBorrowButtonState() {
        if (borrowButton == null || bookTable == null) {
            return;
        }
        Book selectedBook = bookTable.getSelectionModel().getSelectedItem();
        borrowButton.setDisable(currentStudentStaff == null || selectedBook == null || !selectedBook.isAvailable() || hasReachedBorrowLimit());
    }

    // Helper methods to ensure that the user is logged in as the appropriate role before performing certain actions in the author and librarian portals, throwing a validation exception if the user is not logged in, to enforce access control and prevent unauthorized actions
    private void ensureAuthorLogin() {
        if (currentAuthor == null) {
            throw new ValidationException("Please login as author first.");
        }
    }

    // Helper method to ensure that the user is logged in as a librarian before performing certain actions in the librarian portal, throwing a validation exception if the user is not logged in, to enforce access control and prevent unauthorized actions
    private void ensureLibrarianLogin() {
        if (currentLibrarian == null) {
            throw new ValidationException("Please login as librarian first.");
        }
    }

    // Helper method to get the currently selected submission in the librarian portal, throwing a validation exception if no submission is selected, to ensure that actions that require a selected submission (such as previewing, approving, or rejecting) have a valid target submission to operate on
    private BookSubmission2 requireSelectedSubmission() {
        BookSubmission2 submission = pendingSubmissionTable.getSelectionModel().getSelectedItem();
        if (submission == null) {
            throw new ValidationException("Select a submission first.");
        }
        return submission;
    }

    // Helper method to perform UI updates after approving or rejecting a submission in the librarian portal, including refreshing the list of pending submissions and book results, clearing the preview and comment fields, and showing an informational dialog with the result of the action, to provide immediate feedback to the librarian and ensure that the UI reflects the latest state after the action
    private void afterSubmissionUpdate(String message) {
        refreshPendingSubmissions();
        refreshBookResults();
        librarianPreviewArea.clear();
        librarianCommentField.clear();
        showInfo("Librarian update", message);
    }

    // Helper method to populate the author submission form with the details from a selected draft, which is called when the author selects a draft and clicks the "Load Selected Draft" button, allowing the author to easily continue working on a previously saved draft by loading its details into the form fields
    private void populateDraft(BookDraft2 draft) {
        if (draft == null) {
            return;
        }
        authorTitleField.setText(draft.getTitle());
        selectAuthorGenres(draft.getGenres());
        authorDescriptionArea.setText(draft.getDescription());
        authorFileField.setText(draft.getFilePath());
        authorPreviewArea.clear();
    }

    // Helper method to clear the author submission form fields, which is called after logging in and after successfully submitting a book for review, to reset the form to a clean state and prevent any leftover data from previous drafts or submissions from being displayed in the form
    private void clearAuthorForm() {
        authorTitleField.clear();
        if (authorGenreListView != null) {
            authorGenreListView.getSelectionModel().clearSelection();
        }
        authorDescriptionArea.clear();
        authorFileField.clear();
        authorPreviewArea.clear();
    }

    // Helper methods to get and set the selected genres in the author submission form, which are used when saving drafts and publishing submissions to capture the selected genres from the ListView and to set the selected genres when loading a draft, ensuring that the genre selection is properly handled in both directions between the UI and the service layer
    private List<String> selectedAuthorGenres() {
        if (authorGenreListView == null) {
            return List.of();
        }
        return List.copyOf(authorGenreListView.getSelectionModel().getSelectedItems());
    }

    // Helper method to set the selected genres in the author submission form based on a list of genre strings, which is used when loading a draft to reflect the saved genre selections in the ListView, allowing the author to see which genres were previously selected for the draft and to modify them if needed
    private void selectAuthorGenres(List<String> genres) {
        if (authorGenreListView == null) {
            return;
        }

        authorGenreListView.getSelectionModel().clearSelection();
        if (genres == null || genres.isEmpty()) {
            return;
        }

        for (String genre : genres) {
            for (String supportedGenre : authorGenreListView.getItems()) {
                if (supportedGenre.equalsIgnoreCase(genre)) {
                    authorGenreListView.getSelectionModel().select(supportedGenre);
                }
            }
        }
    }

    // Helper method to open a file chooser dialog for the author to select a submission file, which is called when the author clicks the "Browse..." button next to the file path field in the submission form, allowing the author to easily select a file from their system and automatically populate the file path field with the selected file's path
    private void chooseAuthorFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Submission File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Supported files", "*.pdf", "*.txt", "*.doc", "*.docx"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );

        Window window = authorFileField == null || authorFileField.getScene() == null
                ? null
                : authorFileField.getScene().getWindow();
        java.io.File file = chooser.showOpenDialog(window);
        if (file != null) {
            authorFileField.setText(file.getAbsolutePath());
        }
    }

    // Helper method to check if the currently logged-in student/staff user has reached the maximum borrow limit, which is used to disable the borrow button when the user cannot borrow more books, ensuring that the user is aware of the borrow limit and preventing them from attempting to borrow more books than allowed
    private boolean hasReachedBorrowLimit() {
        return currentStudentStaff != null
                && borrowService.listActiveBorrowsByUser(currentStudentStaff.getUsername()).size() >= SecurityConfig.MAX_BORROW_LIMIT;
    }

    // Helper method to show an error dialog with a given header and message, which is called whenever an exception occurs in the handleAction method, providing consistent error feedback to the user across different operations
    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Helper method to show an informational dialog with a given header and message, which is called after successful operations such as borrowing a book or approving a submission, providing positive feedback to the user and confirming that the action was completed successfully
    private void showInfo(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("COMP3111 Library");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
