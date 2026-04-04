const expectedRole = document.querySelector("main")?.dataset.role || "STAFF";
const currentUser = requireRole(expectedRole);

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

let selectedBorrowedBookId = null;
let readerFileObjectUrl = null;
let currentPdfPageCount = 0;
let activeReaderType = "text";
let readerCoverObjectUrl = null;
const PDF_DRAWING_PREFIX = "__PDF_DRAWING__=";
let drawModeEnabled = false;
let pdfDrawingStrokes = [];
let currentDrawingStroke = null;
let pageDrawCanvasMap = new Map();
let readingSessionStartedAtMs = 0;
let readingSessionBookId = "";

function formatAverageRatingFromReviews(reviews) {
    if (!Array.isArray(reviews) || reviews.length === 0) {
        return "-";
    }
    const total = reviews.reduce((sum, item) => sum + Number(item?.rating || 0), 0);
    const average = total / reviews.length;
    return `${average.toFixed(2)} (${reviews.length})`;
}

function renderReaderReviews(reviews) {
    const list = document.getElementById("readerReviewsList");
    const summary = document.getElementById("readerRatingSummary");
    if (!list || !summary) {
        return;
    }

    list.innerHTML = "";
    summary.textContent = `Average rating: ${formatAverageRatingFromReviews(reviews)}`;
    if (!Array.isArray(reviews) || reviews.length === 0) {
        const li = document.createElement("li");
        li.className = "muted";
        li.textContent = "No reviews yet.";
        list.appendChild(li);
        return;
    }

    reviews.forEach((item) => {
        const li = document.createElement("li");
        li.textContent = `${item.reviewerFullName || item.username}: ${item.rating}/5 - ${item.reviewText || ""}`;
        list.appendChild(li);
    });
}

async function loadBookReviewsAndSyncInput(bookId) {
    try {
        const reviews = await api(`/api/reviews?bookId=${encodeURIComponent(bookId)}`);
        renderReaderReviews(reviews);

        const currentReview = Array.isArray(reviews)
            ? reviews.find((item) => item.username === currentUser?.username)
            : null;
        const ratingSelect = document.getElementById("reviewRating");
        const reviewTextInput = document.getElementById("reviewTextInput");
        if (ratingSelect) {
            ratingSelect.value = String(currentReview?.rating || 5);
        }
        if (reviewTextInput) {
            reviewTextInput.value = currentReview?.reviewText || "";
        }
    } catch (_) {
        renderReaderReviews([]);
    }
}

function startReadingTimer(bookId) {
    readingSessionBookId = String(bookId || "");
    readingSessionStartedAtMs = Date.now();
}

async function flushReadingTimer(reason = "") {
    if (!readingSessionBookId || readingSessionStartedAtMs <= 0) {
        return;
    }

    const elapsedMs = Date.now() - readingSessionStartedAtMs;
    const seconds = Math.max(0, Math.round(elapsedMs / 1000));
    const bookId = readingSessionBookId;

    readingSessionBookId = "";
    readingSessionStartedAtMs = 0;

    if (seconds <= 0) {
        return;
    }

    await api("/api/reading-progress/time", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ bookId, seconds, reason })
    });
}

function flushReadingTimerOnUnload() {
    if (!readingSessionBookId || readingSessionStartedAtMs <= 0) {
        return;
    }

    const elapsedMs = Date.now() - readingSessionStartedAtMs;
    const seconds = Math.max(0, Math.round(elapsedMs / 1000));
    const bookId = readingSessionBookId;

    readingSessionBookId = "";
    readingSessionStartedAtMs = 0;

    if (seconds <= 0) {
        return;
    }

    const params = new URLSearchParams();
    params.set("bookId", bookId);
    params.set("seconds", String(seconds));
    params.set("reason", "unload");

    const headers = { "Content-Type": "application/x-www-form-urlencoded" };
    if (currentUser?.sessionId) {
        headers["X-Session-Id"] = currentUser.sessionId;
    }

    fetch("/api/reading-progress/time", {
        method: "POST",
        headers,
        body: params,
        keepalive: true
    }).catch(() => {});
}

