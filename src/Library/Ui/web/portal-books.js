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
const activeBorrowedBookIds = new Set();
let selectedBorrowedBookId = null;
let allBooks = [];
let currentPage = 1;
const pageSize = 5;
let allNotifications = [];
let currentNotificationPage = 1;
const notificationPageSize = 5;
let readerFileObjectUrl = null;
let currentPdfPageCount = 0;
let activeReaderType = "text";
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

async function detectPdfPageCountFromBytes(arrayBuffer) {
    if (typeof pdfjsLib === "undefined") {
        return 0;
    }

    if (!pdfjsLib.GlobalWorkerOptions.workerSrc) {
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.worker.min.js";
    }

    const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer });
    const pdf = await loadingTask.promise;
    return Number(pdf.numPages || 0);
}

function applyBookmarkControlMode(pageCount, preferredPage = 1) {
    const bookmarkInput = document.getElementById("bookmarkPage");
    const bookmarkSelect = document.getElementById("bookmarkPageSelect");
    const rangeHint = document.getElementById("bookmarkRangeHint");
    const safePreferred = Number.isFinite(Number(preferredPage)) ? Number(preferredPage) : 1;

    if (!bookmarkInput || !bookmarkSelect) {
        return;
    }

    if (pageCount > 0) {
        currentPdfPageCount = pageCount;
        bookmarkInput.style.display = "none";
        bookmarkSelect.style.display = "inline-block";
        bookmarkSelect.innerHTML = "";

        for (let page = 1; page <= pageCount; page += 1) {
            const option = document.createElement("option");
            option.value = String(page);
            option.textContent = `Page ${page}`;
            bookmarkSelect.appendChild(option);
        }

        const bounded = Math.min(Math.max(1, safePreferred), pageCount);
        bookmarkSelect.value = String(bounded);
        bookmarkInput.value = String(bounded);
        bookmarkInput.min = "1";
        bookmarkInput.max = String(pageCount);
        if (rangeHint) {
            rangeHint.textContent = `PDF page range detected: 1 to ${pageCount}. Select one page as bookmark.`;
        }
        return;
    }

    currentPdfPageCount = 0;
    bookmarkSelect.style.display = "none";
    bookmarkSelect.innerHTML = "";
    bookmarkInput.style.display = "inline-block";
    bookmarkInput.min = "1";
    bookmarkInput.max = "";
    bookmarkInput.value = String(Math.max(1, safePreferred));
    if (rangeHint) {
        rangeHint.textContent = "Bookmark page range is not available for this format. Enter a page number manually.";
    }
}

function getSelectedBookmarkPage() {
    const bookmarkInput = document.getElementById("bookmarkPage");
    const bookmarkSelect = document.getElementById("bookmarkPageSelect");
    if (bookmarkSelect && bookmarkSelect.style.display !== "none") {
        return Number(bookmarkSelect.value || "1");
    }
    return Number(bookmarkInput?.value || "1");
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
    updateSearchQuota();
}

