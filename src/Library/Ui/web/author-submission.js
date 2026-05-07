const currentUser = requireRole("AUTHOR");

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    const authorName = document.getElementById("authorName");
    if (authorName) {
        authorName.value = currentUser.fullName || "";
    }
    attachLogout("logoutBtn");
}

let selectedFile = null;
let selectedCoverImageFile = null;
let previewObjectUrl = null;
let coverPreviewObjectUrl = null;

const filePathInput = document.getElementById("authorFilePath");
const fileInput = document.getElementById("authorFileInput");
const coverImagePathInput = document.getElementById("authorCoverImagePath");
const coverImageInput = document.getElementById("authorCoverImageInput");
const filePreview = document.getElementById("filePreview");
const coverPreviewWrap = document.getElementById("coverPreviewWrap");
const coverPreviewImage = document.getElementById("coverPreviewImage");

function getSelectedGenres() {
    const genresSelect = document.getElementById("authorGenres");
    if (!genresSelect) {
        return "";
    }

    return Array.from(genresSelect.selectedOptions)
        .map((option) => option.value.trim())
        .filter(Boolean)
        .join(",");
}

function setSelectedGenres(values) {
    const genresSelect = document.getElementById("authorGenres");
    if (!genresSelect) {
        return;
    }

    const toGenreKey = (value) => String(value || "").trim().toLowerCase().replace(/[^a-z0-9]/g, "");
    const selectedSet = new Set((values || []).map((value) => toGenreKey(value)).filter(Boolean));
    Array.from(genresSelect.options).forEach((option) => {
        option.selected = selectedSet.has(toGenreKey(option.value));
    });
}

function clearFilePreviewUrl() {
    if (previewObjectUrl) {
        URL.revokeObjectURL(previewObjectUrl);
        previewObjectUrl = null;
    }
}

function clearCoverPreviewUrl() {
    if (coverPreviewObjectUrl) {
        URL.revokeObjectURL(coverPreviewObjectUrl);
        coverPreviewObjectUrl = null;
    }
}

function limitWords(text, maxWords) {
    if (!text) {
        return "";
    }
    const parts = text.trim().split(/\s+/);
    if (parts.length <= maxWords) {
        return text.trim();
    }
    return `${parts.slice(0, maxWords).join(" ")}...`;
}

function summaryLevelToWordLimit(level) {
    const normalized = String(level || "").trim().toLowerCase();
    if (normalized === "short") {
        return 50;
    }
    if (normalized === "detail" || normalized === "detailed") {
        return 150;
    }
    return 100;
}

async function extractPdfTextWithWordLimit(file, maxWords) {
    if (!file) {
        return "";
    }
    if (typeof pdfjsLib === "undefined") {
        throw new Error("PDF text extraction dependency is missing.");
    }

    if (!pdfjsLib.GlobalWorkerOptions.workerSrc) {
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.worker.min.js";
    }

    const bytes = await file.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: bytes }).promise;
    const totalPages = Number(pdf.numPages || 0);
    const words = [];

    for (let pageNumber = 1; pageNumber <= totalPages; pageNumber += 1) {
        const page = await pdf.getPage(pageNumber);
        const textContent = await page.getTextContent();
        const text = Array.isArray(textContent?.items)
            ? textContent.items.map((item) => item.str || "").join(" ").replace(/\s+/g, " ").trim()
            : "";
        if (!text) {
            continue;
        }
        const pageWords = text.split(/\s+/).filter(Boolean);
        for (const word of pageWords) {
            words.push(word);
            if (words.length >= maxWords) {
                return words.join(" ").trim();
            }
        }
    }

    return words.join(" ").trim();
}

async function renderPdfPagesFromFile(file) {
    if (!filePreview || typeof pdfjsLib === "undefined") {
        if (filePreview) {
            filePreview.textContent = "PDF preview dependency is missing.";
        }
        return;
    }

    if (!pdfjsLib.GlobalWorkerOptions.workerSrc) {
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.worker.min.js";
    }

    const bytes = await file.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: bytes }).promise;
    filePreview.innerHTML = "";
    filePreview.style.display = "flex";
    filePreview.style.flexDirection = "column";
    filePreview.style.gap = "10px";

    for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber += 1) {
        const page = await pdf.getPage(pageNumber);
        const viewport = page.getViewport({ scale: 1.2 });
        const canvas = document.createElement("canvas");
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = "100%";
        canvas.style.maxWidth = "100%";
        canvas.style.border = "1px solid #d9d9d9";
        canvas.style.borderRadius = "8px";
        canvas.style.background = "#fff";

        const context = canvas.getContext("2d");
        await page.render({ canvasContext: context, viewport }).promise;
        filePreview.appendChild(canvas);
    }
}

