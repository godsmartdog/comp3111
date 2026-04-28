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
        row.innerHTML = '<td colspan="6" class="muted">No published books found.</td>';
        body.appendChild(row);
        return;
    }

    status.textContent = `Found ${items.length} published book(s).`;
    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${escapeHtml(item.title || "")}</td>
            <td>${escapeHtml(item.author || "")}</td>
            <td>${Array.isArray(item.genres) ? escapeHtml(item.genres.join(", ")) : ""}</td>
            <td>${escapeHtml(item.publishDate || "")}</td>
            <td>${Number(item.availableCopies || 0)}/${Number(item.totalCopies || 0)}</td>
            <td><button class="secondary" type="button">Edit</button></td>
        `;

        row.querySelector("button")?.addEventListener("click", () => {
            loadBookForEdit(item.id);
        });
        body.appendChild(row);
    });
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

    const items = await api("/api/librarian/approved-books");
    publishedBooks = Array.isArray(items) ? items : [];
    const keyword = document.getElementById("bookKeyword")?.value.trim() || "";
    renderPublishedBooks(filterBooksByKeyword(publishedBooks, keyword));
}

async function generateDescription(title, authorNames, genres) {
    const response = await api("/api/librarian/published-book/generate-description", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ title, authorNames, genres })
    });
    return response.description || "";
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
}

document.getElementById("searchBooksBtn")?.addEventListener("click", () => {
    const keyword = document.getElementById("bookKeyword")?.value.trim() || "";
    renderPublishedBooks(filterBooksByKeyword(publishedBooks, keyword));
});

document.getElementById("refreshBooksBtn")?.addEventListener("click", () => {
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
    try {
        const title = document.getElementById("editTitle")?.value.trim() || "";
        const authorNames = document.getElementById("editAuthorNames")?.value.trim() || "";
        const genres = getSelectedGenres("editGenres");
        if (!title || !genres) {
            throw new Error("Title and at least one genre are required to generate description.");
        }
        const description = await generateDescription(title, authorNames, genres);
        document.getElementById("editDescription").value = description;
        showToast("Description generated.", false);
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("generateAddDescriptionBtn")?.addEventListener("click", async () => {
    try {
        const title = document.getElementById("addTitle")?.value.trim() || "";
        const authorNames = document.getElementById("addAuthorNames")?.value.trim() || "";
        const genres = getSelectedGenres("addGenres");
        if (!title || !genres) {
            throw new Error("Title and at least one genre are required to generate description.");
        }
        const description = await generateDescription(title, authorNames, genres);
        document.getElementById("addDescription").value = description;
        showToast("Description generated.", false);
    } catch (error) {
        showToast(error.message, true);
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
    refreshBooks().catch((error) => {
        const status = document.getElementById("booksStatus");
        if (status) {
            status.textContent = "Failed to load published books.";
        }
        showToast(error.message, true);
    });
}
