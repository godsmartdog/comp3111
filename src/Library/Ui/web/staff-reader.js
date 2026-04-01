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

    const bookmark = document.getElementById("bookmarkPage");
    const highlights = document.getElementById("highlightsInput");
    if (bookmark) {
        bookmark.value = "1";
    }
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
        const response = await fetch(payload.url, { headers });
        if (!response.ok) {
            throw new Error("Failed to load PDF preview.");
        }

        clearReaderObjectUrl();
        const blob = await response.blob();
        readerFileObjectUrl = URL.createObjectURL(blob);

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
    const bookmark = document.getElementById("bookmarkPage");
    const highlights = document.getElementById("highlightsInput");
    if (bookmark) {
        bookmark.value = String(progress.bookmark || 1);
    }
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

        const bookmark = Number(document.getElementById("bookmarkPage")?.value || "1");
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
