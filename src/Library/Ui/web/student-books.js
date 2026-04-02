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
const activeBorrowedBookIds = new Set();
let allBooks = [];
let currentPage = 1;
const pageSize = 5;
const MAX_BORROW_LIMIT = 5;
let selectedBookCoverObjectUrl = null;
let selectedBookPreviewObjectUrl = null;

function clearSelectedBookObjectUrls() {
    if (selectedBookCoverObjectUrl) {
        URL.revokeObjectURL(selectedBookCoverObjectUrl);
        selectedBookCoverObjectUrl = null;
    }
    if (selectedBookPreviewObjectUrl) {
        URL.revokeObjectURL(selectedBookPreviewObjectUrl);
        selectedBookPreviewObjectUrl = null;
    }
}

async function fetchProtectedBlob(url) {
    const headers = {};
    const current = getCurrentUser();
    if (current?.sessionId) {
        headers["X-Session-Id"] = current.sessionId;
    }

    const response = await fetch(url, { headers });
    if (!response.ok) {
        throw new Error(await response.text());
    }
    return response.blob();
}

async function renderPdfPreviewPages(previewBlob, container) {
    if (!container || typeof pdfjsLib === "undefined") {
        return false;
    }

    if (!pdfjsLib.GlobalWorkerOptions.workerSrc) {
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.worker.min.js";
    }

    const arrayBuffer = await previewBlob.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
    const pageCount = Math.min(2, Number(pdf.numPages || 0));
    container.innerHTML = "";

    for (let pageNumber = 1; pageNumber <= pageCount; pageNumber += 1) {
        const page = await pdf.getPage(pageNumber);
        const viewport = page.getViewport({ scale: 1.15 });
        const canvas = document.createElement("canvas");
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = "100%";
        canvas.style.maxWidth = "100%";
        canvas.style.border = "1px solid #d9d9d9";
        canvas.style.borderRadius = "8px";

        const context = canvas.getContext("2d");
        await page.render({ canvasContext: context, viewport }).promise;
        container.appendChild(canvas);
    }

    return pageCount > 0;
}

function updateSearchQuota() {
    const searchQuota = document.getElementById("searchQuota");
    if (!searchQuota) {
        return;
    }

    const total = allBooks.length;
    const shown = Math.max(0, Math.min(pageSize, total - (currentPage - 1) * pageSize));
    searchQuota.textContent = `Search quota: showing ${shown} of ${total} result(s).`;
}

async function refreshBorrowQuota() {
    const borrowQuota = document.getElementById("borrowQuota");
    if (!borrowQuota) {
        return;
    }

    try {
        const activeBorrows = await api("/api/borrows?status=active");
        const used = Array.isArray(activeBorrows) ? activeBorrows.length : 0;
        const remaining = Math.max(0, MAX_BORROW_LIMIT - used);
        borrowQuota.textContent = `Borrow quota: ${used}/${MAX_BORROW_LIMIT} used (${remaining} remaining).`;
    } catch (_) {
        borrowQuota.textContent = `Borrow quota: max ${MAX_BORROW_LIMIT}.`;
    }
}

async function refreshActiveBorrowedBookIds() {
    activeBorrowedBookIds.clear();
    const activeBorrows = await api("/api/borrows?status=active");
    if (!Array.isArray(activeBorrows)) {
        return;
    }

    activeBorrows.forEach((record) => {
        if (record?.bookId) {
            activeBorrowedBookIds.add(record.bookId);
        }
    });
}

function resetSelectedBookSummary() {
    const description = document.getElementById("selectedBookDescription");
    const preview = document.getElementById("selectedBookPreview");
    const cover = document.getElementById("selectedBookCover");
    const filePreview = document.getElementById("selectedBookFilePreview");
    if (description) {
        description.textContent = "Select a book to view description.";
    }
    if (preview) {
        preview.textContent = "First 2-page preview will appear here if available.";
    }
    if (cover) {
        cover.style.display = "none";
        cover.src = "";
    }
    if (filePreview) {
        filePreview.style.display = "none";
        filePreview.src = "";
    }
}