function renderCoverPreview(file) {
    if (!coverPreviewWrap || !coverPreviewImage) {
        return;
    }

    clearCoverPreviewUrl();
    coverPreviewWrap.style.display = "none";
    coverPreviewImage.removeAttribute("src");

    if (!file) {
        return;
    }

    coverPreviewObjectUrl = URL.createObjectURL(file);
    coverPreviewImage.src = coverPreviewObjectUrl;
    coverPreviewWrap.style.display = "block";
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
        await renderPdfPagesFromFile(file);
        return;
    }

    if (isImage) {
        filePreview.style.display = "block";
        previewObjectUrl = URL.createObjectURL(file);
        const image = document.createElement("img");
        image.src = previewObjectUrl;
        image.alt = file.name;
        filePreview.appendChild(image);
        return;
    }

    if (isDocx) {
        filePreview.style.display = "block";
        if (typeof mammoth === "undefined") {
            filePreview.textContent = "DOCX preview dependency not loaded.";
            return;
        }

        const arrayBuffer = await file.arrayBuffer();
        const result = await mammoth.convertToHtml({ arrayBuffer });
        filePreview.innerHTML = result.value || "No visible preview content.";
        return;
    }

    if (fileName.endsWith(".txt") || fileName.endsWith(".md") || file.type.startsWith("text/")) {
        filePreview.style.display = "block";
        const text = await file.text();
        filePreview.textContent = text.length > 4000 ? `${text.slice(0, 4000)}...` : text;
        return;
    }

    filePreview.style.display = "block";
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
            setSelectedGenres(Array.isArray(draft.genres) ? draft.genres : []);
            document.getElementById("authorDescription").value = draft.description;
            document.getElementById("authorFilePath").value = draft.filePath;
            selectedFile = null;
            fileInput.value = "";
            clearFilePreviewUrl();
            clearCoverPreviewUrl();
            if (filePreview) {
                filePreview.style.display = "block";
                filePreview.textContent = "Choose a file to preview (PDF, DOCX, JPG/JPEG/PNG).";
            }
            if (coverPreviewWrap) {
                coverPreviewWrap.style.display = "none";
            }
            if (coverPreviewImage) {
                coverPreviewImage.removeAttribute("src");
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

document.getElementById("pickCoverImageBtn")?.addEventListener("click", () => {
    coverImageInput?.click();
});

fileInput?.addEventListener("change", async () => {
    const file = fileInput.files && fileInput.files[0] ? fileInput.files[0] : null;
    if (!file) {
        selectedFile = null;
        clearFilePreviewUrl();
        if (filePreview) {
            filePreview.style.display = "block";
            filePreview.textContent = "Choose a file to preview (PDF, DOCX, JPG/JPEG/PNG).";
        }
        return;
    }

    selectedFile = file;
    if (filePathInput) {
        filePathInput.value = file.name;
    }
    showToast("File selected. Click Preview to render it.", false);
});

coverImageInput?.addEventListener("change", () => {
    const file = coverImageInput.files && coverImageInput.files[0] ? coverImageInput.files[0] : null;
    if (!file) {
        renderCoverPreview(null);
        return;
    }

    selectedCoverImageFile = file;
    if (coverImagePathInput) {
        coverImagePathInput.value = file.name;
    }
    renderCoverPreview(file);
    showToast("Cover image selected.", false);
});

document.getElementById("autoSaveBtn")?.addEventListener("click", async () => {
    try {
        const text = await api("/api/author/draft", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                title: document.getElementById("authorTitle")?.value.trim() || "",
                genres: parseGenres(getSelectedGenres()),
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

document.getElementById("generateSummaryBtn")?.addEventListener("click", async () => {
    const button = document.getElementById("generateSummaryBtn");
    const loading = document.getElementById("generateSummaryLoading");
    const summaryLengthSelect = document.getElementById("summaryLengthSelect");
    try {
        const title = document.getElementById("authorTitle")?.value.trim() || "";
        const genres = parseGenres(getSelectedGenres());
        const description = document.getElementById("authorDescription");
        let content = "";
        const summaryLevel = summaryLengthSelect?.value || "medium";
        const wordLimit = summaryLevelToWordLimit(summaryLevel);

        if (!title) {
            showToast("Enter a title before generating a summary.", true);
            return;
        }
        if (!genres) {
            showToast("Select at least one genre before generating a summary.", true);
            return;
        }

        if (selectedFile) {
            const fileName = selectedFile.name.toLowerCase();
            const isPdf = fileName.endsWith(".pdf") || selectedFile.type === "application/pdf";
            if (isPdf) {
                try {
                    content = await extractPdfTextWithWordLimit(selectedFile, wordLimit);
                } catch (error) {
                    showToast("Could not extract readable text from the PDF.", true);
                }
            }
        }

        if (button) {
            button.disabled = true;
        }
        if (loading) {
            loading.style.display = "inline-flex";
        }

        const response = await api("/api/author/submit/generate-summary", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                title,
                genres,
                note: "",
                content,
                summaryLevel
            })
        });
        console.log("[DEBUG] generate-summary response:", response);
        if (description) {
            // Prefer `summary` field; fall back to top-level string or message for debug
            description.value = (response && typeof response === 'object') ? (response.summary || response.message || "") : (response || "");
        }
        showToast(response?.message || "Summary generated. You can now submit!", false);
    } catch (error) {
        showToast(error.message, true);
    } finally {
        if (button) {
            button.disabled = false;
        }
        if (loading) {
            loading.style.display = "none";
        }
    }
});

document.getElementById("previewBtn")?.addEventListener("click", async () => {
    try {
        const preview = await api("/api/author/preview", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                title: document.getElementById("authorTitle")?.value.trim() || "",
                genres: parseGenres(getSelectedGenres()),
                description: document.getElementById("authorDescription")?.value.trim() || ""
            })
        }, false);

        const previewBox = document.getElementById("authorPreview");
        if (previewBox) {
            previewBox.textContent = preview;
        }

        if (selectedFile) {
            await renderLocalFilePreview(selectedFile);
        } else if (filePreview) {
            filePreview.style.display = "block";
            filePreview.textContent = "Choose a file to preview (PDF, DOCX, JPG/JPEG/PNG).";
        }
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("submitBtn")?.addEventListener("click", async () => {
    try {
        const title = document.getElementById("authorTitle")?.value.trim() || "";
        const genres = parseGenres(getSelectedGenres());
        const description = document.getElementById("authorDescription")?.value.trim() || "";
        const manualFilePath = document.getElementById("authorFilePath")?.value.trim() || "";

        let text;
        const manualCoverImagePath = document.getElementById("authorCoverImagePath")?.value.trim() || "";
        if (selectedFile) {
            const payload = new FormData();
            payload.append("title", title);
            payload.append("genres", genres);
            payload.append("description", description);
            payload.append("file", selectedFile, selectedFile.name);
            if (selectedCoverImageFile) {
                payload.append("coverImage", selectedCoverImageFile, selectedCoverImageFile.name);
            }

            text = await api("/api/author/submit", {
                method: "POST",
                body: payload
            }, false);
        } else {
            text = await api("/api/author/submit", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: formBody({ title, genres, description, filePath: manualFilePath, coverImagePath: manualCoverImagePath })
            }, false);
        }

        showToast(text || "Submission created successfully. Summary finalized and ready for review.", false);
        document.getElementById("authorTitle").value = "";
        setSelectedGenres([]);
        document.getElementById("authorDescription").value = "";
        document.getElementById("authorFilePath").value = "";
        document.getElementById("authorCoverImagePath").value = "";
        const previewBox = document.getElementById("authorPreview");
        if (previewBox) {
            previewBox.textContent = "";
        }
        selectedFile = null;
        selectedCoverImageFile = null;
        if (fileInput) {
            fileInput.value = "";
        }
        if (coverImageInput) {
            coverImageInput.value = "";
        }
        clearFilePreviewUrl();
        clearCoverPreviewUrl();
        if (filePreview) {
            filePreview.style.display = "block";
            filePreview.textContent = "Choose a file to preview (PDF, DOCX, JPG/JPEG/PNG).";
        }
        if (coverPreviewWrap) {
            coverPreviewWrap.style.display = "none";
        }
        if (coverPreviewImage) {
            coverPreviewImage.removeAttribute("src");
        }
        await refreshDrafts();
    } catch (error) {
        showToast(error.message, true);
    }
});

window.addEventListener("beforeunload", () => {
    clearFilePreviewUrl();
    clearCoverPreviewUrl();
});

if (currentUser) {
    refreshDrafts().catch((e) => showToast(e.message, true));
}
