const currentUser = requireRole("LIBRARIAN");

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

let publishedBooks = [];
let selectedEditBookId = "";
let editSelectedFile = null;
let editSelectedCoverImage = null;
let addSelectedFile = null;
let addSelectedCoverImage = null;
const PENDING_PUBLISHED_BOOK_PREFILL_KEY = "pendingPublishedBookPrefill";

function getSelectedGenres(selectId) {
    const select = document.getElementById(selectId);
    if (!select) {
        return "";
    }
    return Array.from(select.selectedOptions)
        .map((option) => option.value.trim())
        .filter(Boolean)
        .join(",");
}

function setSelectedGenres(selectId, values) {
    const select = document.getElementById(selectId);
    if (!select) {
        return;
    }

    const normalize = (value) => String(value || "").trim().toLowerCase().replace(/[^a-z0-9]/g, "");
    const selectedSet = new Set((values || []).map((item) => normalize(item)).filter(Boolean));
    Array.from(select.options).forEach((option) => {
        option.selected = selectedSet.has(normalize(option.value));
    });
}

function validateRequiredFields(values) {
    if (!values.title) {
        throw new Error("Title is required.");
    }
    if (!values.authorNames) {
        throw new Error("Author name is required.");
    }
    if (!values.genres) {
        throw new Error("At least one genre is required.");
    }
    if (!values.description) {
        throw new Error("Description is required.");
    }
    if (!values.hasFilePath) {
        throw new Error("Book file is required.");
    }
}