function encodeDrawingPayload(strokes) {
    const compact = {
        v: 1,
        s: (Array.isArray(strokes) ? strokes : []).map((stroke) => ({
            p: Number(stroke.p || 0),
            w: Number(stroke.w || 14),
            c: String(stroke.c || "rgba(255, 235, 59, 0.35)"),
            pts: Array.isArray(stroke.pts)
                ? stroke.pts.map((point) => [
                    Number(Number(point[0] || 0).toFixed(4)),
                    Number(Number(point[1] || 0).toFixed(4))
                ])
                : []
        }))
    };
    const json = JSON.stringify(compact);
    return PDF_DRAWING_PREFIX + btoa(unescape(encodeURIComponent(json)));
}

function decodeDrawingPayload(line) {
    if (!line || !line.startsWith(PDF_DRAWING_PREFIX)) {
        return [];
    }

    try {
        const encoded = line.substring(PDF_DRAWING_PREFIX.length);
        const decoded = decodeURIComponent(escape(atob(encoded)));
        const payload = JSON.parse(decoded);
        if (!payload || !Array.isArray(payload.s)) {
            return [];
        }

        return payload.s
            .map((stroke) => ({
                p: Number(stroke.p || 0),
                w: Number(stroke.w || 14),
                c: String(stroke.c || "rgba(255, 235, 59, 0.35)"),
                pts: Array.isArray(stroke.pts)
                    ? stroke.pts
                        .map((point) => [Number(point[0]), Number(point[1])])
                        .filter((point) => Number.isFinite(point[0]) && Number.isFinite(point[1]))
                    : []
            }))
            .filter((stroke) => stroke.p > 0 && stroke.pts.length > 0);
    } catch (_) {
        return [];
    }
}

function splitHighlightsAndDrawings(lines) {
    const safeLines = Array.isArray(lines) ? lines : [];
    const userLines = [];
    let drawingStrokes = [];

    safeLines.forEach((line) => {
        if (typeof line !== "string") {
            return;
        }
        if (line.startsWith(PDF_DRAWING_PREFIX)) {
            drawingStrokes = decodeDrawingPayload(line);
            return;
        }
        userLines.push(line);
    });

    return { userLines, drawingStrokes };
}

function buildHighlightsPayloadForSave() {
    const inputValue = document.getElementById("highlightsInput")?.value || "";
    const userLines = inputValue
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean);

    if (pdfDrawingStrokes.length > 0) {
        userLines.push(encodeDrawingPayload(pdfDrawingStrokes));
    }
    return userLines.join("\n");
}

function updateDrawModeUi() {
    const toggleBtn = document.getElementById("togglePdfDrawModeBtn");
    const hint = document.getElementById("pdfDrawHint");
    if (toggleBtn) {
        toggleBtn.textContent = drawModeEnabled ? "Drawing Mode: ON" : "Drawing Mode: OFF";
    }
    if (hint) {
        hint.textContent = drawModeEnabled
            ? "Drag on PDF to paint highlight strokes."
            : "Turn on drawing mode, then drag on PDF to highlight.";
    }

    pageDrawCanvasMap.forEach((canvas) => {
        canvas.style.pointerEvents = drawModeEnabled ? "auto" : "none";
        canvas.style.cursor = drawModeEnabled ? "crosshair" : "default";
    });
}

function normalizedPointFromEvent(canvas, event) {
    const rect = canvas.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
        return [0, 0];
    }
    const x = Math.min(Math.max(0, event.clientX - rect.left), rect.width);
    const y = Math.min(Math.max(0, event.clientY - rect.top), rect.height);
    return [x / rect.width, y / rect.height];
}

function drawStrokeOnCanvas(canvas, stroke) {
    const ctx = canvas.getContext("2d");
    if (!ctx) {
        return;
    }

    const points = Array.isArray(stroke.pts) ? stroke.pts : [];
    if (points.length === 0) {
        return;
    }

    ctx.strokeStyle = stroke.c || "rgba(255, 235, 59, 0.35)";
    ctx.fillStyle = stroke.c || "rgba(255, 235, 59, 0.35)";
    ctx.lineWidth = Number(stroke.w || 14);
    ctx.lineCap = "round";
    ctx.lineJoin = "round";

    if (points.length === 1) {
        const x = points[0][0] * canvas.width;
        const y = points[0][1] * canvas.height;
        ctx.beginPath();
        ctx.arc(x, y, Math.max(2, ctx.lineWidth / 2), 0, Math.PI * 2);
        ctx.fill();
        return;
    }

    ctx.beginPath();
    ctx.moveTo(points[0][0] * canvas.width, points[0][1] * canvas.height);
    for (let i = 1; i < points.length; i += 1) {
        ctx.lineTo(points[i][0] * canvas.width, points[i][1] * canvas.height);
    }
    ctx.stroke();
}

