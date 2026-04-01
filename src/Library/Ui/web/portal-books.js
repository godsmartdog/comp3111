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
let allNotifications = [];
let currentNotificationPage = 1;
const notificationPageSize = 5;
let readerFileObjectUrl = null;

function resetSelectedBookSummary() {
    const description = document.getElementById("selectedBookDescription");
    const preview = document.getElementById("selectedBookPreview");
    if (description) {
        description.textContent = "Select a book to view description.";
    }
    if (preview) {
        preview.textContent = "First 2-page preview will appear here if available.";
    }
}

async function loadSelectedBookSummary(bookId) {
    const description = document.getElementById("selectedBookDescription");
    const preview = document.getElementById("selectedBookPreview");
    if (!description || !preview || !bookId) {
        return;
    }

    const payload = await api(`/api/books/summary?bookId=${encodeURIComponent(bookId)}`);
    description.textContent = payload.summary || "No description available for this book.";
    preview.textContent = payload.preview || "First 2-page preview is not available.";
}

function clearReaderObjectUrl() {
    if (readerFileObjectUrl) {
        URL.revokeObjectURL(readerFileObjectUrl);
        readerFileObjectUrl = null;
    }
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
        resetSelectedBookSummary();
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

    if (!Array.isArray(books) || books.length === 0) {
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="4" class="muted">No books available for the current filter.</td>';
        tbody.appendChild(row);
        return;
    }

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
            loadSelectedBookSummary(book.id).catch((e) => showToast(e.message, true));
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
    const items = await api("/api/recommendations?limit=10");
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

    if (status) {
        params.set("status", status);
    }
    if (sortBy) {
        params.set("sortBy", sortBy);
        params.set("sortDir", sortDir);
    }

    const query = params.toString();
    const items = await api(query ? `/api/borrows?${query}` : "/api/borrows");
    list.innerHTML = "";

    if (!Array.isArray(items) || items.length === 0) {
        const li = document.createElement("li");
        li.className = "muted";
        li.textContent = "No borrow records yet.";
        list.appendChild(li);
        return;
    }

    items.forEach((item) => {
        const li = document.createElement("li");
        const isReturned = item.returned === true || String(item.status || "").toLowerCase() === "returned";
        const warningLabel = item.overdue
            ? "OVERDUE"
            : item.dueSoon
                ? `DUE SOON: ${item.daysUntilDue} day(s)`
                : "";
        if (item.overdue) {
            li.style.background = "rgba(139, 47, 39, 0.12)";
            li.style.border = "1px solid rgba(139, 47, 39, 0.45)";
        } else if (item.dueSoon) {
            li.style.background = "rgba(176, 116, 29, 0.12)";
            li.style.border = "1px solid rgba(176, 116, 29, 0.4)";
        }

        if (isReturned) {
            const returnedDate = item.returnedDate ? ` on ${item.returnedDate}` : "";
            li.innerHTML = `<div class="borrow-item-row"><span>${item.bookTitle} (Returned${returnedDate})</span></div>`;
            list.appendChild(li);
            return;
        }

        li.innerHTML = `
            <div class="borrow-item-row">
                <span>${item.bookTitle} (borrowed ${item.borrowDate || ""}, due ${item.dueDate})${warningLabel ? ` [${warningLabel}]` : ""}</span>
                <div class="borrow-item-actions">
                    <button class="secondary" type="button">Read</button>
                    <button class="secondary" type="button">Return</button>
                </div>
            </div>
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
                if (!confirm(`Confirm return \"${item.bookTitle}\"?`)) {
                    return;
                }
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
    if (!list || !status) {
        return;
    }

    status.textContent = "Loading notifications...";
    try {
        const params = new URLSearchParams();
        params.set("scope", "active");
        params.set("sortBy", "createdAt");
        params.set("sortDir", "desc");
        const keyword = document.getElementById("notificationKeyword")?.value?.trim() || "";
        const priorityFilter = document.getElementById("notificationPriorityFilter")?.value || "all";
        if (keyword) {
            params.set("q", keyword);
        }
        if (priorityFilter !== "all") {
            params.set("priority", priorityFilter);
        }

        const items = await api(`/api/notifications?${params.toString()}`);
        allNotifications = Array.isArray(items) ? items : [];
        currentNotificationPage = 1;
        renderCurrentNotificationPage();
    } catch (error) {
        status.textContent = "Failed to load notifications.";
        throw error;
    }
}

function getTotalNotificationPages() {
    return Math.max(1, Math.ceil(allNotifications.length / notificationPageSize));
}

function updateNotificationPagerUi() {
    const totalPages = getTotalNotificationPages();
    const pageInfo = document.getElementById("notificationPageInfo");
    const prevBtn = document.getElementById("notificationPrevPageBtn");
    const nextBtn = document.getElementById("notificationNextPageBtn");

    if (pageInfo) {
        pageInfo.textContent = `${currentNotificationPage}/${totalPages}`;
    }
    if (prevBtn) {
        prevBtn.disabled = currentNotificationPage <= 1;
    }
    if (nextBtn) {
        nextBtn.disabled = currentNotificationPage >= totalPages;
    }
}

function renderCurrentNotificationPage() {
    const list = document.getElementById("notificationsList");
    const status = document.getElementById("notificationStatus");
    if (!list || !status) {
        return;
    }

    list.innerHTML = "";

    if (allNotifications.length === 0) {
        status.textContent = "No notifications.";
        updateNotificationPagerUi();
        return;
    }

    const unreadCount = allNotifications.filter((item) => !item.read).length;
    status.textContent = `Total: ${allNotifications.length}, Unread: ${unreadCount}`;

    const start = (currentNotificationPage - 1) * notificationPageSize;
    const end = start + notificationPageSize;
    const currentItems = allNotifications.slice(start, end);

    currentItems.forEach((item) => {
            const li = document.createElement("li");
            const readLabel = item.read ? "Read" : "Unread";
            const created = item.createdAt || "";
            const priority = (item.priority || "NORMAL").toUpperCase();
            const priorityClass = `priority-${priority.toLowerCase()}`;

            li.style.padding = "10px";
            li.style.borderRadius = "8px";
            li.style.marginBottom = "8px";
            li.style.background = item.read ? "rgba(60, 80, 120, 0.12)" : "rgba(32, 53, 79, 0.2)";
            li.style.border = item.read ? "1px solid rgba(120, 140, 180, 0.35)" : "1px solid rgba(74, 116, 173, 0.45)";
            const highLabel = priority === "HIGH" ? " !" : "";

            li.innerHTML = `
                <div>
                    <strong>[${readLabel}] ${item.title}${highLabel}</strong>
                    <span class="${priorityClass}" style="margin-left:8px;font-size:0.75rem;font-weight:700;padding:2px 8px;border-radius:999px;${priority === "HIGH" ? "background:#8b2f27;color:#fff;" : priority === "LOW" ? "background:#355b2a;color:#fff;" : "background:#2f4968;color:#fff;"}">${priority}</span>
                    <div>${item.message || ""}</div>
                    <small>${created}${item.readAt ? ` | read at ${item.readAt}` : ""}${item.archivedAt ? ` | archived at ${item.archivedAt}` : ""}</small>
                </div>
                <div class="notification-actions">
                    <button class="secondary notification-read-btn" type="button" ${item.read ? "disabled" : ""}>Mark As Read</button>
                    <button class="danger notification-delete-btn" type="button">Delete</button>
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
                    item.read = true;
                    if (payload.readAt) {
                        item.readAt = payload.readAt;
                    }
                    renderCurrentNotificationPage();
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
                    allNotifications = allNotifications.filter((notification) => notification.id !== item.id);
                    if (currentNotificationPage > getTotalNotificationPages()) {
                        currentNotificationPage = getTotalNotificationPages();
                    }
                    renderCurrentNotificationPage();
                } catch (error) {
                    showToast(error.message, true);
                }
            });

            list.appendChild(li);
    });

    updateNotificationPagerUi();
}

function getNotificationSnapshotState() {
    return {
        notificationPage: currentNotificationPage
    };
}

function applyNotificationSnapshotState(state) {
    const targetPage = Number(state?.notificationPage || 1);
    const maxPage = getTotalNotificationPages();
    if (!Number.isNaN(targetPage)) {
        currentNotificationPage = Math.min(Math.max(targetPage, 1), maxPage);
    }
    renderCurrentNotificationPage();
}

function resetReaderUi(statusText) {
    const status = document.getElementById("readerStatus");
    const readerPdf = document.getElementById("readerPdf");
    const readerText = document.getElementById("readerText");
    clearReaderObjectUrl();
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
    const headers = {};
    if (currentUser?.sessionId) {
        headers["X-Session-Id"] = currentUser.sessionId;
    }

    if (payload.type === "pdf") {
        const response = await fetch(payload.url, { headers });
        if (!response.ok) {
            throw new Error("Failed to load PDF preview.");
        }

        clearReaderObjectUrl();
        const blob = await response.blob();
        readerFileObjectUrl = URL.createObjectURL(blob);

        readerText.style.display = "none";
        readerText.textContent = "";
        readerPdf.style.display = "block";
        readerPdf.src = readerFileObjectUrl;
        return;
    }

    if (payload.type === "docx") {
        readerPdf.style.display = "none";
        readerPdf.src = "";
        readerText.style.display = "block";
        readerText.style.whiteSpace = "normal";

        if (typeof mammoth === "undefined") {
            readerText.textContent = "DOCX preview dependency is missing.";
            return;
        }

        const response = await fetch(payload.url, { headers });
        if (!response.ok) {
            throw new Error("Failed to load DOCX preview.");
        }

        const arrayBuffer = await response.arrayBuffer();
        const result = await mammoth.convertToHtml({ arrayBuffer });
        readerText.innerHTML = result.value || "No readable DOCX content available.";
        return;
    }

    readerPdf.style.display = "none";
    readerPdf.src = "";
    readerText.style.display = "block";
    readerText.style.whiteSpace = "pre-wrap";
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
        const selected = allBooks.find((book) => book.id === selectedBookId);
        const title = selected?.title || selectedBookId;
        if (!confirm(`Confirm borrow \"${title}\" for ${days} day(s)?`)) {
            return;
        }
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
        if (!confirm(`Confirm borrow ${selectedBookIds.size} selected book(s) for ${days} day(s)?`)) {
            return;
        }
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
        .then(() => sessionSnapshotController?.persistSnapshot("notifications-filter"))
        .catch((e) => showToast(e.message, true));
});

document.getElementById("notificationPrevPageBtn")?.addEventListener("click", () => {
    if (currentNotificationPage > 1) {
        currentNotificationPage -= 1;
        renderCurrentNotificationPage();
        sessionSnapshotController?.persistSnapshot("notifications-page-prev");
    }
});

document.getElementById("notificationNextPageBtn")?.addEventListener("click", () => {
    if (currentNotificationPage < getTotalNotificationPages()) {
        currentNotificationPage += 1;
        renderCurrentNotificationPage();
        sessionSnapshotController?.persistSnapshot("notifications-page-next");
    }
});

document.getElementById("applyBorrowFiltersBtn")?.addEventListener("click", () => {
    refreshBorrows().catch((e) => showToast(e.message, true));
});

document.getElementById("resetBorrowFiltersBtn")?.addEventListener("click", () => {
    const status = document.getElementById("borrowStatusFilter");
    const sortBy = document.getElementById("borrowSortBy");
    const sortDir = document.getElementById("borrowSortDir");

    if (status) {
        status.value = "active";
    }
    if (sortBy) {
        sortBy.value = "";
    }
    if (sortDir) {
        sortDir.value = "asc";
    }

    refreshBorrows().catch((e) => showToast(e.message, true));
});

document.getElementById("checkBorrowRemindersBtn")?.addEventListener("click", async () => {
    try {
        const payload = await api("/api/borrow/reminders/check", {
            method: "POST"
        });
        showToast(`Reminder check complete. Generated ${payload.generated || 0} reminder(s).`, false);
        await refreshBorrows();
        await refreshNotifications();
    } catch (error) {
        showToast(error.message, true);
    }
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

    refreshBooks().catch((e) => showToast(e.message, true));
    resetSelectedBookSummary();
    refreshRecommendations().catch((e) => showToast(e.message, true));
    refreshBorrows().catch((e) => showToast(e.message, true));
    refreshNotifications().catch((e) => showToast(e.message, true));
    sessionSnapshotController.checkForRestore().catch((e) => showToast(e.message, true));
}
