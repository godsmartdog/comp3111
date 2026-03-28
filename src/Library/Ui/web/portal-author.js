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
    refreshDrafts().catch((e) => showToast(e.message, true));
}