function redrawPdfDrawingStrokes() {
    pageDrawCanvasMap.forEach((canvas) => {
        const ctx = canvas.getContext("2d");
        if (ctx) {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
        }
    });

    pdfDrawingStrokes.forEach((stroke) => {
        const canvas = pageDrawCanvasMap.get(Number(stroke.p));
        if (!canvas) {
            return;
        }
        drawStrokeOnCanvas(canvas, stroke);
    });
}

function bindDrawCanvas(pageNumber, drawCanvas) {
    pageDrawCanvasMap.set(pageNumber, drawCanvas);

    drawCanvas.addEventListener("pointerdown", (event) => {
        if (!drawModeEnabled) {
            return;
        }
        event.preventDefault();
        drawCanvas.setPointerCapture(event.pointerId);
        const point = normalizedPointFromEvent(drawCanvas, event);
        currentDrawingStroke = {
            p: pageNumber,
            w: 16,
            c: "rgba(255, 235, 59, 0.35)",
            pts: [point]
        };
        pdfDrawingStrokes.push(currentDrawingStroke);
        redrawPdfDrawingStrokes();
    });

    drawCanvas.addEventListener("pointermove", (event) => {
        if (!drawModeEnabled || !currentDrawingStroke || currentDrawingStroke.p !== pageNumber) {
            return;
        }
        event.preventDefault();
        currentDrawingStroke.pts.push(normalizedPointFromEvent(drawCanvas, event));
        redrawPdfDrawingStrokes();
    });

    const endStroke = () => {
        currentDrawingStroke = null;
    };
    drawCanvas.addEventListener("pointerup", endStroke);
    drawCanvas.addEventListener("pointercancel", endStroke);
    drawCanvas.addEventListener("pointerleave", () => {
        if (currentDrawingStroke && currentDrawingStroke.p === pageNumber) {
            currentDrawingStroke = null;
        }
    });
}

function collectHighlightTerms() {
    const raw = document.getElementById("highlightsInput")?.value || "";
    return raw
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean);
}

function applyPdfTextHighlights() {
    const readerPdfPages = document.getElementById("readerPdfPages");
    if (!readerPdfPages) {
        return;
    }

    const terms = collectHighlightTerms();
    const spans = readerPdfPages.querySelectorAll(".pdf-text-layer span");
    spans.forEach((span) => {
        const text = (span.textContent || "").trim().toLowerCase();
        const matched = text.length > 0 && terms.some((term) => text.includes(term.toLowerCase()));
        span.classList.toggle("pdf-text-highlight", matched);
    });
}