function renderBooks(books) {
    const tbody = document.getElementById("booksBody");
    tbody.innerHTML = "";

    if (!Array.isArray(books) || books.length === 0) {
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="6" class="muted">No books available for the current filter.</td>';
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
        const genresText = Array.isArray(book.genres) && book.genres.length > 0
            ? book.genres.join(", ")
            : "-";
        const publishDateText = formatDateOnly(book.publishDate || "") || "-";

        if (!borrowable) {
            selectedBookIds.delete(book.id);
            if (selectedBookId === book.id) {
                selectedBookId = null;
            }
        }

        row.innerHTML = `
            <td>${book.title}</td>
            <td>${book.author}</td>
            <td>${genresText}</td>
            <td>${publishDateText}</td>
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

    const normalizedValues = Array.isArray(values)
        ? values.map((value) => String(value || "").trim().toLowerCase()).filter(Boolean)
        : [];
    const normalizedFilter = filterText.trim().toLowerCase();

    if (matchMode === "exact") {
        return normalizedValues.some((value) => value === normalizedFilter);
    }

    return normalizedValues.some((value) => value.includes(normalizedFilter));
}

function getSelectedGenreFilters() {
    const genreSelect = document.getElementById("genreFilter");
    if (!genreSelect) {
        return [];
    }

    return Array.from(genreSelect.selectedOptions || [])
        .map((option) => String(option.value || "").trim())
        .filter(Boolean);
}

function matchesSelectedGenres(bookGenres, selectedGenres, matchMode) {
    if (!Array.isArray(selectedGenres) || selectedGenres.length === 0) {
        return true;
    }

    return selectedGenres.some((genre) => matchesAnyAdvancedFilter(bookGenres, genre, matchMode));
}

function applyAdvancedFilters(books) {
    const titleFilter = document.getElementById("titleFilter")?.value || "";
    const authorFilter = document.getElementById("authorFilter")?.value || "";
    const selectedGenres = getSelectedGenreFilters();
    const publishedDateFilter = document.getElementById("publishedDateFilter")?.value || "";
    const matchMode = document.getElementById("advancedMatchMode")?.value || "contains";

    return books.filter((book) => {
        const titleMatch = matchesAdvancedFilter(book.title, titleFilter, matchMode);
        const authorMatch = matchesAdvancedFilter(book.author, authorFilter, matchMode);
        const genreMatch = matchesSelectedGenres(book.genres, selectedGenres, matchMode);
        const publishedDateMatch = !publishedDateFilter
            || formatDateOnly(book.publishDate || "") === publishedDateFilter.trim();
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
    const selectAll = document.getElementById("selectAllBorrowsInline");
    if (selectAll) {
        selectAll.checked = false;
    }
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
            const returnedDate = item.returnedDate ? ` on ${formatDateOnly(item.returnedDate)}` : "";
            li.innerHTML = `<div class="borrow-item-row"><span>${item.bookTitle} (Returned${returnedDate})</span></div>`;
            list.appendChild(li);
            return;
        }

        li.innerHTML = `
            <div class="borrow-item-row">
                <input type="checkbox" class="borrow-select-box" data-book-id="${item.bookId}" data-book-title="${escapeHtml(item.bookTitle)}" aria-label="Select ${escapeHtml(item.bookTitle)}">
                <span>${item.bookTitle} (borrowed ${formatDateOnly(item.borrowDate || "")}, due ${formatDateOnly(item.dueDate || "")})${warningLabel ? ` [${warningLabel}]` : ""}</span>
                <div class="borrow-item-actions">
                    <button class="secondary" type="button">Read</button>
                    <button class="secondary" type="button">Return</button>
                </div>
            </div>
        `;
        li.querySelector(".borrow-select-box")?.addEventListener("change", updateReturnSelectedButtonInline);
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

    updateReturnSelectedButtonInline();
}

function getBorrowSelectBoxesInline() {
    return Array.from(document.querySelectorAll(".borrow-select-box"));
}

function updateReturnSelectedButtonInline() {
    const button = document.getElementById("returnSelectedBtnInline");
    if (!button) {
        return;
    }
    const selected = getBorrowSelectBoxesInline().filter((cb) => cb.checked);
    button.textContent = `Return Selected (${selected.length})`;
    button.disabled = selected.length === 0;
}

document.getElementById("selectAllBorrowsInline")?.addEventListener("change", (event) => {
    const checked = event.target.checked === true;
    getBorrowSelectBoxesInline().forEach((cb) => { cb.checked = checked; });
    updateReturnSelectedButtonInline();
});

document.getElementById("returnSelectedBtnInline")?.addEventListener("click", async () => {
    const ids = getBorrowSelectBoxesInline().filter((cb) => cb.checked).map((cb) => cb.dataset.bookId);
    if (ids.length === 0) {
        return;
    }
    if (!confirm(`Confirm return ${ids.length} selected book(s)?`)) {
        return;
    }
    try {
        const payload = await api("/api/return-bulk", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ bookIds: ids.join(",") })
        });
        const failedCount = Array.isArray(payload.failed) ? payload.failed.length : 0;
        showToast(`Returned ${payload.succeeded || 0} book(s)${failedCount ? `; ${failedCount} failed` : ""}.`, failedCount > 0);
        if (failedCount) {
            console.warn("Bulk return failures:", payload.failed);
        }
        if (ids.includes(selectedBorrowedBookId)) {
            resetReaderUi("Book returned.");
        }
        await refreshBooks();
        await refreshBorrows();
    } catch (error) {
        showToast(error.message, true);
    }
});

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
        const categoryFilter = document.getElementById("notificationCategoryFilter")?.value || "all";
        const createdDateFrom = document.getElementById("notificationDateFrom")?.value || "";
        const createdDateTo = document.getElementById("notificationDateTo")?.value || "";
        if (keyword) {
            params.set("q", keyword);
        }
        if (priorityFilter !== "all") {
            params.set("priority", priorityFilter);
        }
        if (categoryFilter !== "all") {
            params.set("category", categoryFilter);
        }
        if (createdDateFrom) {
            params.set("createdDateFrom", createdDateFrom);
        }
        if (createdDateTo) {
            params.set("createdDateTo", createdDateTo);
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
            const created = formatDateOnly(item.createdDate || item.createdAt || "");
            const priority = (item.priority || "NORMAL").toUpperCase();
            const category = item.category || "Other";
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
                    <span style="display:inline-block;margin-left:8px;padding:2px 8px;border-radius:999px;background:#2f4968;color:#fff;font-size:0.75rem;font-weight:700;">${category}</span>
                    <span class="${priorityClass}" style="margin-left:8px;font-size:0.75rem;font-weight:700;padding:2px 8px;border-radius:999px;${priority === "HIGH" ? "background:#8b2f27;color:#fff;" : priority === "LOW" ? "background:#355b2a;color:#fff;" : "background:#2f4968;color:#fff;"}">${priority}</span>
                    <div>${item.message || ""}</div>
                    <small>${created}${item.readAt ? ` | read at ${formatDateOnly(item.readAt)}` : ""}${item.archivedAt ? ` | archived at ${formatDateOnly(item.archivedAt)}` : ""}</small>
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
    activeReaderType = "text";
    applyBookmarkControlMode(0, 1);
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
        activeReaderType = "pdf";
        const response = await fetch(payload.url, { headers });
        if (!response.ok) {
            throw new Error("Failed to load PDF preview.");
        }

        clearReaderObjectUrl();
        const blob = await response.blob();
        const bytes = await blob.arrayBuffer();
        readerFileObjectUrl = URL.createObjectURL(blob);
        const pageCount = await detectPdfPageCountFromBytes(bytes);
        applyBookmarkControlMode(pageCount, 1);

        readerText.style.display = "none";
        readerText.textContent = "";
        readerPdf.style.display = "block";
        readerPdf.src = readerFileObjectUrl;
        return;
    }

    if (payload.type === "docx") {
        activeReaderType = "docx";
        applyBookmarkControlMode(0, 1);
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

    activeReaderType = "text";
    applyBookmarkControlMode(0, 1);
    readerPdf.style.display = "none";
    readerPdf.src = "";
    readerText.style.display = "block";
    readerText.style.whiteSpace = "pre-wrap";
    readerText.textContent = payload.content || "No content available.";
}

async function loadReadingProgress(bookId) {
    const progress = await api(`/api/reading-progress?bookId=${encodeURIComponent(bookId)}`);
    const preferred = Number(progress.bookmark || 1);
    if (currentPdfPageCount > 0) {
        applyBookmarkControlMode(currentPdfPageCount, preferred);
    } else {
        applyBookmarkControlMode(0, preferred);
    }
    if (activeReaderType === "pdf" && readerFileObjectUrl) {
        const readerPdf = document.getElementById("readerPdf");
        if (readerPdf) {
            readerPdf.src = `${readerFileObjectUrl}#page=${Math.max(1, preferred)}`;
        }
    }
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

        const bookmark = getSelectedBookmarkPage();
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
        Array.from(genreFilter.options || []).forEach((option) => {
            option.selected = false;
        });
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
        Array.from(genreFilter.options || []).forEach((option) => {
            option.selected = false;
        });
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

document.getElementById("genreFilter")?.addEventListener("change", () => {
    refreshBooks(document.getElementById("searchKeyword")?.value.trim() || "").catch((e) => showToast(e.message, true));
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

        const days = Number(document.getElementById("borrowDays").value);
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
        await refreshRecommendations();
        await refreshBorrows();
        await refreshBorrowQuota();
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
        await refreshBorrowQuota();
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
    refreshBorrowQuota().catch((e) => showToast(e.message, true));
    refreshNotifications().catch((e) => showToast(e.message, true));
    sessionSnapshotController.checkForRestore().catch((e) => showToast(e.message, true));
}

window.addEventListener("beforeunload", () => {
    clearSelectedBookObjectUrls();
});
