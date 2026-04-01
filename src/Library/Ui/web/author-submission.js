const currentUser = requireRole("AUTHOR");

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
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
    if (!filePreview) {
        return;
    }

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

async function refreshDrafts() {
    const list = document.getElementById("drafts");
    if (!list || !fileInput) {
        return;
    }

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
            if (filePreview) {
                filePreview.textContent = "Choose a file to preview (PDF, DOCX, JPG/JPEG/PNG).";
            }
            showToast("Draft loaded.", false);
        });
        li.appendChild(button);
        list.appendChild(li);
    });
}

document.getElementById("pickFileBtn")?.addEventListener("click", () => {
    fileInput?.click();
});

fileInput?.addEventListener("change", async () => {
    const file = fileInput.files && fileInput.files[0] ? fileInput.files[0] : null;
    if (!file) {
        return;
    }

    selectedFile = file;
    if (filePathInput) {
        filePathInput.value = file.name;
    }

    try {
        await renderLocalFilePreview(file);
        showToast("File selected for preview.", false);
    } catch (error) {
        if (filePreview) {
            filePreview.textContent = "Failed to render local preview.";
        }
        showToast(error.message || "Preview failed.", true);
    }
});

document.getElementById("autoSaveBtn")?.addEventListener("click", async () => {
    try {
        const text = await api("/api/author/draft", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                title: document.getElementById("authorTitle")?.value.trim() || "",
                genres: parseGenres(document.getElementById("authorGenres")?.value || ""),
                description: document.getElementById("authorDescription")?.value.trim() || "",
                filePath: document.getElementById("authorFilePath")?.value.trim() || ""
            })
        }, false);
        showToast(text, false);
        await refreshDrafts();
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("loadDraftsBtn")?.addEventListener("click", () => {
    refreshDrafts().catch((e) => showToast(e.message, true));
});

document.getElementById("previewBtn")?.addEventListener("click", async () => {
    try {
        const preview = await api("/api/author/preview", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                title: document.getElementById("authorTitle")?.value.trim() || "",
                genres: parseGenres(document.getElementById("authorGenres")?.value || ""),
                description: document.getElementById("authorDescription")?.value.trim() || ""
            })
        }, false);

        const previewBox = document.getElementById("authorPreview");
        if (previewBox) {
            previewBox.textContent = preview;
        }
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("submitBtn")?.addEventListener("click", async () => {
    try {
        const title = document.getElementById("authorTitle")?.value.trim() || "";
        const genres = parseGenres(document.getElementById("authorGenres")?.value || "");
        const description = document.getElementById("authorDescription")?.value.trim() || "";
        const manualFilePath = document.getElementById("authorFilePath")?.value.trim() || "";

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
        document.getElementById("authorTitle").value = "";
        document.getElementById("authorGenres").value = "";
        document.getElementById("authorDescription").value = "";
        document.getElementById("authorFilePath").value = "";
        const previewBox = document.getElementById("authorPreview");
        if (previewBox) {
            previewBox.textContent = "";
        }
        selectedFile = null;
        if (fileInput) {
            fileInput.value = "";
        }
        clearFilePreviewUrl();
        if (filePreview) {
            filePreview.textContent = "Choose a file to preview (PDF, DOCX, JPG/JPEG/PNG).";
        }
        await refreshDrafts();
    } catch (error) {
        showToast(error.message, true);
    }
});

if (currentUser) {
    refreshDrafts().catch((e) => showToast(e.message, true));
}