async function loadSelectedBookSummary(bookId) {
    const description = document.getElementById("selectedBookDescription");
    const preview = document.getElementById("selectedBookPreview");
    const cover = document.getElementById("selectedBookCover");
    const filePreview = document.getElementById("selectedBookFilePreview");
    if (!description || !preview || !bookId) {
        return;
    }

    const payload = await api(`/api/books/summary?bookId=${encodeURIComponent(bookId)}`);
    description.textContent = payload.summary || "No description available for this book.";

    if (cover) {
        cover.style.display = "none";
        cover.src = "";
        if (payload.coverImageUrl) {
            try {
                const coverBlob = await fetchProtectedBlob(payload.coverImageUrl);
                selectedBookCoverObjectUrl = URL.createObjectURL(coverBlob);
                cover.src = selectedBookCoverObjectUrl;
                cover.style.display = "block";
            } catch (error) {
                cover.style.display = "none";
                cover.src = "";
            }
        }
    }

    if (filePreview) {
        filePreview.style.display = "none";
        filePreview.src = "";
        if (payload.previewType === "file" && payload.previewUrl) {
            try {
                const previewBlob = await fetchProtectedBlob(payload.previewUrl);
                selectedBookPreviewObjectUrl = URL.createObjectURL(previewBlob);
                const rendered = await renderPdfPreviewPages(previewBlob, filePreview);
                filePreview.style.display = rendered ? "flex" : "none";
                preview.textContent = rendered
                    ? "Showing first 2 page(s) of the PDF preview."
                    : (payload.preview || "First 2-page preview is not available.");
            } catch (error) {
                preview.textContent = payload.preview || "First 2-page preview is not available.";
            }
        } else {
            preview.textContent = payload.preview || "First 2-page preview is not available.";
        }
    } else {
        preview.textContent = payload.preview || "First 2-page preview is not available.";
    }
}

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
        const alreadyBorrowed = activeBorrowedBookIds.has(book.id);
        const availableCopies = Number(book.availableCopies ?? 0);
        const totalCopies = Number(book.totalCopies ?? 0);
        const borrowable = book.available && !alreadyBorrowed;
        const statusClass = borrowable ? "status-available" : "status-unavailable";
        const statusText = alreadyBorrowed
            ? `Unavailable (already borrowed by you, ${availableCopies}/${totalCopies} copies left)`
            : (book.available
                ? `Available (${availableCopies}/${totalCopies} copies)`
                : `Unavailable (${availableCopies}/${totalCopies} copies)`);

        if (!borrowable) {
            selectedBookIds.delete(book.id);
            if (selectedBookId === book.id) {
                selectedBookId = null;
            }
        }

        row.innerHTML = `
            <td>${book.title}</td>
            <td>${book.author}</td>
            <td class="${statusClass}">${statusText}</td>
            <td>
                <label>
                    <input class="book-multi-select" type="checkbox" ${selectedBookIds.has(book.id) ? "checked" : ""} ${borrowable ? "" : "disabled"}>
                    Multi
                </label>
                <button class="secondary" type="button" ${borrowable ? "" : "disabled"}>Select</button>
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
            loadSelectedBookSummary(book.id).catch((e) => showToast(e.message, true));
        });

        tbody.appendChild(row);
    });
}

function renderCurrentPageBooks() {
    const start = (currentPage - 1) * pageSize;
    const end = start + pageSize;
    renderBooks(allBooks.slice(start, end));
    updatePagerUi();
    updateSearchQuota();
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

function matchesAnyAdvancedFilter(values, filterText, matchMode) {
    if (!filterText) {
        return true;
    }

    const normalizedFilter = filterText.trim().toLowerCase();
    const normalizedValues = Array.isArray(values)
        ? values.map((value) => String(value || "").trim().toLowerCase()).filter(Boolean)
        : [];

    if (matchMode === "exact") {
        return normalizedValues.some((value) => value === normalizedFilter);
    }

    return normalizedValues.some((value) => value.includes(normalizedFilter));
}

function applyAdvancedFilters(books) {
    const titleFilter = document.getElementById("titleFilter")?.value || "";
    const authorFilter = document.getElementById("authorFilter")?.value || "";
    const genreFilter = document.getElementById("genreFilter")?.value || "";
    const publishedDateFilter = document.getElementById("publishedDateFilter")?.value || "";
    const matchMode = document.getElementById("advancedMatchMode")?.value || "contains";

    return books.filter((book) => {
        const titleMatch = matchesAdvancedFilter(book.title, titleFilter, matchMode);
        const authorMatch = matchesAdvancedFilter(book.author, authorFilter, matchMode);
        const genreMatch = matchesAnyAdvancedFilter(book.genres, genreFilter, matchMode);
        const publishedDateMatch = !publishedDateFilter
            || String(book.publishDate || "").trim() === publishedDateFilter.trim();
        return titleMatch && authorMatch && genreMatch && publishedDateMatch;
    });
}

async function refreshBooks(keyword = "") {
    await refreshActiveBorrowedBookIds();
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
    const genreFilter = document.getElementById("genreFilter");
    const publishedDateFilter = document.getElementById("publishedDateFilter");
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
    if (genreFilter) {
        genreFilter.value = "";
    }
    if (publishedDateFilter) {
        publishedDateFilter.value = "";
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
    const genreFilter = document.getElementById("genreFilter");
    const publishedDateFilter = document.getElementById("publishedDateFilter");
    const advancedMatchMode = document.getElementById("advancedMatchMode");
    if (titleFilter) {
        titleFilter.value = "";
    }
    if (authorFilter) {
        authorFilter.value = "";
    }
    if (genreFilter) {
        genreFilter.value = "";
    }
    if (publishedDateFilter) {
        publishedDateFilter.value = "";
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

document.getElementById("genreFilter")?.addEventListener("keydown", (event) => {
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
        await refreshBorrowQuota();
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
        const selectedTitles = Array.from(selectedBookIds)
            .map((id) => allBooks.find((book) => book.id === id)?.title || id)
            .join("\n- ");
        const confirmationMessage = [
            `Confirm borrow ${selectedBookIds.size} selected book(s) for ${days} day(s)?`,
            "",
            "Selected books:",
            `- ${selectedTitles}`
        ].join("\n");

        if (!confirm(confirmationMessage)) {
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
        await refreshBorrowQuota();
    } catch (error) {
        showToast(error.message, true);
    }
});

if (currentUser) {
    resetSelectedBookSummary();
    refreshBooks().catch((e) => showToast(e.message, true));
    refreshBorrowQuota().catch((e) => showToast(e.message, true));
}

window.addEventListener("beforeunload", () => {
    clearSelectedBookObjectUrls();
});
