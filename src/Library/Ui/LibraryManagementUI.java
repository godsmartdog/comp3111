package Library.Ui;

import Library.Exception.AuthenticationException;
import Library.Exception.BusinessException;
import Library.Exception.ValidationException;
import Library.Model.Book;
import Library.Model.BookDraft2;
import Library.Model.BookSubmission2;
import Library.Model.BorrowRecord;
import Library.Model.Role;
import Library.Model.User;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
    private TextField authorGenresField;
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

    public Parent createContent() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));

        Label heading = new Label("COMP3111 Library Management System");
        heading.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label subtitle = new Label("All portals are available in JavaFX. Search results mark available titles in black and unavailable titles in red.");
        subtitle.setWrapText(true);

        VBox top = new VBox(6, heading, subtitle);
        root.setTop(top);

        TabPane tabs = new TabPane(
                buildStudentStaffTab(),
                buildAuthorTab(),
                buildLibrarianTab()
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        root.setCenter(tabs);

        refreshBookResults();
        refreshRecommendations();
        refreshDrafts();
        refreshPendingSubmissions();
        refreshActiveBorrows();
        return root;
    }

    private Tab buildStudentStaffTab() {
        studentStatusLabel = new Label("Not logged in.");

        TextField registerUsername = new TextField();
        TextField registerName = new TextField();
        TextField registerPassword = new TextField();
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

        GridPane registerPane = createForm(
                "Student/Staff Registration",
                new String[]{"Username", "Full Name", "Password", "Role"},
                registerUsername, registerName, registerPassword, registerRole
        );
        registerPane.add(registerButton, 1, 4);

        TextField loginUsername = new TextField();
        TextField loginPassword = new TextField();
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

        GridPane loginPane = createForm(
                "Student/Staff Login",
                new String[]{"Username", "Password", "Role"},
                loginUsername, loginPassword, loginRole
        );
        loginPane.add(loginButton, 1, 3);

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

        borrowDaysSpinner = new Spinner<>(1, 60, 14);
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

    private Tab buildAuthorTab() {
        authorStatusLabel = new Label("Not logged in.");

        TextField registerUsername = new TextField();
        TextField registerName = new TextField();
        TextField registerPassword = new TextField();
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
        registerPane.add(registerButton, 1, 4);

        TextField loginUsername = new TextField();
        TextField loginPassword = new TextField();
        Button loginButton = new Button("Login Author");
        loginButton.setOnAction(event -> handleAction(() -> {
            currentAuthor = authorService.loginAuthor(loginUsername.getText(), loginPassword.getText());
            authorStatusLabel.setText("Logged in as " + currentAuthor.getFullName());
            refreshDrafts();
            showInfo("Login successful.", "Author portal is ready.");
        }));

        GridPane loginPane = createForm(
                "Author Login",
                new String[]{"Username", "Password"},
                loginUsername, loginPassword
        );
        loginPane.add(loginButton, 1, 2);

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
        authorGenresField = new TextField();
        authorGenresField.setPromptText(String.join(", ", authorService.getSupportedGenres()));
        authorFileField = new TextField();
        authorDescriptionArea = new TextArea();
        authorDescriptionArea.setWrapText(true);
        authorDescriptionArea.setPrefRowCount(8);
        authorPreviewArea = new TextArea();
        authorPreviewArea.setEditable(false);
        authorPreviewArea.setWrapText(true);

        Button saveDraftButton = new Button("Auto-Save Draft");
        saveDraftButton.setOnAction(event -> handleAction(() -> {
            ensureAuthorLogin();
            authorDraftService.autoSave(
                    currentAuthor.getUsername(),
                    authorTitleField.getText(),
                    parseGenres(authorGenresField.getText()),
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
                        parseGenres(authorGenresField.getText()),
                        authorDescriptionArea.getText()
                ))
        ));

        Button publishButton = new Button("Submit for Review");
        publishButton.setOnAction(event -> handleAction(() -> {
            ensureAuthorLogin();
            fileService.validateSubmissionFile(authorFileField.getText());
            BookSubmission2 submission = authorService.publishBook(
                    currentAuthor.getUsername(),
                    authorTitleField.getText(),
                    parseGenres(authorGenresField.getText()),
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
                new String[]{"Title", "Genres", "File Path"},
                authorTitleField, authorGenresField, authorFileField
        );
        form.add(new Label("Description"), 0, 3);
        form.add(authorDescriptionArea, 1, 3);
        form.add(new HBox(8, saveDraftButton, previewButton, publishButton), 1, 4);

        VBox right = new VBox(10, form, new Label("Preview"), authorPreviewArea);
        VBox.setVgrow(authorPreviewArea, Priority.ALWAYS);
        right.setPadding(new Insets(0, 0, 0, 12));

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.33);

        Tab tab = new Tab("Author", splitPane);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildLibrarianTab() {
        librarianStatusLabel = new Label("Not logged in.");

        TextField registerUsername = new TextField();
        TextField registerName = new TextField();
        TextField registerPassword = new TextField();
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
        registerPane.add(registerButton, 1, 4);

        TextField loginUsername = new TextField();
        TextField loginPassword = new TextField();
        Button loginButton = new Button("Login Librarian");
        loginButton.setOnAction(event -> handleAction(() -> {
            currentLibrarian = librarianService.loginLibrarian(loginUsername.getText(), loginPassword.getText());
            librarianStatusLabel.setText("Logged in as " + currentLibrarian.getFullName());
            refreshPendingSubmissions();
            showInfo("Login successful.", "Librarian portal is ready.");
        }));

        GridPane loginPane = createForm(
                "Librarian Login",
                new String[]{"Username", "Password"},
                loginUsername, loginPassword
        );
        loginPane.add(loginButton, 1, 2);

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

    private void handleAction(Runnable action) {
        try {
            action.run();
        } catch (ValidationException | AuthenticationException | BusinessException e) {
            showError("Operation failed", e.getMessage());
        }
    }

    private void refreshBookResults() {
        String keyword = searchField == null ? "" : searchField.getText().trim();
        List<Book> books = keyword.isEmpty()
                ? bookService.listApprovedBooksWithAvailability()
                : bookService.searchApprovedBooks(keyword);
        bookResults.setAll(books);
        updateBorrowButtonState();
    }

    private void refreshRecommendations() {
        recommendationItems.setAll(
                recommendationService.recommendTopPopular(3).stream()
                        .map(book -> book.getTitle() + " by " + book.getAuthorFullName())
                        .toList()
        );
    }

    private void refreshActiveBorrows() {
        if (currentStudentStaff == null) {
            activeBorrowItems.clear();
            return;
        }
        activeBorrowItems.setAll(
                borrowService.listActiveBorrowsByUser(currentStudentStaff.getUsername()).stream()
                        .map(record -> record.getBookId() + " (due " + record.getDueDate() + ")")
                        .toList()
        );
    }

    private void refreshDrafts() {
        if (currentAuthor == null) {
            draftItems.clear();
            return;
        }
        draftItems.setAll(authorDraftService.loadDrafts(currentAuthor.getUsername()));
    }

    private void refreshPendingSubmissions() {
        pendingSubmissionItems.setAll(librarianService.getPendingSubmissions());
        refreshBookResults();
    }

    private void updateBorrowButtonState() {
        if (borrowButton == null || bookTable == null) {
            return;
        }
        Book selectedBook = bookTable.getSelectionModel().getSelectedItem();
        borrowButton.setDisable(currentStudentStaff == null || selectedBook == null || !selectedBook.isAvailable());
    }

    private void ensureAuthorLogin() {
        if (currentAuthor == null) {
            throw new ValidationException("Please login as author first.");
        }
    }

    private void ensureLibrarianLogin() {
        if (currentLibrarian == null) {
            throw new ValidationException("Please login as librarian first.");
        }
    }

    private BookSubmission2 requireSelectedSubmission() {
        BookSubmission2 submission = pendingSubmissionTable.getSelectionModel().getSelectedItem();
        if (submission == null) {
            throw new ValidationException("Select a submission first.");
        }
        return submission;
    }

    private void afterSubmissionUpdate(String message) {
        refreshPendingSubmissions();
        refreshBookResults();
        librarianPreviewArea.clear();
        librarianCommentField.clear();
        showInfo("Librarian update", message);
    }

    private void populateDraft(BookDraft2 draft) {
        if (draft == null) {
            return;
        }
        authorTitleField.setText(draft.getTitle());
        authorGenresField.setText(String.join(", ", draft.getGenres()));
        authorDescriptionArea.setText(draft.getDescription());
        authorFileField.setText(draft.getFilePath());
    }

    private void clearAuthorForm() {
        authorTitleField.clear();
        authorGenresField.clear();
        authorDescriptionArea.clear();
        authorFileField.clear();
        authorPreviewArea.clear();
    }

    private List<String> parseGenres(String rawGenres) {
        return Arrays.stream(rawGenres.split(","))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("COMP3111 Library");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