function renderPublishedBooks(items) {
    const body = document.getElementById("publishedBooksBody");
    const status = document.getElementById("booksStatus");
    if (!body || !status) {
        return;
    }

    body.innerHTML = "";
    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No published books found.";
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="7" class="muted">No published books found.</td>';
        body.appendChild(row);
        updateBulkSelectionState();
        return;
    }

    status.textContent = `Found ${items.length} published book(s).`;
    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td><input type="checkbox" class="bulk-row-checkbox" data-book-id="${escapeHtml(item.id || "")}"></td>
            <td>${escapeHtml(item.title || "")}</td>
            <td>${escapeHtml(item.author || "")}</td>
            <td>${Array.isArray(item.genres) ? escapeHtml(item.genres.join(", ")) : ""}</td>
            <td>${escapeHtml(formatDateOnly(item.publishDate || ""))}</td>
            <td>${Number(item.availableCopies || 0)}/${Number(item.totalCopies || 0)}</td>
            <td><button class="secondary" type="button">Edit</button></td>
        `;

        row.querySelector("button")?.addEventListener("click", () => {
            loadBookForEdit(item.id);
        });
        row.querySelector(".bulk-row-checkbox")?.addEventListener("change", updateBulkSelectionState);
        body.appendChild(row);
    });
    const selectAll = document.getElementById("bulkSelectAll");
    if (selectAll) {
        selectAll.checked = false;
    }
    updateBulkSelectionState();
}

function getSelectedBookIds() {
    return Array.from(document.querySelectorAll(".bulk-row-checkbox"))
        .filter((cb) => cb.checked)
        .map((cb) => cb.getAttribute("data-book-id"))
        .filter(Boolean);
}

function updateBulkDeleteState() {
    const btn = document.getElementById("bulkDeleteBtn");
    if (!btn) return;
    const count = getSelectedBookIds().length;
    btn.disabled = count === 0;
    btn.textContent = count > 0 ? `Delete Selected (${count})` : "Delete Selected";
}

function updateBulkEditStatus() {
    const status = document.getElementById("bulkEditStatus");
    if (!status) return;
    const ids = getSelectedBookIds();
    if (ids.length === 0) {
        status.textContent = "No published books selected.";
        return;
    }
    const titles = ids
        .map((id) => publishedBooks.find((book) => book.id === id)?.title || id)
        .slice(0, 5);
    const suffix = ids.length > 5 ? `, and ${ids.length - 5} more` : "";
    status.textContent = `Selected ${ids.length} published book(s): ${titles.join(", ")}${suffix}.`;
}

function updateBulkSelectionState() {
    updateBulkDeleteState();
    updateBulkEditStatus();
}

async function bulkDeleteSelected() {
    const ids = getSelectedBookIds();
    if (ids.length === 0) return;
    if (!confirm(`Delete ${ids.length} selected book(s)? This cannot be undone.`)) {
        return;
    }
    const result = await api("/api/librarian/published-books-bulk-delete", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ bookIds: ids.join(",") })
    });
    const deleted = Number(result?.deleted || 0);
    const failed = Array.isArray(result?.failed) ? result.failed : [];
    if (failed.length === 0) {
        showToast(`Deleted ${deleted} book(s).`, false);
    } else {
        showToast(`Deleted ${deleted}; ${failed.length} failed: ${failed.map((f) => f.id).join(", ")}`, true);
    }
    await refreshBooks();
    refreshAdminStats().catch(() => {});
}

function renderBulkEditResult(result) {
    const box = document.getElementById("bulkEditResult");
    if (!box) return;
    const skipped = Array.isArray(result?.skipped) ? result.skipped : [];
    if (skipped.length === 0) {
        box.style.display = "none";
        box.innerHTML = "";
        return;
    }
    box.style.display = "block";
    box.innerHTML = `
        <strong>Skipped books</strong>
        <ul>
            ${skipped.map((item) => `<li>${escapeHtml(item.id || "")}: ${escapeHtml(item.reason || "Failed")}</li>`).join("")}
        </ul>
    `;
}

function clearBulkEditForm() {
    ["bulkApplyDescription", "bulkApplyGenres", "bulkApplyTotalCopies"].forEach((id) => {
        const input = document.getElementById(id);
        if (input) input.checked = false;
    });
    const description = document.getElementById("bulkDescription");
    const totalCopies = document.getElementById("bulkTotalCopies");
    if (description) description.value = "";
    if (totalCopies) totalCopies.value = "";
    setSelectedGenres("bulkGenres", []);
    const result = document.getElementById("bulkEditResult");
    if (result) {
        result.style.display = "none";
        result.innerHTML = "";
    }
}

async function bulkEditSelected() {
    const ids = getSelectedBookIds();
    if (ids.length === 0) {
        throw new Error("Select at least one published book to bulk edit.");
    }

    const applyDescription = Boolean(document.getElementById("bulkApplyDescription")?.checked);
    const applyGenres = Boolean(document.getElementById("bulkApplyGenres")?.checked);
    const applyTotalCopies = Boolean(document.getElementById("bulkApplyTotalCopies")?.checked);
    if (!applyDescription && !applyGenres && !applyTotalCopies) {
        throw new Error("Select at least one field to update.");
    }

    const description = document.getElementById("bulkDescription")?.value.trim() || "";
    const genres = getSelectedGenres("bulkGenres");
    const totalCopies = document.getElementById("bulkTotalCopies")?.value.trim() || "";
    if (applyDescription && !description) {
        throw new Error("Description is required when updating description.");
    }
    if (applyGenres && !genres) {
        throw new Error("At least one genre is required when updating genres.");
    }
    const totalCopiesValue = Number(totalCopies);
    if (applyTotalCopies && (!totalCopies || !Number.isInteger(totalCopiesValue) || totalCopiesValue < 1)) {
        throw new Error("Total copies must be at least 1 when updating total copies.");
    }

    if (!confirm(`Apply selected bulk edits to ${ids.length} published book(s)?`)) {
        return;
    }

    const result = await api("/api/librarian/published-books-bulk-edit", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({
            bookIds: ids.join(","),
            applyDescription: applyDescription ? "true" : "false",
            applyGenres: applyGenres ? "true" : "false",
            applyTotalCopies: applyTotalCopies ? "true" : "false",
            description,
            genres,
            totalCopies
        })
    });

    const updatedCount = Number(result?.updatedCount || 0);
    const skippedCount = Number(result?.skippedCount || 0);
    const message = result?.message || `Bulk edit complete. Updated ${updatedCount} book(s)${skippedCount > 0 ? `, skipped ${skippedCount}.` : "."}`;
    showToast(message, skippedCount > 0);
    renderBulkEditResult(result);
    await refreshBooks();
    refreshAdminStats().catch(() => {});
    if (selectedEditBookId) {
        refreshVersionHistory(selectedEditBookId).catch(() => {});
    }
}

async function refreshAdminStats() {
    const status = document.getElementById("adminStatsStatus");
    try {
        const data = await api("/api/librarian/library-admin-stats");
        document.getElementById("adminTotalBooks").textContent = String(data.totalBooks ?? 0);
        document.getElementById("adminTotalAuthors").textContent = String(data.totalAuthors ?? 0);
        document.getElementById("adminGenreCoverage").textContent = String(data.genreCoverage ?? 0);
        const avg = Number(data.avgBooksPerAuthor);
        document.getElementById("adminAvgBooksPerAuthor").textContent = Number.isFinite(avg) ? avg.toFixed(2) : "0.00";
        if (status) status.textContent = "Library stats up to date.";
    } catch (error) {
        if (status) status.textContent = `Failed to load stats: ${error.message}`;
    }
}

async function refreshVersionHistory(bookId) {
    const body = document.getElementById("versionHistoryBody");
    const status = document.getElementById("versionHistoryStatus");
    if (!body || !status) return;
    body.innerHTML = "";
    if (!bookId) {
        status.textContent = "Select a book to view its edit history.";
        return;
    }
    status.textContent = "Loading version history...";
    try {
        const items = await api(`/api/librarian/published-book-history?bookId=${encodeURIComponent(bookId)}`);
        if (!Array.isArray(items) || items.length === 0) {
            status.textContent = "No edits recorded yet.";
            return;
        }
        status.textContent = `${items.length} edit entr${items.length === 1 ? "y" : "ies"}.`;
        items.forEach((entry) => {
            const row = document.createElement("tr");
            row.innerHTML = `
                <td>${escapeHtml(entry.timestamp || "")}</td>
                <td>${escapeHtml(entry.editorUsername || "")}</td>
                <td>${escapeHtml(entry.fieldName || "")}</td>
                <td><span class="muted">${escapeHtml(entry.oldValue || "")}</span> → ${escapeHtml(entry.newValue || "")}</td>
            `;
            body.appendChild(row);
        });
    } catch (error) {
        status.textContent = `Failed to load history: ${error.message}`;
    }
}

function loadBookForEdit(bookId) {
    const target = publishedBooks.find((book) => book.id === bookId);
    if (!target) {
        showToast("Selected book cannot be found. Please refresh.", true);
        return;
    }

    selectedEditBookId = target.id;
    document.getElementById("editBookId").value = target.id || "";
    document.getElementById("editTitle").value = target.title || "";
    document.getElementById("editAuthorNames").value = target.author || "";
    document.getElementById("editDescription").value = target.description || "";
    document.getElementById("editFilePath").value = target.filePath || "";
    document.getElementById("editCoverImagePath").value = target.coverImagePath || "";
    setSelectedGenres("editGenres", Array.isArray(target.genres) ? target.genres : []);
    editSelectedFile = null;
    editSelectedCoverImage = null;
    const editFileInput = document.getElementById("editFileInput");
    const editCoverInput = document.getElementById("editCoverImageInput");
    if (editFileInput) {
        editFileInput.value = "";
    }
    if (editCoverInput) {
        editCoverInput.value = "";
    }
    showToast("Book loaded for editing.", false);
    refreshVersionHistory(target.id).catch(() => {});
}

function clearEditForm() {
    selectedEditBookId = "";
    editSelectedFile = null;
    editSelectedCoverImage = null;
    document.getElementById("editBookId").value = "";
    document.getElementById("editTitle").value = "";
    document.getElementById("editAuthorNames").value = "";
    document.getElementById("editDescription").value = "";
    document.getElementById("editFilePath").value = "";
    document.getElementById("editCoverImagePath").value = "";
    setSelectedGenres("editGenres", []);
    const editFileInput = document.getElementById("editFileInput");
    const editCoverInput = document.getElementById("editCoverImageInput");
    if (editFileInput) {
        editFileInput.value = "";
    }
    if (editCoverInput) {
        editCoverInput.value = "";
    }
}

function clearAddForm() {
    addSelectedFile = null;
    addSelectedCoverImage = null;
    document.getElementById("addTitle").value = "";
    document.getElementById("addAuthorNames").value = "";
    document.getElementById("addDescription").value = "";
    document.getElementById("addFilePath").value = "";
    document.getElementById("addCoverImagePath").value = "";
    setSelectedGenres("addGenres", []);
    const addFileInput = document.getElementById("addFileInput");
    const addCoverInput = document.getElementById("addCoverImageInput");
    if (addFileInput) {
        addFileInput.value = "";
    }
    if (addCoverInput) {
        addCoverInput.value = "";
    }
}

function consumePendingPublishedBookPrefill() {
    let raw = "";
    try {
        raw = sessionStorage.getItem(PENDING_PUBLISHED_BOOK_PREFILL_KEY) || "";
        if (!raw) {
            return;
        }
        sessionStorage.removeItem(PENDING_PUBLISHED_BOOK_PREFILL_KEY);
    } catch (error) {
        return;
    }

    let prefill = null;
    try {
        prefill = JSON.parse(raw);
    } catch (error) {
        return;
    }
    if (!prefill || typeof prefill !== "object") {
        return;
    }

    if (prefill.title) {
        document.getElementById("addTitle").value = prefill.title;
    }
    if (prefill.authorNames) {
        document.getElementById("addAuthorNames").value = prefill.authorNames;
    }
    if (prefill.genres) {
        setSelectedGenres("addGenres", String(prefill.genres).split(","));
    }
    if (prefill.description) {
        document.getElementById("addDescription").value = prefill.description;
    }
    if (prefill.coverImagePath) {
        document.getElementById("addCoverImagePath").value = prefill.coverImagePath;
    }

    const addSection = document.getElementById("addPublishedBookSection");
    addSection?.scrollIntoView({ behavior: "smooth", block: "start" });
    showToast(prefill.sourcePdfUrl
        ? "Prefilled Add New Published Book from request. Choose a local book file before saving."
        : "Prefilled Add New Published Book from request.", false);
}

function filterBooksByKeyword(items, keyword) {
    if (!keyword) {
        return items;
    }
    const normalized = keyword.toLowerCase();
    return items.filter((item) => {
        const haystack = [
            item.id || "",
            item.title || "",
            item.author || ""
        ].join(" ").toLowerCase();
        return haystack.includes(normalized);
    });
}

async function refreshBooks() {
    const status = document.getElementById("booksStatus");
    if (status) {
        status.textContent = "Loading published books...";
    }

    const params = new URLSearchParams();
    const genre = document.getElementById("filterGenre")?.value.trim() || "";
    const author = document.getElementById("filterAuthor")?.value.trim() || "";
    const statusFilter = document.getElementById("filterStatus")?.value.trim() || "";
    if (genre) params.set("genre", genre);
    if (author) params.set("author", author);
    if (statusFilter) params.set("status", statusFilter);
    const path = `/api/librarian/approved-books${params.toString() ? `?${params.toString()}` : ""}`;
    const items = await api(path);
    publishedBooks = Array.isArray(items) ? items : [];
    const keyword = document.getElementById("bookKeyword")?.value.trim() || "";
    renderPublishedBooks(filterBooksByKeyword(publishedBooks, keyword));
}

async function generateDescription(title, authorNames, genres) {
    const response = await api("/api/librarian/published-book/generate-description", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ title, authorNames, genres, note: "", content: "", summaryLevel: "medium" })
    });
    return response.summary || response.description || "";
}

function setGenerateButtonState(button, isGenerating) {
    if (!button) {
        return;
    }
    button.disabled = isGenerating;
    button.textContent = isGenerating ? "Generating..." : (button.dataset.defaultLabel || "Generate Summary");
}

async function saveEdit() {
    if (!selectedEditBookId) {
        throw new Error("Please select a book to edit first.");
    }

    const title = document.getElementById("editTitle")?.value.trim() || "";
    const authorNames = document.getElementById("editAuthorNames")?.value.trim() || "";
    const genres = getSelectedGenres("editGenres");
    const description = document.getElementById("editDescription")?.value.trim() || "";
    const filePath = document.getElementById("editFilePath")?.value.trim() || "";
    const coverImagePath = document.getElementById("editCoverImagePath")?.value.trim() || "";

    validateRequiredFields({
        title,
        authorNames,
        genres,
        description,
        hasFilePath: Boolean(filePath || editSelectedFile)
    });

    if (!confirm("Confirm updating this published book?")) {
        return;
    }

    let text;
    if (editSelectedFile || editSelectedCoverImage) {
        const payload = new FormData();
        payload.append("bookId", selectedEditBookId);
        payload.append("title", title);
        payload.append("authorNames", authorNames);
        payload.append("genres", genres);
        payload.append("description", description);
        if (filePath) {
            payload.append("filePath", filePath);
        }
        if (coverImagePath) {
            payload.append("coverImagePath", coverImagePath);
        }
        if (editSelectedFile) {
            payload.append("file", editSelectedFile, editSelectedFile.name);
        }
        if (editSelectedCoverImage) {
            payload.append("coverImage", editSelectedCoverImage, editSelectedCoverImage.name);
        }

        text = await api("/api/librarian/published-book/update", {
            method: "POST",
            body: payload
        }, false);
    } else {
        text = await api("/api/librarian/published-book/update", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                bookId: selectedEditBookId,
                title,
                authorNames,
                genres,
                description,
                filePath,
                coverImagePath
            })
        }, false);
    }

    showToast(text || "Published book updated.", false);
    await refreshBooks();
    loadBookForEdit(selectedEditBookId);
    refreshAdminStats().catch(() => {});
}

async function addBook() {
    const title = document.getElementById("addTitle")?.value.trim() || "";
    const authorNames = document.getElementById("addAuthorNames")?.value.trim() || "";
    const genres = getSelectedGenres("addGenres");
    const description = document.getElementById("addDescription")?.value.trim() || "";
    const filePath = document.getElementById("addFilePath")?.value.trim() || "";
    const coverImagePath = document.getElementById("addCoverImagePath")?.value.trim() || "";

    validateRequiredFields({
        title,
        authorNames,
        genres,
        description,
        hasFilePath: Boolean(filePath || addSelectedFile)
    });

    if (!confirm("Confirm adding this new published book?")) {
        return;
    }

    let text;
    if (addSelectedFile || addSelectedCoverImage) {
        const payload = new FormData();
        payload.append("title", title);
        payload.append("authorNames", authorNames);
        payload.append("genres", genres);
        payload.append("description", description);
        if (filePath) {
            payload.append("filePath", filePath);
        }
        if (coverImagePath) {
            payload.append("coverImagePath", coverImagePath);
        }
        if (addSelectedFile) {
            payload.append("file", addSelectedFile, addSelectedFile.name);
        }
        if (addSelectedCoverImage) {
            payload.append("coverImage", addSelectedCoverImage, addSelectedCoverImage.name);
        }

        text = await api("/api/librarian/published-book/add", {
            method: "POST",
            body: payload
        }, false);
    } else {
        text = await api("/api/librarian/published-book/add", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ title, authorNames, genres, description, filePath, coverImagePath })
        }, false);
    }

    showToast(text || "Published book added.", false);
    clearAddForm();
    await refreshBooks();
    refreshAdminStats().catch(() => {});
}

document.getElementById("searchBooksBtn")?.addEventListener("click", () => {
    const keyword = document.getElementById("bookKeyword")?.value.trim() || "";
    renderPublishedBooks(filterBooksByKeyword(publishedBooks, keyword));
});

document.getElementById("refreshBooksBtn")?.addEventListener("click", () => {
    refreshBooks().catch((error) => showToast(error.message, true));
});

document.getElementById("applyFiltersBtn")?.addEventListener("click", () => {
    refreshBooks().catch((error) => showToast(error.message, true));
});

document.getElementById("resetFiltersBtn")?.addEventListener("click", () => {
    const g = document.getElementById("filterGenre");
    const a = document.getElementById("filterAuthor");
    const s = document.getElementById("filterStatus");
    const k = document.getElementById("bookKeyword");
    if (g) g.value = "";
    if (a) a.value = "";
    if (s) s.value = "";
    if (k) k.value = "";
    refreshBooks().catch((error) => showToast(error.message, true));
});

document.getElementById("pickEditFileBtn")?.addEventListener("click", () => {
    document.getElementById("editFileInput")?.click();
});

document.getElementById("pickEditCoverBtn")?.addEventListener("click", () => {
    document.getElementById("editCoverImageInput")?.click();
});

document.getElementById("pickAddFileBtn")?.addEventListener("click", () => {
    document.getElementById("addFileInput")?.click();
});

document.getElementById("pickAddCoverBtn")?.addEventListener("click", () => {
    document.getElementById("addCoverImageInput")?.click();
});

document.getElementById("editFileInput")?.addEventListener("change", (event) => {
    const file = event.target.files && event.target.files[0] ? event.target.files[0] : null;
    editSelectedFile = file;
    if (file) {
        document.getElementById("editFilePath").value = file.name;
    }
});

document.getElementById("editCoverImageInput")?.addEventListener("change", (event) => {
    const file = event.target.files && event.target.files[0] ? event.target.files[0] : null;
    editSelectedCoverImage = file;
    if (file) {
        document.getElementById("editCoverImagePath").value = file.name;
    }
});

document.getElementById("addFileInput")?.addEventListener("change", (event) => {
    const file = event.target.files && event.target.files[0] ? event.target.files[0] : null;
    addSelectedFile = file;
    if (file) {
        document.getElementById("addFilePath").value = file.name;
    }
});

document.getElementById("addCoverImageInput")?.addEventListener("change", (event) => {
    const file = event.target.files && event.target.files[0] ? event.target.files[0] : null;
    addSelectedCoverImage = file;
    if (file) {
        document.getElementById("addCoverImagePath").value = file.name;
    }
});

document.getElementById("generateEditDescriptionBtn")?.addEventListener("click", async () => {
    const button = document.getElementById("generateEditDescriptionBtn");
    try {
        const title = document.getElementById("editTitle")?.value.trim() || "";
        const authorNames = document.getElementById("editAuthorNames")?.value.trim() || "";
        const genres = getSelectedGenres("editGenres");
        if (!title || !genres) {
            throw new Error("Title and at least one genre are required to generate description.");
        }
        setGenerateButtonState(button, true);
        const description = await generateDescription(title, authorNames, genres);
        document.getElementById("editDescription").value = description;
        showToast("Summary generated.", false);
    } catch (error) {
        showToast(error.message, true);
    } finally {
        setGenerateButtonState(button, false);
    }
});

document.getElementById("generateAddDescriptionBtn")?.addEventListener("click", async () => {
    const button = document.getElementById("generateAddDescriptionBtn");
    try {
        const title = document.getElementById("addTitle")?.value.trim() || "";
        const authorNames = document.getElementById("addAuthorNames")?.value.trim() || "";
        const genres = getSelectedGenres("addGenres");
        if (!title || !genres) {
            throw new Error("Title and at least one genre are required to generate description.");
        }
        setGenerateButtonState(button, true);
        const description = await generateDescription(title, authorNames, genres);
        document.getElementById("addDescription").value = description;
        showToast("Summary generated.", false);
    } catch (error) {
        showToast(error.message, true);
    } finally {
        setGenerateButtonState(button, false);
    }
});

document.getElementById("saveEditBtn")?.addEventListener("click", () => {
    saveEdit().catch((error) => showToast(error.message, true));
});

document.getElementById("addBookBtn")?.addEventListener("click", () => {
    addBook().catch((error) => showToast(error.message, true));
});

document.getElementById("clearEditBtn")?.addEventListener("click", () => {
    clearEditForm();
});

document.getElementById("clearAddBtn")?.addEventListener("click", () => {
    clearAddForm();
});

if (currentUser) {
    document.getElementById("bulkSelectAll")?.addEventListener("change", (event) => {
        const checked = !!event.target.checked;
        document.querySelectorAll(".bulk-row-checkbox").forEach((cb) => { cb.checked = checked; });
        updateBulkSelectionState();
    });
    document.getElementById("bulkDeleteBtn")?.addEventListener("click", () => {
        bulkDeleteSelected().catch((error) => showToast(error.message, true));
    });
    document.getElementById("bulkEditBtn")?.addEventListener("click", () => {
        bulkEditSelected().catch((error) => showToast(error.message, true));
    });
    document.getElementById("clearBulkEditBtn")?.addEventListener("click", () => {
        clearBulkEditForm();
    });
    const vhSection = document.getElementById("versionHistorySection");
    vhSection?.addEventListener("toggle", () => {
        if (vhSection.open && selectedEditBookId) {
            refreshVersionHistory(selectedEditBookId).catch(() => {});
        }
    });

    refreshAdminStats().catch(() => {});
    refreshBooks().catch((error) => {
        const status = document.getElementById("booksStatus");
        if (status) {
            status.textContent = "Failed to load published books.";
        }
        showToast(error.message, true);
    });
    consumePendingPublishedBookPrefill();
}
