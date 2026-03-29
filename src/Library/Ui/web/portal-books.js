const expectedRole = document.querySelector("main").dataset.role;
const currentUser = requireRole(expectedRole);
let sessionSnapshotController = null;
if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let selectedBookId = null;
const selectedBookIds = new Set();
let selectedBorrowedBookId = null;
let allBooks = [];
let currentPage = 1;
const pageSize = 5;

async function loadProfile() {
    const profile = await api("/api/profile");
    const fullNameInput = document.getElementById("profileFullName");
    const currentPasswordInput = document.getElementById("profileCurrentPassword");
    const newPasswordInput = document.getElementById("profilePassword");
    const profileFeedback = document.getElementById("profileFeedback");

    if (fullNameInput) {
        fullNameInput.value = profile.fullName || "";
    }
    if (currentPasswordInput) {
        currentPasswordInput.value = "";
    }
    if (newPasswordInput) {
        newPasswordInput.value = "";
    }
    if (profileFeedback) {
        profileFeedback.textContent = "";
    }
}

async function saveProfile() {
    const fullNameInput = document.getElementById("profileFullName");
    const currentPasswordInput = document.getElementById("profileCurrentPassword");
    const newPasswordInput = document.getElementById("profilePassword");
    const profileFeedback = document.getElementById("profileFeedback");

    const fullName = (fullNameInput?.value || "").trim();
    const currentPassword = (currentPasswordInput?.value || "").trim();
    const newPassword = (newPasswordInput?.value || "").trim();

    if (!fullName) {
        showToast("Full Name cannot be empty.", true);
        if (profileFeedback) {
            profileFeedback.textContent = "Full Name cannot be empty.";
        }
        return;
    }

    if (newPassword) {
        if (!currentPassword) {
            showToast("Current password is required to change password.", true);
            if (profileFeedback) {
                profileFeedback.textContent = "Current password is required to change password.";
            }
            return;
        }

        const passwordIssues = getPasswordPolicyViolations(newPassword);
        if (passwordIssues.length > 0) {
            const message = passwordIssues[0];
            showToast(message, true);
            if (profileFeedback) {
                profileFeedback.textContent = message;
            }
            return;
        }
    }

    await api("/api/profile", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({
            fullName,
            password: newPassword,
            currentPassword
        })
    }, false);

    if (currentUser) {
        currentUser.fullName = fullName;
        saveCurrentUser(currentUser);
    }

    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine && currentUser) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    if (currentPasswordInput) {
        currentPasswordInput.value = "";
    }
    if (newPasswordInput) {
        newPasswordInput.value = "";
    }
    if (profileFeedback) {
        profileFeedback.textContent = "Profile updated successfully.";
    }
    showToast("Profile updated successfully.", false);
}

function updateSelectedBookLabel() {
    const selectedBookLabel = document.getElementById("selectedBook");
    const totalSelected = selectedBookIds.size;
    if (!selectedBookId) {
        selectedBookLabel.textContent = `Selected book: none | Multi-selected: ${totalSelected}`;
        return;
    }

    const selected = allBooks.find((book) => book.id === selectedBookId);
    const title = selected?.title || selectedBookId;
    selectedBookLabel.textContent = `Selected book: ${title} | Multi-selected: ${totalSelected}`;
}

function syncMultiSelectionWithVisibleBooks() {
    const visibleIds = new Set(allBooks.map((book) => book.id));
    Array.from(selectedBookIds).forEach((bookId) => {
        if (!visibleIds.has(bookId)) {
            selectedBookIds.delete(bookId);
        }
    });

    if (selectedBookId && !visibleIds.has(selectedBookId)) {
        selectedBookId = null;
    }
}

function getTotalPages() {
    return Math.max(1, Math.ceil(allBooks.length / pageSize));
}

function updatePagerUi() {
    const totalPages = getTotalPages();
    const pageInfo = document.getElementById("pageInfo");
    const prevBtn = document.getElementById("prevPageBtn");
    const nextBtn = document.getElementById("nextPageBtn");

    pageInfo.textContent = `${currentPage}/${totalPages}`;
    prevBtn.disabled = currentPage <= 1;
    nextBtn.disabled = currentPage >= totalPages;
}

