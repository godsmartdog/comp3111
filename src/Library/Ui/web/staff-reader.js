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

function resetReaderUi(statusText) {
    const status = document.getElementById("readerStatus");
    const readerPdf = document.getElementById("readerPdf");
    const readerText = document.getElementById("readerText");

    clearReaderObjectUrl();
    if (status) {
        status.textContent = statusText;
    }
    if (readerPdf) {
        readerPdf.style.display = "none";
        readerPdf.src = "";
    }
    if (readerText) {
        readerText.style.display = "none";
        readerText.textContent = "";
    }
    activeReaderType = "text";

    const bookmark = document.getElementById("bookmarkPage");
    const highlights = document.getElementById("highlightsInput");
    applyBookmarkControlMode(0, 1);
    if (highlights) {
        highlights.value = "";
    }
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

        if (readerText) {
            readerText.style.display = "none";
            readerText.textContent = "";
        }
        if (readerPdf) {
            readerPdf.style.display = "block";
            readerPdf.src = readerFileObjectUrl;
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
        const readerPdf = document.getElementById("readerPdf");
        if (readerPdf) {
            readerPdf.src = `${readerFileObjectUrl}#page=${Math.max(1, preferred)}`;
        }
    }
    const highlights = document.getElementById("highlightsInput");
    if (highlights) {
        highlights.value = Array.isArray(progress.highlights) ? progress.highlights.join("\n") : "";
    }
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
                selectedBorrowedBookId = item.bookId;
                const status = document.getElementById("readerStatus");
                if (status) {
                    status.textContent = `Reading: ${item.bookTitle}`;
                }
                await loadBorrowedContent(item.bookId);
                await loadReadingProgress(item.bookId);
            } catch (error) {
                showToast(error.message, true);
            }
        });

        list.appendChild(li);
    }

    if (autoBookId) {
        const target = items.find((it) => String(it.bookId) === String(autoBookId));
        if (target) {
            selectedBorrowedBookId = target.bookId;
            const status = document.getElementById("readerStatus");
            if (status) {
                status.textContent = `Reading: ${target.bookTitle}`;
            }
            await loadBorrowedContent(target.bookId);
            await loadReadingProgress(target.bookId);
        }
    }
}

document.getElementById("saveProgressBtn")?.addEventListener("click", async () => {
    try {
        if (!selectedBorrowedBookId) {
            showToast("Please click Read on a borrowed book first.", true);
            return;
        }

        const bookmark = getSelectedBookmarkPage();
        const highlights = document.getElementById("highlightsInput")?.value || "";

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

window.addEventListener("beforeunload", () => {
    clearReaderObjectUrl();
});

if (currentUser) {
    const params = new URLSearchParams(window.location.search);
    const autoBookId = params.get("bookId") || "";
    refreshBorrows(autoBookId).catch((e) => showToast(e.message, true));
}
