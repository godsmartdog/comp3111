const expectedRole = document.querySelector("main").dataset.role;
const currentUser = requireRole(expectedRole);
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
    const items = await api("/api/borrows");
    list.innerHTML = "";
    items.forEach((item) => {
        const li = document.createElement("li");
        li.innerHTML = `
            <span>${item.bookTitle} (due ${item.dueDate})${item.overdue ? " [OVERDUE]" : ""}</span>
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
    if (!list || !status) {
        return;
    }

    status.textContent = "Loading notifications...";
    try {
        const items = await api("/api/notifications");
        list.innerHTML = "";

        if (!Array.isArray(items) || items.length === 0) {
            status.textContent = "No notifications.";
            return;
        }

        const unreadCount = items.filter((item) => !item.read).length;
        status.textContent = `Total: ${items.length}, Unread: ${unreadCount}`;

        items.forEach((item) => {
            const li = document.createElement("li");
            const readLabel = item.read ? "Read" : "Unread";
            const created = item.createdAt || "";

            li.innerHTML = `
                <div>
                    <strong>[${readLabel}] ${item.title}</strong>
                    <div>${item.message || ""}</div>
                    <small>${created}</small>
                </div>
                <button class="secondary" type="button" ${item.read ? "disabled" : ""}>Mark As Read</button>
            `;

            const button = li.querySelector("button");
            button.addEventListener("click", async () => {
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

            list.appendChild(li);
        });
    } catch (error) {
        status.textContent = "Failed to load notifications.";
        throw error;
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
    refreshNotifications().catch((e) => showToast(e.message, true));
});

if (currentUser) {
    loadProfile().catch((e) => showToast(e.message, true));
    refreshBooks().catch((e) => showToast(e.message, true));
    refreshRecommendations().catch((e) => showToast(e.message, true));
    refreshBorrows().catch((e) => showToast(e.message, true));
    refreshNotifications().catch((e) => showToast(e.message, true));
}