function renderCurrentPageBooks() {
    const start = (currentPage - 1) * pageSize;
    const end = start + pageSize;
    renderBooks(allBooks.slice(start, end));
    updatePagerUi();
}

function renderBooks(books) {
    const tbody = document.getElementById("booksBody");
    tbody.innerHTML = "";

    books.forEach((book) => {
        const row = document.createElement("tr");
        const statusClass = book.available ? "status-available" : "status-unavailable";

        row.innerHTML = `
            <td>${book.title}</td>
            <td>${book.author}</td>
            <td class="${statusClass}">${book.available ? "Available" : "Unavailable"}</td>
            <td>
                <label>
                    <input class="book-multi-select" type="checkbox" ${selectedBookIds.has(book.id) ? "checked" : ""}>
                    Multi
                </label>
                <button class="secondary" type="button">Select</button>
            </td>
        `;

        const checkbox = row.querySelector(".book-multi-select");
        checkbox.addEventListener("change", () => {
            if (checkbox.checked) {
                selectedBookIds.add(book.id);
            } else {
                selectedBookIds.delete(book.id);
            }
            updateSelectedBookLabel();
        });

        row.querySelector("button").addEventListener("click", () => {
            selectedBookId = book.id;
            updateSelectedBookLabel();
        });

        tbody.appendChild(row);
    });
}

async function refreshBooks(keyword = "") {
    const normalizedKeyword = typeof keyword === "string" ? keyword.trim() : "";
    const availability = document.getElementById("availabilityFilter")?.value || "all";
    const params = new URLSearchParams();
    if (normalizedKeyword) {
        params.set("keyword", normalizedKeyword);
    }
    if (availability !== "all") {
        params.set("availability", availability);
    }

    const query = params.toString();
    const url = query ? `/api/books?${query}` : "/api/books";
    const books = await api(url);
    allBooks = [...books].sort((a, b) => a.title.localeCompare(b.title, undefined, { sensitivity: "base" }));
    syncMultiSelectionWithVisibleBooks();
    currentPage = 1;
    renderCurrentPageBooks();
    updateSelectedBookLabel();
}

async function refreshRecommendations() {
    const list = document.getElementById("recommendations");
    const items = await api("/api/recommendations?limit=5");
    list.innerHTML = "";
    items.forEach((item) => {
        const li = document.createElement("li");
        li.textContent = `${item.title} (${item.author})`;
        list.appendChild(li);
    });
}

