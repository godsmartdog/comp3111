const expectedRole = document.querySelector("main").dataset.role;
const currentUser = requireRole(expectedRole);
if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let selectedBookId = null;
let selectedBookSummary = null;
let selectedBorrowedBookId = null;
let allBooks = [];
let currentPage = 1;
const pageSize = 5;

function animateSelectedBookState() {
    const selectedBookElement = document.getElementById("selectedBook");
    selectedBookElement.classList.remove("is-updated");
    void selectedBookElement.offsetWidth;
    selectedBookElement.classList.add("is-updated");
}

function updateSelectedBookUi(book) {
    const selectedBookElement = document.getElementById("selectedBook");
    if (!book) {
        selectedBookElement.classList.remove("has-selection", "is-updated");
        selectedBookElement.textContent = "Selected book: none";
        return;
    }

    const days = Number(document.getElementById("borrowDays")?.value || "14");
    selectedBookElement.classList.add("has-selection");
    selectedBookElement.innerHTML = `
        <span class="selected-book-label">Ready to borrow</span>
        <strong class="selected-book-title">${book.title}</strong>
        <span class="selected-book-meta">by ${book.author} · ${days} day${days === 1 ? "" : "s"}</span>
    `;
    animateSelectedBookState();
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
        updatePagerUi();
        return;
    }

    books.forEach((book) => {
        const row = document.createElement("tr");
        const statusClass = book.available ? "status-available" : "status-unavailable";
        const isSelected = selectedBookId === book.id;

        if (isSelected) {
            row.classList.add("book-row-selected");
        }

        row.innerHTML = `
            <td>${book.title}</td>
            <td>${book.author}</td>
            <td class="${statusClass}">${book.available ? "Available" : "Unavailable"}</td>
            <td><button type="button" class="secondary book-select-btn ${isSelected ? "is-selected" : ""}">${isSelected ? "Selected" : "Select"}</button></td>
        `;

        row.querySelector("button").addEventListener("click", () => {
            selectedBookId = book.id;
            selectedBookSummary = { id: book.id, title: book.title, author: book.author };
            updateSelectedBookUi(selectedBookSummary);
            renderCurrentPageBooks();
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

    if (selectedBookId) {
        const latestSelected = allBooks.find((book) => book.id === selectedBookId);
        if (latestSelected) {
            selectedBookSummary = {
                id: latestSelected.id,
                title: latestSelected.title,
                author: latestSelected.author
            };
        }
        updateSelectedBookUi(selectedBookSummary);
    }

    currentPage = 1;
    renderCurrentPageBooks();
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
    const items = await api("/api/borrows/history");
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
        const isReturned = !!item.returned;
        const statusText = isReturned
            ? `Returned${item.returnedDate ? ` on ${item.returnedDate}` : ""}`
            : `Due ${item.dueDate}${item.overdue ? " [OVERDUE]" : ""}`;

        li.innerHTML = isReturned
            ? `<span>${item.bookTitle} (${statusText})</span>`
            : `
                <span>${item.bookTitle} (${statusText})</span>
                <button class="secondary" type="button">Read</button>
                <button class="secondary" type="button">Return</button>
            `;

        if (isReturned) {
            list.appendChild(li);
            return;
        }

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
        const unreadItems = Array.isArray(items) ? items.filter((item) => !item.read) : [];
        list.innerHTML = "";

        if (unreadItems.length === 0) {
            status.textContent = "No notifications.";
            return;
        }

        status.textContent = `Unread: ${unreadItems.length}`;

        unreadItems.forEach((item) => {
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

                    li.remove();
                    const remaining = list.querySelectorAll("li").length;
                    status.textContent = remaining === 0 ? "No notifications." : `Unread: ${remaining}`;
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

document.getElementById("borrowDays").addEventListener("change", () => {
    if (selectedBookSummary) {
        updateSelectedBookUi(selectedBookSummary);
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
        selectedBookId = null;
        selectedBookSummary = null;
        updateSelectedBookUi(null);
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
