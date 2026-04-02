const currentUser = requireRole("LIBRARIAN");
let submissionCoverObjectUrl = null;

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function clearCoverObjectUrl() {
    if (submissionCoverObjectUrl) {
        URL.revokeObjectURL(submissionCoverObjectUrl);
        submissionCoverObjectUrl = null;
    }
}

async function fetchProtectedBlob(url) {
    const headers = {};
    if (currentUser?.sessionId) {
        headers["X-Session-Id"] = currentUser.sessionId;
    }
    const response = await fetch(url, { headers });
    if (!response.ok) {
        throw new Error(await response.text());
    }
    return response.blob();
}

async function renderPdfPagesFromBlob(blob) {
    const container = document.getElementById("submissionPdfPages");
    if (!container || typeof pdfjsLib === "undefined") {
        return false;
    }

    if (!pdfjsLib.GlobalWorkerOptions.workerSrc) {
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.worker.min.js";
    }

    const bytes = await blob.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: bytes }).promise;
    container.innerHTML = "";

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
        container.appendChild(canvas);
    }

    container.style.display = "flex";
    return true;
}

async function loadSubmission() {
    const status = document.getElementById("readerStatus");
    const textPreview = document.getElementById("submissionTextPreview");
    const pdfPages = document.getElementById("submissionPdfPages");
    const coverImage = document.getElementById("submissionCoverImage");

    const submissionId = getQueryParam("submissionId");
    if (!submissionId) {
        throw new Error("Missing submissionId in URL.");
    }

    const payload = await api(`/api/librarian/submission/read?submissionId=${encodeURIComponent(submissionId)}`);
    status.textContent = `Reading submission: ${payload.title} by ${payload.authorFullName}`;

    if (coverImage) {
        clearCoverObjectUrl();
        coverImage.style.display = "none";
        coverImage.src = "";
        if (payload.coverImageUrl) {
            try {
                const coverBlob = await fetchProtectedBlob(payload.coverImageUrl);
                submissionCoverObjectUrl = URL.createObjectURL(coverBlob);
                coverImage.src = submissionCoverObjectUrl;
                coverImage.style.display = "block";
            } catch (_) {
                coverImage.style.display = "none";
            }
        }
    }

    if (pdfPages) {
        pdfPages.style.display = "none";
        pdfPages.innerHTML = "";
    }
    if (textPreview) {
        textPreview.style.display = "none";
        textPreview.textContent = "";
    }

    const blob = await fetchProtectedBlob(payload.fileUrl);
    if (payload.previewType === "pdf") {
        await renderPdfPagesFromBlob(blob);
        return;
    }

    if (payload.previewType === "docx") {
        if (typeof mammoth === "undefined") {
            throw new Error("DOCX preview dependency is missing.");
        }
        const html = await mammoth.convertToHtml({ arrayBuffer: await blob.arrayBuffer() });
        textPreview.innerHTML = html.value || "No readable DOCX content available.";
        textPreview.style.display = "block";
        textPreview.style.whiteSpace = "normal";
        return;
    }

    textPreview.textContent = payload.previewText || "No preview available.";
    textPreview.style.display = "block";
    textPreview.style.whiteSpace = "pre-wrap";
}

window.addEventListener("beforeunload", () => {
    clearCoverObjectUrl();
});

if (currentUser) {
    loadSubmission().catch((error) => {
        document.getElementById("readerStatus").textContent = error.message;
        showToast(error.message, true);
    });
}
