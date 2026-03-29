const currentUser = requireRole("AUTHOR");
if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let selectedFile = null;
let previewObjectUrl = null;

const filePathInput = document.getElementById("authorFilePath");
const fileInput = document.getElementById("authorFileInput");
const filePreview = document.getElementById("filePreview");

function clearFilePreviewUrl() {
    if (previewObjectUrl) {
        URL.revokeObjectURL(previewObjectUrl);
        previewObjectUrl = null;
    }
}

async function loadAuthorProfile() {
    const payload = await api("/api/author/profile");
    document.getElementById("authorProfileFullName").value = payload.fullName || "";
    document.getElementById("authorProfileBio").value = payload.bio || "";
}

async function saveAuthorProfile() {
    const feedback = document.getElementById("authorProfileFeedback");
    const fullName = document.getElementById("authorProfileFullName").value.trim();
    const bio = document.getElementById("authorProfileBio").value.trim();
    const password = document.getElementById("authorProfilePassword").value;

    if (!fullName) {
        feedback.textContent = "Full Name cannot be empty.";
        showToast(feedback.textContent, true);
        return;
    }
    if (!bio) {
        feedback.textContent = "Bio cannot be empty.";
        showToast(feedback.textContent, true);
        return;
    }

    if (password.trim()) {
        const issues = getPasswordPolicyViolations(password);
        if (issues.length > 0) {
            feedback.textContent = issues[0];
            showToast(feedback.textContent, true);
            return;
        }
    }

    const text = await api("/api/author/profile", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ fullName, bio, password })
    }, false);

    feedback.textContent = text;
    document.getElementById("authorProfilePassword").value = "";

    if (currentUser) {
        currentUser.fullName = fullName;
        saveCurrentUser(currentUser);
        document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    showToast(text, false);
}

async function renderLocalFilePreview(file) {
    clearFilePreviewUrl();
    filePreview.innerHTML = "";

    const fileName = file.name.toLowerCase();
    const isPdf = fileName.endsWith(".pdf") || file.type === "application/pdf";
    const isImage = /\.(jpg|jpeg|png)$/i.test(fileName) || file.type.startsWith("image/");
    const isDocx = fileName.endsWith(".docx") || file.type.includes("wordprocessingml");

    if (isPdf) {
        previewObjectUrl = URL.createObjectURL(file);
        const iframe = document.createElement("iframe");
        iframe.src = previewObjectUrl;
        iframe.title = "PDF Preview";
        filePreview.appendChild(iframe);
        return;
    }

    if (isImage) {
        previewObjectUrl = URL.createObjectURL(file);
        const image = document.createElement("img");
        image.src = previewObjectUrl;
        image.alt = file.name;
        filePreview.appendChild(image);
        return;
    }

    if (isDocx) {
        if (typeof mammoth === "undefined") {
            filePreview.textContent = "DOCX preview dependency not loaded.";
            return;
        }

        const arrayBuffer = await file.arrayBuffer();
        const result = await mammoth.convertToHtml({ arrayBuffer });
        filePreview.innerHTML = result.value || "No visible preview content.";
        return;
    }

    filePreview.textContent = "Preview is supported for PDF, DOCX, JPG, JPEG, and PNG files.";
}

document.getElementById("pickFileBtn").addEventListener("click", () => {
    fileInput.click();
});

fileInput.addEventListener("change", async () => {
    const file = fileInput.files && fileInput.files[0] ? fileInput.files[0] : null;
    if (!file) {
        return;
    }

    selectedFile = file;
    filePathInput.value = file.name;

    try {
        await renderLocalFilePreview(file);
        showToast("File selected for preview.", false);
    } catch (error) {
        filePreview.textContent = "Failed to render local preview.";
        showToast(error.message || "Preview failed.", true);
    }
});

