const expectedRole = document.querySelector("main")?.dataset.role || "STUDENT";
const currentUser = requireRole(expectedRole);

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

let selectedBookId = null;
const selectedBookIds = new Set();
let allBooks = [];
let currentPage = 1;
const pageSize = 5;

function updateSelectedBookLabel() {
    const selectedBookLabel = document.getElementById("selectedBook");
    const totalSelected = selectedBookIds.size;
    if (!selectedBookLabel) {
        return;
    }

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

    if (pageInfo) {
        pageInfo.textContent = `${currentPage}/${totalPages}`;
    }
    if (prevBtn) {
        prevBtn.disabled = currentPage <= 1;
    }
    if (nextBtn) {
        nextBtn.disabled = currentPage >= totalPages;
    }
}

function renderBooks(books) {
    const tbody = document.getElementById("booksBody");
    if (!tbody) {
        return;
    }
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
        checkbox?.addEventListener("change", () => {
            if (checkbox.checked) {
                selectedBookIds.add(book.id);
            } else {
                selectedBookIds.delete(book.id);
            }
            updateSelectedBookLabel();
        });

        row.querySelector("button")?.addEventListener("click", () => {
            selectedBookId = book.id;
            updateSelectedBookLabel();
        });

        tbody.appendChild(row);
    });
}

function renderCurrentPageBooks() {
    const start = (currentPage - 1) * pageSize;
    const end = start + pageSize;
    renderBooks(allBooks.slice(start, end));
    updatePagerUi();
}

function matchesAdvancedFilter(value, filterText, matchMode) {
    if (!filterText) {
        return true;
    }

    const normalizedValue = String(value || "").trim().toLowerCase();
    const normalizedFilter = filterText.trim().toLowerCase();
    if (matchMode === "exact") {
        return normalizedValue === normalizedFilter;
    }
    return normalizedValue.includes(normalizedFilter);
}

function applyAdvancedFilters(books) {
    const titleFilter = document.getElementById("titleFilter")?.value || "";
    const authorFilter = document.getElementById("authorFilter")?.value || "";
    const matchMode = document.getElementById("advancedMatchMode")?.value || "contains";

    return books.filter((book) => {
        const titleMatch = matchesAdvancedFilter(book.title, titleFilter, matchMode);
        const authorMatch = matchesAdvancedFilter(book.author, authorFilter, matchMode);
        return titleMatch && authorMatch;
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
    allBooks = applyAdvancedFilters(books)
        .sort((a, b) => a.title.localeCompare(b.title, undefined, { sensitivity: "base" }));
    syncMultiSelectionWithVisibleBooks();
    currentPage = 1;
    renderCurrentPageBooks();
    updateSelectedBookLabel();
}

document.getElementById("searchBtn")?.addEventListener("click", () => {
    refreshBooks(document.getElementById("searchKeyword")?.value.trim() || "").catch((e) => showToast(e.message, true));
});

document.getElementById("searchKeyword")?.addEventListener("keydown", (event) => {
    if (event.key !== "Enter") {
        return;
    }
    event.preventDefault();
    refreshBooks(document.getElementById("searchKeyword")?.value.trim() || "").catch((e) => showToast(e.message, true));
});

document.getElementById("showAllBtn")?.addEventListener("click", () => {
    const keyword = document.getElementById("searchKeyword");
    const availabilityFilter = document.getElementById("availabilityFilter");
    const titleFilter = document.getElementById("titleFilter");
    const authorFilter = document.getElementById("authorFilter");
    const advancedMatchMode = document.getElementById("advancedMatchMode");
    if (keyword) {
        keyword.value = "";
    }
    if (availabilityFilter) {
        availabilityFilter.value = "all";
    }
    if (titleFilter) {
        titleFilter.value = "";
    }
    if (authorFilter) {
        authorFilter.value = "";
    }
    if (advancedMatchMode) {
        advancedMatchMode.value = "contains";
    }
    refreshBooks().catch((e) => showToast(e.message, true));
});

document.getElementById("applyAdvancedSearchBtn")?.addEventListener("click", () => {
    refreshBooks(document.getElementById("searchKeyword")?.value.trim() || "").catch((e) => showToast(e.message, true));
});

document.getElementById("clearAdvancedSearchBtn")?.addEventListener("click", () => {
    const titleFilter = document.getElementById("titleFilter");
    const authorFilter = document.getElementById("authorFilter");
    const advancedMatchMode = document.getElementById("advancedMatchMode");
    if (titleFilter) {
        titleFilter.value = "";
    }
    if (authorFilter) {
        authorFilter.value = "";
    }
    if (advancedMatchMode) {
        advancedMatchMode.value = "contains";
    }
    refreshBooks(document.getElementById("searchKeyword")?.value.trim() || "").catch((e) => showToast(e.message, true));
});

document.getElementById("titleFilter")?.addEventListener("keydown", (event) => {
    if (event.key !== "Enter") {
        return;
    }
    event.preventDefault();
    refreshBooks(document.getElementById("searchKeyword")?.value.trim() || "").catch((e) => showToast(e.message, true));
});

document.getElementById("authorFilter")?.addEventListener("keydown", (event) => {
    if (event.key !== "Enter") {
        return;
    }
    event.preventDefault();
    refreshBooks(document.getElementById("searchKeyword")?.value.trim() || "").catch((e) => showToast(e.message, true));
});

document.getElementById("prevPageBtn")?.addEventListener("click", () => {
    if (currentPage > 1) {
        currentPage -= 1;
        renderCurrentPageBooks();
    }
});

document.getElementById("nextPageBtn")?.addEventListener("click", () => {
    if (currentPage < getTotalPages()) {
        currentPage += 1;
        renderCurrentPageBooks();
    }
});

document.getElementById("borrowBtn")?.addEventListener("click", async () => {
    try {
        if (!selectedBookId) {
            showToast("Please select a book first.", true);
            return;
        }

        const days = Number(document.getElementById("borrowDays")?.value || 14);
        const text = await api("/api/borrow", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ bookId: selectedBookId, days })
        }, false);

        showToast(text, false);
        await refreshBooks();
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

        const days = Number(document.getElementById("borrowDays")?.value || 14);
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
    } catch (error) {
        showToast(error.message, true);
    }
});

if (currentUser) {
    refreshBooks().catch((e) => showToast(e.message, true));
}