async function refreshBorrows() {
    const list = document.getElementById("borrows");
    const params = new URLSearchParams();
    const status = document.getElementById("borrowStatusFilter")?.value || "active";
    const sortBy = document.getElementById("borrowSortBy")?.value || "";
    const sortDir = document.getElementById("borrowSortDir")?.value || "asc";
    const borrowDateFrom = document.getElementById("borrowDateFrom")?.value || "";
    const borrowDateTo = document.getElementById("borrowDateTo")?.value || "";
    const dueDateFrom = document.getElementById("dueDateFrom")?.value || "";
    const dueDateTo = document.getElementById("dueDateTo")?.value || "";

    if (status) {
        params.set("status", status);
    }
    if (sortBy) {
        params.set("sortBy", sortBy);
        params.set("sortDir", sortDir);
    }
    if (borrowDateFrom) {
        params.set("borrowDateFrom", borrowDateFrom);
    }
    if (borrowDateTo) {
        params.set("borrowDateTo", borrowDateTo);
    }
    if (dueDateFrom) {
        params.set("dueDateFrom", dueDateFrom);
    }
    if (dueDateTo) {
        params.set("dueDateTo", dueDateTo);
    }

    const query = params.toString();
    const items = await api(query ? `/api/borrows?${query}` : "/api/borrows");
    list.innerHTML = "";
    items.forEach((item) => {
        const li = document.createElement("li");
        li.innerHTML = `
            <span>${item.bookTitle} (borrowed ${item.borrowDate || ""}, due ${item.dueDate})${item.overdue ? " [OVERDUE]" : ""}</span>
            <button class="secondary" type="button">Read</button>
            <button class="secondary" type="button">Return</button>
        `;
        const buttons = li.querySelectorAll("button");
        const readBtn = buttons[0];
        const returnBtn = buttons[1];

        readBtn.addEventListener("click", async () => {
            try {
                selectedBorrowedBookId = item.bookId;
                document.getElementById("readerStatus").textContent = `Reading: ${item.bookTitle}`;
                await loadBorrowedContent(item.bookId);
                await loadReadingProgress(item.bookId);
            } catch (error) {
                showToast(error.message, true);
            }
        });

        returnBtn.addEventListener("click", async () => {
            try {
                const text = await api("/api/return", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({ bookId: item.bookId })
                }, false);
                showToast(text, false);
                if (selectedBorrowedBookId === item.bookId) {
                    resetReaderUi("Book returned.");
                }
                await refreshBooks();
                await refreshBorrows();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        list.appendChild(li);
    });
}

async function refreshNotifications() {
    const list = document.getElementById("notificationsList");
    const status = document.getElementById("notificationStatus");
    const scopeFilter = document.getElementById("notificationScopeFilter");
    const readFilter = document.getElementById("notificationReadFilter");
    const priorityFilter = document.getElementById("notificationPriorityFilter");
    const sortByFilter = document.getElementById("notificationSortBy");
    const sortDirFilter = document.getElementById("notificationSortDir");
    const searchInput = document.getElementById("notificationSearchInput");
    if (!list || !status) {
        return;
    }

    const selectedScope = scopeFilter?.value || "active";
    const selectedRead = readFilter?.value || "all";
    const selectedPriority = priorityFilter?.value || "all";
    const selectedSortBy = sortByFilter?.value || "createdAt";
    const selectedSortDir = sortDirFilter?.value || "desc";
    const selectedQuery = (searchInput?.value || "").trim();

    const params = new URLSearchParams();
    params.set("scope", selectedScope);
    params.set("read", selectedRead);
    params.set("priority", selectedPriority);
    params.set("sortBy", selectedSortBy);
    params.set("sortDir", selectedSortDir);
    if (selectedQuery) {
        params.set("q", selectedQuery);
    }

    status.textContent = "Loading notifications...";
    try {
        const items = await api(`/api/notifications?${params.toString()}`);
        const activeItems = selectedScope === "active"
            ? items
            : await api("/api/notifications?scope=active");
        list.innerHTML = "";

        if (!Array.isArray(items) || items.length === 0) {
            const activeUnread = Array.isArray(activeItems)
                ? activeItems.filter((item) => !item.read).length
                : 0;
            status.textContent = `No notifications in ${selectedScope} view. Active unread: ${activeUnread}`;
            return;
        }

        const unreadCount = Array.isArray(activeItems)
            ? activeItems.filter((item) => !item.read).length
            : 0;
        const queryLabel = selectedQuery ? ` | Search: ${selectedQuery}` : "";
        status.textContent = `View: ${selectedScope} | Total: ${items.length} | Active Unread: ${unreadCount}${queryLabel}`;

        items.forEach((item) => {
            const li = document.createElement("li");
            const readLabel = item.read ? "Read" : "Unread";
            const created = item.createdAt || "";
            const priority = (item.priority || "NORMAL").toUpperCase();
            const priorityClass = `priority-${priority.toLowerCase()}`;
            const isArchived = item.archived === true;

            li.style.padding = "10px";
            li.style.borderRadius = "8px";
            li.style.marginBottom = "8px";
            li.style.background = item.read ? "rgba(60, 80, 120, 0.12)" : "rgba(32, 53, 79, 0.2)";
            li.style.border = item.read ? "1px solid rgba(120, 140, 180, 0.35)" : "1px solid rgba(74, 116, 173, 0.45)";

            li.innerHTML = `
                <div>
                    <strong>[${readLabel}] ${item.title}</strong>
                    <span class="${priorityClass}" style="margin-left:8px;font-size:0.75rem;font-weight:700;padding:2px 8px;border-radius:999px;${priority === "HIGH" ? "background:#8b2f27;color:#fff;" : priority === "LOW" ? "background:#355b2a;color:#fff;" : "background:#2f4968;color:#fff;"}">${priority}</span>
                    <div>${item.message || ""}</div>
                    <small>${created}${item.readAt ? ` | read at ${item.readAt}` : ""}${item.archivedAt ? ` | archived at ${item.archivedAt}` : ""}</small>
                </div>
                <div>
                    <button class="secondary notification-read-btn" type="button" ${item.read ? "disabled" : ""}>Mark As Read</button>
                    <button class="danger notification-delete-btn" type="button">Delete</button>
                    <button class="secondary notification-archive-btn" type="button">${isArchived ? "Unarchive" : "Archive"}</button>
                </div>
            `;

            const markReadButton = li.querySelector(".notification-read-btn");
            markReadButton.addEventListener("click", async () => {
                try {
                    const payload = await api("/api/notifications/read", {
                        method: "POST",
                        headers: { "Content-Type": "application/x-www-form-urlencoded" },
                        body: formBody({ notificationId: item.id })
                    });
                    showToast(payload.message || "Notification marked as read.", false);
                    await refreshNotifications();
                } catch (error) {
                    showToast(error.message, true);
                }
            });

            const deleteButton = li.querySelector(".notification-delete-btn");
            deleteButton.addEventListener("click", async () => {
                try {
                    const payload = await api("/api/notifications/delete", {
                        method: "POST",
                        headers: { "Content-Type": "application/x-www-form-urlencoded" },
                        body: formBody({ notificationId: item.id })
                    });
                    showToast(payload.message || "Notification deleted.", false);
                    await refreshNotifications();
                } catch (error) {
                    showToast(error.message, true);
                }
            });

            const archiveButton = li.querySelector(".notification-archive-btn");
            archiveButton.addEventListener("click", async () => {
                try {
                    const endpoint = isArchived ? "/api/notifications/unarchive" : "/api/notifications/archive";
                    const payload = await api(endpoint, {
                        method: "POST",
                        headers: { "Content-Type": "application/x-www-form-urlencoded" },
                        body: formBody({ notificationId: item.id })
                    });
                    showToast(payload.message || (isArchived ? "Notification unarchived." : "Notification archived."), false);
                    await refreshNotifications();
                } catch (error) {
                    showToast(error.message, true);
                }
            });

            list.appendChild(li);
        });
    } catch (error) {
        status.textContent = "Failed to load notifications.";
        throw error;
    }
}

function getNotificationSnapshotState() {
    return {
        scope: document.getElementById("notificationScopeFilter")?.value || "active",
        read: document.getElementById("notificationReadFilter")?.value || "all",
        priority: document.getElementById("notificationPriorityFilter")?.value || "all",
        sortBy: document.getElementById("notificationSortBy")?.value || "createdAt",
        sortDir: document.getElementById("notificationSortDir")?.value || "desc",
        query: document.getElementById("notificationSearchInput")?.value || ""
    };
}

function applyNotificationSnapshotState(state) {
    const values = state || {};
    const scope = document.getElementById("notificationScopeFilter");
    const read = document.getElementById("notificationReadFilter");
    const priority = document.getElementById("notificationPriorityFilter");
    const sortBy = document.getElementById("notificationSortBy");
    const sortDir = document.getElementById("notificationSortDir");
    const search = document.getElementById("notificationSearchInput");

    if (scope && values.scope) {
        scope.value = values.scope;
    }
    if (read && values.read) {
        read.value = values.read;
    }
    if (priority && values.priority) {
        priority.value = values.priority;
    }
    if (sortBy && values.sortBy) {
        sortBy.value = values.sortBy;
    }
    if (sortDir && values.sortDir) {
        sortDir.value = values.sortDir;
    }
    if (search) {
        search.value = values.query || "";
    }
}

function resetReaderUi(statusText) {
    const status = document.getElementById("readerStatus");
    const readerPdf = document.getElementById("readerPdf");
    const readerText = document.getElementById("readerText");
    status.textContent = statusText;
    readerPdf.style.display = "none";
    readerPdf.src = "";
    readerText.style.display = "none";
    readerText.textContent = "";
    document.getElementById("bookmarkPage").value = "1";
    document.getElementById("highlightsInput").value = "";
}

async function loadBorrowedContent(bookId) {
    const payload = await api(`/api/borrow/content?bookId=${encodeURIComponent(bookId)}`);
    const readerPdf = document.getElementById("readerPdf");
    const readerText = document.getElementById("readerText");

    if (payload.type === "pdf") {
        readerText.style.display = "none";
        readerText.textContent = "";
        readerPdf.style.display = "block";
        readerPdf.src = payload.url;
        return;
    }

    readerPdf.style.display = "none";
    readerPdf.src = "";
    readerText.style.display = "block";
    readerText.textContent = payload.content || "No content available.";
}

async function loadReadingProgress(bookId) {
    const progress = await api(`/api/reading-progress?bookId=${encodeURIComponent(bookId)}`);
    document.getElementById("bookmarkPage").value = String(progress.bookmark || 1);
    document.getElementById("highlightsInput").value = Array.isArray(progress.highlights)
        ? progress.highlights.join("\n")
        : "";
}

document.getElementById("saveProgressBtn").addEventListener("click", async () => {
    try {
        if (!selectedBorrowedBookId) {
            showToast("Please click Read on a borrowed book first.", true);
            return;
        }

        const bookmark = Number(document.getElementById("bookmarkPage").value || "1");
        const highlights = document.getElementById("highlightsInput").value;
        await api("/api/reading-progress", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                bookId: selectedBorrowedBookId,
                bookmark,
                highlights
            })
        });
        showToast("Reading progress saved.", false);
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("searchBtn").addEventListener("click", () => {
    refreshBooks(document.getElementById("searchKeyword").value.trim()).catch((e) => showToast(e.message, true));
});

document.getElementById("searchKeyword")?.addEventListener("keydown", (event) => {
    if (event.key !== "Enter") {
        return;
    }
    event.preventDefault();
    refreshBooks(document.getElementById("searchKeyword").value.trim()).catch((e) => showToast(e.message, true));
});

document.getElementById("showAllBtn").addEventListener("click", () => {
    document.getElementById("searchKeyword").value = "";
    const availabilityFilter = document.getElementById("availabilityFilter");
    if (availabilityFilter) {
        availabilityFilter.value = "all";
    }
    refreshBooks().catch((e) => showToast(e.message, true));
});

document.getElementById("saveProfileBtn")?.addEventListener("click", () => {
    saveProfile().catch((e) => {
        const profileFeedback = document.getElementById("profileFeedback");
        if (profileFeedback) {
            profileFeedback.textContent = e.message;
        }
        showToast(e.message, true);
    });
});

document.getElementById("prevPageBtn").addEventListener("click", () => {
    if (currentPage > 1) {
        currentPage -= 1;
        renderCurrentPageBooks();
    }
});

document.getElementById("nextPageBtn").addEventListener("click", () => {
    if (currentPage < getTotalPages()) {
        currentPage += 1;
        renderCurrentPageBooks();
    }
});

document.getElementById("borrowBtn").addEventListener("click", async () => {
    try {
        if (!selectedBookId) {
            showToast("Please select a book first.", true);
            return;
        }

        const days = Number(document.getElementById("borrowDays").value);
        const text = await api("/api/borrow", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ bookId: selectedBookId, days })
        }, false);

        showToast(text, false);
        await refreshBooks();
        await refreshRecommendations();
        await refreshBorrows();
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("borrowBulkBtn")?.addEventListener("click", async () => {
    try {
        if (selectedBookIds.size === 0) {
            showToast("Please multi-select at least one book first.", true);
            return;
        }

        const days = Number(document.getElementById("borrowDays").value);
        const text = await api("/api/borrow/bulk", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                bookIds: Array.from(selectedBookIds).join(","),
                days
            })
        }, false);

        showToast(text, false);
        selectedBookIds.clear();
        await refreshBooks();
        await refreshRecommendations();
        await refreshBorrows();
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("refreshNotificationsBtn")?.addEventListener("click", () => {
    refreshNotifications()
        .then(() => sessionSnapshotController?.persistSnapshot("notifications-refresh"))
        .catch((e) => showToast(e.message, true));
});

document.getElementById("applyNotificationFiltersBtn")?.addEventListener("click", () => {
    refreshNotifications()
        .then(() => sessionSnapshotController?.persistSnapshot("notifications-filter-apply"))
        .catch((e) => showToast(e.message, true));
});

document.getElementById("resetNotificationFiltersBtn")?.addEventListener("click", () => {
    const scope = document.getElementById("notificationScopeFilter");
    const read = document.getElementById("notificationReadFilter");
    const priority = document.getElementById("notificationPriorityFilter");
    const sortBy = document.getElementById("notificationSortBy");
    const sortDir = document.getElementById("notificationSortDir");
    const search = document.getElementById("notificationSearchInput");

    if (scope) {
        scope.value = "active";
    }
    if (read) {
        read.value = "all";
    }
    if (priority) {
        priority.value = "all";
    }
    if (sortBy) {
        sortBy.value = "createdAt";
    }
    if (sortDir) {
        sortDir.value = "desc";
    }
    if (search) {
        search.value = "";
    }

    refreshNotifications()
        .then(() => sessionSnapshotController?.persistSnapshot("notifications-filter-reset"))
        .catch((e) => showToast(e.message, true));
});

document.getElementById("applyBorrowFiltersBtn")?.addEventListener("click", () => {
    refreshBorrows().catch((e) => showToast(e.message, true));
});

document.getElementById("resetBorrowFiltersBtn")?.addEventListener("click", () => {
    const status = document.getElementById("borrowStatusFilter");
    const sortBy = document.getElementById("borrowSortBy");
    const sortDir = document.getElementById("borrowSortDir");
    const borrowDateFrom = document.getElementById("borrowDateFrom");
    const borrowDateTo = document.getElementById("borrowDateTo");
    const dueDateFrom = document.getElementById("dueDateFrom");
    const dueDateTo = document.getElementById("dueDateTo");

    if (status) {
        status.value = "active";
    }
    if (sortBy) {
        sortBy.value = "";
    }
    if (sortDir) {
        sortDir.value = "asc";
    }
    if (borrowDateFrom) {
        borrowDateFrom.value = "";
    }
    if (borrowDateTo) {
        borrowDateTo.value = "";
    }
    if (dueDateFrom) {
        dueDateFrom.value = "";
    }
    if (dueDateTo) {
        dueDateTo.value = "";
    }

    refreshBorrows().catch((e) => showToast(e.message, true));
});

if (currentUser) {
    sessionSnapshotController = initSessionSnapshotPortal({
        portalKey: `${currentUser.role.toLowerCase()}-portal`,
        defaultViewKey: "notifications-board",
        getViewKey: () => "notifications-board",
        getState: getNotificationSnapshotState,
        restoreState: async (state) => {
            applyNotificationSnapshotState(state);
            await refreshNotifications();
            showToast("Previous notification view restored.", false);
        },
        bannerMessage: "A previous notification view is available for this session."
    });

    loadProfile().catch((e) => showToast(e.message, true));
    refreshBooks().catch((e) => showToast(e.message, true));
    refreshRecommendations().catch((e) => showToast(e.message, true));
    refreshBorrows().catch((e) => showToast(e.message, true));
    refreshNotifications().catch((e) => showToast(e.message, true));
    sessionSnapshotController.checkForRestore().catch((e) => showToast(e.message, true));
}