async function refreshDrafts() {
    const list = document.getElementById("drafts");
    const items = await api("/api/author/drafts");
    list.innerHTML = "";

    items.forEach((draft) => {
        const li = document.createElement("li");
        const button = document.createElement("button");
        button.className = "secondary";
        button.textContent = `Load: ${draft.title}`;
        button.addEventListener("click", () => {
            document.getElementById("authorTitle").value = draft.title;
            document.getElementById("authorGenres").value = draft.genres.join(", ");
            document.getElementById("authorDescription").value = draft.description;
            document.getElementById("authorFilePath").value = draft.filePath;
            selectedFile = null;
            fileInput.value = "";
            clearFilePreviewUrl();
            filePreview.textContent = "Choose a file to preview (PDF, DOCX, JPG/JPEG/PNG).";
            showToast("Draft loaded.", false);
        });
        li.appendChild(button);
        list.appendChild(li);
    });
}

async function refreshPublishedBooks() {
    const status = document.getElementById("publishedStatus");
    const body = document.getElementById("publishedBooksBody");
    if (!status || !body) {
        return;
    }

    const items = await api("/api/author/published");
    body.innerHTML = "";

    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No published books yet.";
        return;
    }

    status.textContent = `Found ${items.length} published book(s).`;
    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${item.id}</td>
            <td>${item.title}</td>
            <td>${item.summary || ""}</td>
            <td>${item.publishDate || ""}</td>
            <td>${item.status}</td>
        `;
        body.appendChild(row);
    });
}

document.getElementById("autoSaveBtn").addEventListener("click", async () => {
    try {
        const text = await api("/api/author/draft", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                title: document.getElementById("authorTitle").value.trim(),
                genres: parseGenres(document.getElementById("authorGenres").value),
                description: document.getElementById("authorDescription").value.trim(),
                filePath: document.getElementById("authorFilePath").value.trim()
            })
        }, false);
        showToast(text, false);
        await refreshDrafts();
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("loadDraftsBtn").addEventListener("click", () => {
    refreshDrafts().catch((e) => showToast(e.message, true));
});

document.getElementById("loadPublishedBtn")?.addEventListener("click", () => {
    refreshPublishedBooks().catch((e) => {
        const status = document.getElementById("publishedStatus");
        if (status) {
            status.textContent = "Failed to load published books.";
        }
        showToast(e.message, true);
    });
});

document.getElementById("saveAuthorProfileBtn")?.addEventListener("click", () => {
    saveAuthorProfile().catch((e) => {
        const feedback = document.getElementById("authorProfileFeedback");
        if (feedback) {
            feedback.textContent = e.message;
        }
        showToast(e.message, true);
    });
});

document.getElementById("previewBtn").addEventListener("click", async () => {
    try {
        const preview = await api("/api/author/preview", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                title: document.getElementById("authorTitle").value.trim(),
                genres: parseGenres(document.getElementById("authorGenres").value),
                description: document.getElementById("authorDescription").value.trim()
            })
        }, false);

        document.getElementById("authorPreview").textContent = preview;
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("submitBtn").addEventListener("click", async () => {
    try {
        const title = document.getElementById("authorTitle").value.trim();
        const genres = parseGenres(document.getElementById("authorGenres").value);
        const description = document.getElementById("authorDescription").value.trim();
        const manualFilePath = document.getElementById("authorFilePath").value.trim();

        let text;
        if (selectedFile) {
            const payload = new FormData();
            payload.append("title", title);
            payload.append("genres", genres);
            payload.append("description", description);
            payload.append("file", selectedFile, selectedFile.name);

            text = await api("/api/author/submit", {
                method: "POST",
                body: payload
            }, false);
        } else {
            text = await api("/api/author/submit", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: formBody({ title, genres, description, filePath: manualFilePath })
            }, false);
        }

        showToast(text, false);
    } catch (error) {
        showToast(error.message, true);
    }
});

if (currentUser) {
    loadAuthorProfile().catch((e) => {
        const feedback = document.getElementById("authorProfileFeedback");
        if (feedback) {
            feedback.textContent = "Failed to load author profile.";
        }
        showToast(e.message, true);
    });
    refreshDrafts().catch((e) => showToast(e.message, true));
    refreshPublishedBooks().catch((e) => {
        const status = document.getElementById("publishedStatus");
        if (status) {
            status.textContent = "Failed to load published books.";
        }
        showToast(e.message, true);
    });
}