function appendSelectedPdfTextToHighlights() {
    if (activeReaderType !== "pdf") {
        showToast("Open a PDF first, then select text to highlight.", true);
        return;
    }

    const selection = window.getSelection();
    const selected = (selection?.toString() || "").replace(/\s+/g, " ").trim();
    if (!selected) {
        showToast("Please select some PDF text first.", true);
        return;
    }

    const readerPdfPages = document.getElementById("readerPdfPages");
    const anchorNode = selection?.anchorNode || null;
    if (!readerPdfPages || !anchorNode || !readerPdfPages.contains(anchorNode)) {
        showToast("Please select text inside the PDF preview area.", true);
        return;
    }

    const highlightsInput = document.getElementById("highlightsInput");
    if (!highlightsInput) {
        return;
    }

    const lines = collectHighlightTerms();
    if (!lines.includes(selected)) {
        highlightsInput.value = lines.length > 0
            ? `${lines.join("\n")}\n${selected}`
            : selected;
    }

    applyPdfTextHighlights();
    selection.removeAllRanges();
    showToast("Selected PDF text added to highlights.", false);
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

function clearReaderCoverObjectUrl() {
    if (readerCoverObjectUrl) {
        URL.revokeObjectURL(readerCoverObjectUrl);
        readerCoverObjectUrl = null;
    }
}

async function loadBookCover(bookId) {
    const coverImage = document.getElementById("readerCoverImage");
    if (!coverImage) {
        return;
    }

    clearReaderCoverObjectUrl();
    coverImage.style.display = "none";
    coverImage.src = "";

    try {
        const summary = await api(`/api/books/summary?bookId=${encodeURIComponent(bookId)}`);
        if (!summary.coverImageUrl) {
            return;
        }

        const blob = await fetchProtectedBlob(summary.coverImageUrl);
        readerCoverObjectUrl = URL.createObjectURL(blob);
        coverImage.src = readerCoverObjectUrl;
        coverImage.style.display = "block";
    } catch (_) {
        coverImage.style.display = "none";
        coverImage.src = "";
    }
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

function clearReaderObjectUrl() {
    if (readerFileObjectUrl) {
        URL.revokeObjectURL(readerFileObjectUrl);
        readerFileObjectUrl = null;
    }
}

async function renderPdfPagesFromBlob(blob) {
    const pagesContainer = document.getElementById("readerPdfPages");
    if (!pagesContainer || typeof pdfjsLib === "undefined") {
        return 0;
    }

    if (!pdfjsLib.GlobalWorkerOptions.workerSrc) {
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.worker.min.js";
    }

    const bytes = await blob.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: bytes }).promise;
    pagesContainer.innerHTML = "";
    pageDrawCanvasMap = new Map();

    for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber += 1) {
        const page = await pdf.getPage(pageNumber);
        const viewport = page.getViewport({ scale: 1.2 });

        const pageWrap = document.createElement("div");
        pageWrap.style.position = "relative";
        pageWrap.style.width = "100%";
        pageWrap.style.background = "#fff";
        pageWrap.style.border = "1px solid #d9d9d9";
        pageWrap.style.borderRadius = "8px";
        pageWrap.style.overflow = "hidden";

        const canvas = document.createElement("canvas");
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = "100%";
        canvas.style.maxWidth = "100%";
        canvas.style.background = "#fff";
        canvas.style.display = "block";

        const context = canvas.getContext("2d");
        await page.render({ canvasContext: context, viewport }).promise;

        const textLayer = document.createElement("div");
        textLayer.className = "pdf-text-layer";
        textLayer.style.position = "absolute";
        textLayer.style.left = "0";
        textLayer.style.top = "0";
        textLayer.style.right = "0";
        textLayer.style.bottom = "0";
        textLayer.style.userSelect = "text";
        textLayer.style.webkitUserSelect = "text";
        textLayer.style.cursor = "text";
        textLayer.style.lineHeight = "1";
        textLayer.style.color = "transparent";

        const textContent = await page.getTextContent();
        const textTask = pdfjsLib.renderTextLayer({
            textContent,
            container: textLayer,
            viewport,
            textDivs: []
        });
        if (textTask?.promise) {
            await textTask.promise;
        }

        const drawCanvas = document.createElement("canvas");
        drawCanvas.width = viewport.width;
        drawCanvas.height = viewport.height;
        drawCanvas.style.position = "absolute";
        drawCanvas.style.left = "0";
        drawCanvas.style.top = "0";
        drawCanvas.style.width = "100%";
        drawCanvas.style.height = "100%";
        drawCanvas.style.zIndex = "2";
        drawCanvas.style.pointerEvents = "none";
        drawCanvas.style.touchAction = "none";

        bindDrawCanvas(pageNumber, drawCanvas);

        pageWrap.appendChild(canvas);
        pageWrap.appendChild(textLayer);
        pageWrap.appendChild(drawCanvas);
        pagesContainer.appendChild(pageWrap);
    }

    pagesContainer.style.display = "flex";
    redrawPdfDrawingStrokes();
    updateDrawModeUi();
    applyPdfTextHighlights();
    return Number(pdf.numPages || 0);
}

function resetReaderUi(statusText) {
    const status = document.getElementById("readerStatus");
    const readerPdf = document.getElementById("readerPdf");
    const readerPdfPages = document.getElementById("readerPdfPages");
    const readerText = document.getElementById("readerText");

    clearReaderObjectUrl();
    if (status) {
        status.textContent = statusText;
    }
    if (readerPdf) {
        readerPdf.style.display = "none";
        readerPdf.src = "";
    }
    if (readerPdfPages) {
        readerPdfPages.style.display = "none";
        readerPdfPages.innerHTML = "";
    }
    if (readerText) {
        readerText.style.display = "none";
        readerText.textContent = "";
    }
    activeReaderType = "text";
    clearReaderCoverObjectUrl();
    const coverImage = document.getElementById("readerCoverImage");
    if (coverImage) {
        coverImage.style.display = "none";
        coverImage.src = "";
    }

    const bookmark = document.getElementById("bookmarkPage");
    const highlights = document.getElementById("highlightsInput");
    applyBookmarkControlMode(0, 1);
    if (highlights) {
        highlights.value = "";
    }
    pdfDrawingStrokes = [];
    currentDrawingStroke = null;
    pageDrawCanvasMap = new Map();
    drawModeEnabled = false;
    updateDrawModeUi();
    renderReaderReviews([]);
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
        readerFileObjectUrl = URL.createObjectURL(blob);
        const pageCount = await renderPdfPagesFromBlob(blob);
        applyBookmarkControlMode(pageCount, 1);

        if (readerText) {
            readerText.style.display = "none";
            readerText.textContent = "";
        }
        if (readerPdf) {
            readerPdf.style.display = "none";
            readerPdf.src = "";
        }
        return;
    }

    if (payload.type === "docx") {
        activeReaderType = "docx";
        applyBookmarkControlMode(0, 1);
        if (readerPdf) {
            readerPdf.style.display = "none";
            readerPdf.src = "";
        }
        if (readerText) {
            readerText.style.display = "block";
            readerText.style.whiteSpace = "normal";
        }

        if (typeof mammoth === "undefined") {
            if (readerText) {
                readerText.textContent = "DOCX preview dependency is missing.";
            }
            return;
        }

        const response = await fetch(payload.url, { headers });
        if (!response.ok) {
            throw new Error("Failed to load DOCX preview.");
        }

        const arrayBuffer = await response.arrayBuffer();
        const result = await mammoth.convertToHtml({ arrayBuffer });
        if (readerText) {
            readerText.innerHTML = result.value || "No readable DOCX content available.";
        }
        return;
    }

    activeReaderType = "text";
    applyBookmarkControlMode(0, 1);
    if (readerPdf) {
        readerPdf.style.display = "none";
        readerPdf.src = "";
    }
    if (readerText) {
        readerText.style.display = "block";
        readerText.style.whiteSpace = "pre-wrap";
        readerText.textContent = payload.content || "No content available.";
    }
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
        const readerPdfPages = document.getElementById("readerPdfPages");
        if (readerPdfPages && readerPdfPages.children.length > 0) {
            const targetIndex = Math.max(1, preferred) - 1;
            const target = readerPdfPages.children[Math.min(targetIndex, readerPdfPages.children.length - 1)];
            target?.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    }
    const highlights = document.getElementById("highlightsInput");
    const parsed = splitHighlightsAndDrawings(progress.highlights);
    pdfDrawingStrokes = parsed.drawingStrokes;
    if (highlights) {
        highlights.value = parsed.userLines.join("\n");
    }
    redrawPdfDrawingStrokes();
    updateDrawModeUi();
    applyPdfTextHighlights();
}

async function refreshBorrows(autoBookId = "") {
    const list = document.getElementById("borrows");
    if (!list) {
        return;
    }

    const items = await api("/api/borrows?status=active&sortBy=dueDate&sortDir=asc");
    list.innerHTML = "";

    if (!Array.isArray(items) || items.length === 0) {
        const li = document.createElement("li");
        li.className = "muted";
        li.textContent = "No active borrows to read.";
        list.appendChild(li);
        resetReaderUi("No active borrowed book selected.");
        return;
    }

    for (const item of items) {
        const li = document.createElement("li");
        li.innerHTML = `
            <div class="borrow-item-row">
                <span>${item.bookTitle} (due ${item.dueDate})</span>
                <div class="borrow-item-actions">
                    <button class="secondary" type="button">Read</button>
                </div>
            </div>
        `;

        li.querySelector("button")?.addEventListener("click", async () => {
            try {
                await flushReadingTimer("switch-book");
                selectedBorrowedBookId = item.bookId;
                const status = document.getElementById("readerStatus");
                if (status) {
                    status.textContent = `Reading: ${item.bookTitle}`;
                }
                await loadBookCover(item.bookId);
                await loadBorrowedContent(item.bookId);
                await loadReadingProgress(item.bookId);
                await loadBookReviewsAndSyncInput(item.bookId);
                startReadingTimer(item.bookId);
            } catch (error) {
                showToast(error.message, true);
            }
        });

        list.appendChild(li);
    }

    if (autoBookId) {
        const target = items.find((it) => String(it.bookId) === String(autoBookId));
        if (target) {
            await flushReadingTimer("switch-book");
            selectedBorrowedBookId = target.bookId;
            const status = document.getElementById("readerStatus");
            if (status) {
                status.textContent = `Reading: ${target.bookTitle}`;
            }
            await loadBookCover(target.bookId);
            await loadBorrowedContent(target.bookId);
            await loadReadingProgress(target.bookId);
            await loadBookReviewsAndSyncInput(target.bookId);
            startReadingTimer(target.bookId);
        }
    }
}

document.getElementById("saveReviewBtn")?.addEventListener("click", async () => {
    try {
        if (!selectedBorrowedBookId) {
            showToast("Please click Read on a borrowed book first.", true);
            return;
        }

        const rating = Number(document.getElementById("reviewRating")?.value || "5");
        const reviewText = document.getElementById("reviewTextInput")?.value || "";
        await api("/api/reviews/submit", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                bookId: selectedBorrowedBookId,
                rating,
                reviewText
            })
        });
        await loadBookReviewsAndSyncInput(selectedBorrowedBookId);
        showToast("Review saved.", false);
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("saveProgressBtn")?.addEventListener("click", async () => {
    try {
        if (!selectedBorrowedBookId) {
            showToast("Please click Read on a borrowed book first.", true);
            return;
        }

        const bookmark = getSelectedBookmarkPage();
        const highlights = buildHighlightsPayloadForSave();

        await flushReadingTimer("save-progress");

        await api("/api/reading-progress", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                bookId: selectedBorrowedBookId,
                bookmark,
                highlights
            })
        });
        startReadingTimer(selectedBorrowedBookId);
        showToast("Reading progress saved.", false);
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("addPdfHighlightBtn")?.addEventListener("click", () => {
    appendSelectedPdfTextToHighlights();
});

document.getElementById("applyPdfHighlightsBtn")?.addEventListener("click", () => {
    applyPdfTextHighlights();
    showToast("Saved highlight marks applied to PDF view.", false);
});

document.getElementById("highlightsInput")?.addEventListener("input", () => {
    applyPdfTextHighlights();
});

document.getElementById("togglePdfDrawModeBtn")?.addEventListener("click", () => {
    if (activeReaderType !== "pdf") {
        showToast("Open a PDF first to draw highlights.", true);
        return;
    }
    drawModeEnabled = !drawModeEnabled;
    updateDrawModeUi();
});

document.getElementById("undoPdfDrawBtn")?.addEventListener("click", () => {
    if (pdfDrawingStrokes.length === 0) {
        showToast("No drawing highlight to undo.", true);
        return;
    }
    pdfDrawingStrokes.pop();
    redrawPdfDrawingStrokes();
    showToast("Last drawing highlight removed.", false);
});

document.getElementById("clearPdfDrawBtn")?.addEventListener("click", () => {
    pdfDrawingStrokes = [];
    redrawPdfDrawingStrokes();
    showToast("All drawing highlights cleared from view.", false);
});

window.addEventListener("beforeunload", () => {
    flushReadingTimerOnUnload();
    clearReaderObjectUrl();
    clearReaderCoverObjectUrl();
});

if (currentUser) {
    const params = new URLSearchParams(window.location.search);
    const autoBookId = params.get("bookId") || "";
    refreshBorrows(autoBookId).catch((e) => showToast(e.message, true));
}
