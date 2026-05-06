const currentUser = requireRole("LIBRARIAN");
let cachedRequests = [];
let selectedRequest = null;
let currentPdfSearchPage = 1;
let currentPdfSearchHasNext = false;
let currentPdfSearchQuery = { title: "", authorName: "", searchMode: "partial" };
const PENDING_PUBLISHED_BOOK_PREFILL_KEY = "pendingPublishedBookPrefill";

// Slice 7: client-side sort state for the request queue table.
// state: { key: string|null, direction: "asc"|"desc"|null }
let requestSortState = { key: null, direction: null };

// Slice 7: derive priority bucket from request age (days since submitted).
function derivePriority(item) {
    const status = String(item.status || "").toLowerCase();
    // Already-handled requests are always Normal.
    if (status !== "pending") {
        return { level: "normal", label: "NORMAL", days: 0 };
    }
    const submitted = item.requestedDate || item.requestedAt || "";
    if (!submitted) {
        return { level: "normal", label: "NORMAL", days: 0 };
    }
    const submittedMs = Date.parse(submitted);
    if (!Number.isFinite(submittedMs)) {
        return { level: "normal", label: "NORMAL", days: 0 };
    }
    const days = Math.floor((Date.now() - submittedMs) / (1000 * 60 * 60 * 24));
    if (days >= 14) return { level: "high", label: "HIGH", days };
    if (days >= 7)  return { level: "medium", label: "MED", days };
    return { level: "normal", label: "NORMAL", days };
}

function priorityRank(level) {
    if (level === "high") return 0;
    if (level === "medium") return 1;
    return 2;
}

const selectedRequestIdInput = document.getElementById("selectedRequestId");
const selectedTitleInput = document.getElementById("selectedTitle");
const selectedAuthorInput = document.getElementById("selectedAuthor");
const selectedGenresInput = document.getElementById("selectedGenres");
const selectedReasonInput = document.getElementById("selectedReason");
const generatedDescriptionInput = document.getElementById("generatedDescription");
const selectedPdfUrlInput = document.getElementById("selectedPdfUrl");
const pdfResultsBody = document.getElementById("pdfResultsBody");
const pdfSearchModeInput = document.getElementById("pdfSearchMode");
const pdfLoadingIndicator = document.getElementById("pdfLoadingIndicator");
const prevPdfPageBtn = document.getElementById("prevPdfPageBtn");
const nextPdfPageBtn = document.getElementById("nextPdfPageBtn");
const pdfPageInfo = document.getElementById("pdfPageInfo");
const uploadPdfFileInput = document.getElementById("uploadPdfFileInput");
const downloadAndUploadBtn = document.getElementById("downloadAndUploadBtn");
const downloadProgressBox = document.getElementById("downloadProgressBox");
const downloadProgressBar = document.getElementById("downloadProgressBar");
const downloadProgressText = document.getElementById("downloadProgressText");
let downloadProgressTimer = null;

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function normalizeText(value) {
    return String(value || "").trim().toLowerCase();
}

function setDownloadProgress(value, text) {
    if (downloadProgressBox) {
        downloadProgressBox.style.display = "block";
    }
    if (downloadProgressBar) {
        downloadProgressBar.value = Math.max(0, Math.min(100, Number(value || 0)));
    }
    if (downloadProgressText) {
        downloadProgressText.textContent = text || "";
    }
}

function clearDownloadProgressTimer() {
    if (downloadProgressTimer) {
        clearInterval(downloadProgressTimer);
        downloadProgressTimer = null;
    }
}

function startDownloadProgress() {
    clearDownloadProgressTimer();
    setDownloadProgress(5, "Preparing download...");
    let value = 5;
    downloadProgressTimer = setInterval(() => {
        value = Math.min(90, value + 5);
        const text = value < 35
            ? "Preparing request..."
            : value < 65
                ? "Downloading requested book..."
                : "Validating and uploading to library...";
        setDownloadProgress(value, text);
        if (value >= 90) {
            clearDownloadProgressTimer();
        }
    }, 600);
}

function finishDownloadProgress(success, message) {
    clearDownloadProgressTimer();
    if (success) {
        setDownloadProgress(100, message || "Download and upload complete.");
        setTimeout(() => {
            if (downloadProgressBox) {
                downloadProgressBox.style.display = "none";
            }
            if (downloadProgressBar) {
                downloadProgressBar.value = 0;
            }
        }, 1800);
    } else {
        setDownloadProgress(0, message || "Download failed.");
        setTimeout(() => {
            if (downloadProgressBox) {
                downloadProgressBox.style.display = "none";
            }
            if (downloadProgressBar) {
                downloadProgressBar.value = 0;
            }
        }, 3000);
    }
}

function requestMatchesFilters(item) {
    const searchInput = document.getElementById("requestSearchInput");
    const statusFilter = document.getElementById("requestStatusFilter");
    const search = normalizeText(searchInput?.value || "");
    const status = normalizeText(statusFilter?.value || "all");
    const itemStatus = normalizeText(item.status || "");

    if (status !== "all" && itemStatus !== status) {
        return false;
    }

    if (!search) {
        return true;
    }

    const genres = Array.isArray(item.genres) ? item.genres.map(normalizeText) : [];
    return normalizeText(item.title).includes(search)
        || normalizeText(item.authorName).includes(search)
        || normalizeText(item.requesterFullName).includes(search)
        || normalizeText(item.requesterUsername).includes(search)
        || normalizeText(item.reason).includes(search)
        || genres.some((genre) => genre.includes(search));
}

function applyRequestSort(items) {
    if (!requestSortState.key || !requestSortState.direction) {
        return items;
    }
    const key = requestSortState.key;
    const dir = requestSortState.direction === "desc" ? -1 : 1;
    const out = items.slice();
    out.sort((a, b) => {
        let av;
        let bv;
        if (key === "priorityRank") {
            av = priorityRank(derivePriority(a).level);
            bv = priorityRank(derivePriority(b).level);
        } else if (key === "requestedDate") {
            av = Date.parse(a.requestedDate || "") || 0;
            bv = Date.parse(b.requestedDate || "") || 0;
        } else {
            av = normalizeText(a[key] || "");
            bv = normalizeText(b[key] || "");
        }
        if (av < bv) return -1 * dir;
        if (av > bv) return  1 * dir;
        return 0;
    });
    return out;
}

function updateSortIndicators() {
    document.querySelectorAll("th.sortable").forEach((th) => {
        const key = th.getAttribute("data-sort-key");
        const base = th.textContent.replace(/[\s▲▼]+$/u, "").replace(/\s+$/u, "");
        if (key === requestSortState.key && requestSortState.direction) {
            th.textContent = `${base} ${requestSortState.direction === "asc" ? "▲" : "▼"}`;
        } else {
            th.textContent = base;
        }
    });
}

function buildRequestRows(items) {
    const body = document.getElementById("requestBody");
    if (!body) {
        return;
    }

    body.innerHTML = "";
    const sorted = applyRequestSort(items);

    sorted.forEach((item) => {
        const row = document.createElement("tr");
        if (item.priority) {
            row.classList.add("request-priority");
        }
        const genres = Array.isArray(item.genres) && item.genres.length > 0
            ? item.genres.map((genre) => `<span class="genre-badge">${genre}</span>`).join("")
            : '<span class="genre-badge genre-badge-empty">None</span>';
        const status = item.status || "PENDING";
        const pri = derivePriority(item);
        const star = item.priority ? " ★" : "";

        row.innerHTML = `
            <td><input type="checkbox" class="bulk-request-checkbox" data-request-id="${item.id || ""}" data-status="${(item.status || "").toLowerCase()}"></td>
            <td><span class="priority-badge priority-${pri.level}">${pri.label}</span>${star}</td>
            <td>${item.title || ""}</td>
            <td>${item.requesterFullName || ""}</td>
            <td>${item.authorName || ""}</td>
            <td><div class="genre-badge-group">${genres}</div></td>
            <td>${status}</td>
            <td>${formatDateOnly(item.requestedDate || "")}</td>
            <td>${formatDateOnly(item.approvedDate || "")}</td>
            <td>${formatDateOnly(item.uploadedDate || "")}</td>
            <td>${item.reason || ""}</td>
            <td></td>
        `;

        const actionCell = row.lastElementChild;
        const requestId = item.id || "";

        const selectBtn = document.createElement("button");
        selectBtn.className = "secondary";
        selectBtn.textContent = "Select";
        selectBtn.addEventListener("click", () => setSelectedRequest(item));

        const approveBtn = document.createElement("button");
        approveBtn.className = "secondary";
        approveBtn.textContent = "Approve";
        approveBtn.disabled = status.toLowerCase() !== "pending";
        approveBtn.addEventListener("click", () => reviewRequest(requestId, "approve", item));

        const uploadBtn = document.createElement("button");
        uploadBtn.className = "secondary";
        uploadBtn.textContent = "Upload";
        uploadBtn.disabled = status.toLowerCase() !== "approved";
        uploadBtn.addEventListener("click", () => reviewRequest(requestId, "upload", item));

        const rejectBtn = document.createElement("button");
        rejectBtn.className = "danger";
        rejectBtn.textContent = "Reject";
        rejectBtn.disabled = status.toLowerCase() !== "pending";
        rejectBtn.addEventListener("click", () => reviewRequest(requestId, "reject", item));

        const priorityBtn = document.createElement("button");
        priorityBtn.className = "secondary";
        priorityBtn.textContent = item.priority ? "Unmark Priority" : "Mark Priority";
        priorityBtn.addEventListener("click", () => togglePriority(requestId, !item.priority));

        actionCell.appendChild(selectBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(approveBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(uploadBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(rejectBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(priorityBtn);

        const cb = row.querySelector(".bulk-request-checkbox");
        cb?.addEventListener("change", updateBulkRequestButtonsState);

        body.appendChild(row);
    });

    if (!sorted.length) {
        body.innerHTML = '<tr><td colspan="12">No matching requests.</td></tr>';
    }

    const selectAll = document.getElementById("bulkSelectAllRequests");
    if (selectAll) selectAll.checked = false;
    updateBulkRequestButtonsState();
    updateSortIndicators();
}

function getSelectedRequestIds(filter = () => true) {
    return Array.from(document.querySelectorAll(".bulk-request-checkbox"))
        .filter((cb) => cb.checked && filter(cb))
        .map((cb) => cb.getAttribute("data-request-id"))
        .filter(Boolean);
}

function updateBulkRequestButtonsState() {
    const approveBtn = document.getElementById("bulkApproveBtn");
    const rejectBtn = document.getElementById("bulkRejectBtn");
    const pendingCount = getSelectedRequestIds((cb) => cb.getAttribute("data-status") === "pending").length;
    const totalCount = getSelectedRequestIds().length;
    if (approveBtn) {
        approveBtn.disabled = pendingCount === 0;
        approveBtn.textContent = pendingCount > 0 ? `Approve Selected (${pendingCount})` : "Approve Selected";
    }
    if (rejectBtn) {
        rejectBtn.disabled = pendingCount === 0;
        rejectBtn.textContent = pendingCount > 0 ? `Reject Selected (${pendingCount})` : "Reject Selected";
    }
    // Note: only pending rows can be approved/rejected; non-pending selections are ignored.
    void totalCount;
}

async function bulkReviewSelected(action) {
    const ids = getSelectedRequestIds((cb) => cb.getAttribute("data-status") === "pending");
    if (ids.length === 0) return;
    const verb = action === "approve" ? "Approve" : "Reject";
    if (!confirm(`${verb} ${ids.length} selected request(s)?`)) {
        return;
    }
    let succeeded = 0;
    const failed = [];
    for (const id of ids) {
        try {
            await api("/api/librarian/book-request/review", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: formBody({ requestId: id, action, comment: "", reason: "" })
            });
            succeeded++;
        } catch (error) {
            failed.push(id);
        }
    }
    if (failed.length === 0) {
        showToast(`${verb}d ${succeeded}.`, false);
    } else {
        showToast(`${verb}d ${succeeded}, failed ${failed.length}: ${failed.join(", ")}`, true);
    }
    await refreshRequests();
}

async function togglePriority(requestId, priority) {
    try {
        await api("/api/librarian/book-request-priority", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ requestId, priority: priority ? "true" : "false" })
        });
        showToast(priority ? "Marked as priority." : "Priority cleared.", false);
        await refreshRequests();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function refreshRequests() {
    const statusFilter = document.getElementById("requestStatusFilter");
    const searchInput = document.getElementById("requestSearchInput");
    const query = new URLSearchParams();

    if (statusFilter?.value && statusFilter.value !== "all") {
        query.set("status", statusFilter.value);
    }
    if (searchInput?.value.trim()) {
        query.set("q", searchInput.value.trim());
    }

    const path = `/api/librarian/book-requests${query.toString() ? `?${query.toString()}` : ""}`;
    const items = await api(path);
    cachedRequests = Array.isArray(items) ? items : [];
    buildRequestRows(cachedRequests.filter(requestMatchesFilters));
    syncSelectedRequest();
}

function syncSelectedRequest() {
    if (!selectedRequest?.id) {
        return;
    }
    const updated = cachedRequests.find((item) => item.id === selectedRequest.id);
    if (updated) {
        setSelectedRequest(updated);
    }
}

function resetFilters() {
    const searchInput = document.getElementById("requestSearchInput");
    const statusFilter = document.getElementById("requestStatusFilter");
    if (searchInput) {
        searchInput.value = "";
    }
    if (statusFilter) {
        statusFilter.value = "all";
    }
}

async function reviewRequest(requestId, action, item = {}) {
    try {
        const title = item.title || requestId;
        const requester = item.requesterFullName || item.requesterUsername || "unknown requester";
        const reason = item.reason || "No reason provided";

        if (!confirm(`Confirm ${action} this request?\n\nTitle: ${title}\nRequester: ${requester}\nReason: ${reason}`)) {
            return;
        }

        let comment = "";
        let rejectionReason = "";
        if (action === "reject") {
            rejectionReason = (prompt("Rejection reason (optional, max 500 characters)") || "").trim();
            if (rejectionReason.length > 500) {
                showToast("Rejection reason must be at most 500 characters.", true);
                return;
            }
            comment = (prompt("Reviewer comment (optional, max 500 characters)") || "").trim();
            if (comment.length > 500) {
                showToast("Reviewer comment must be at most 500 characters.", true);
                return;
            }
        } else {
            comment = (prompt(`Comment for ${action} (optional)`) || "").trim();
        }

        const response = await api("/api/librarian/book-request/review", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ requestId, action, comment, reason: rejectionReason })
        });

        showToast(response?.message || "Request updated.", false);
        await refreshRequests();
    } catch (error) {
        showToast(error.message, true);
    }
}

function setSelectedRequest(item) {
    selectedRequest = item;
    if (selectedRequestIdInput) {
        selectedRequestIdInput.value = item?.id || "";
    }
    if (selectedTitleInput) {
        selectedTitleInput.value = item?.title || "";
    }
    if (selectedAuthorInput) {
        selectedAuthorInput.value = item?.authorName || "";
    }
    if (selectedGenresInput) {
        selectedGenresInput.value = Array.isArray(item?.genres) ? item.genres.join(", ") : "";
    }
    if (selectedReasonInput) {
        selectedReasonInput.value = item?.reason || "";
    }
    if (generatedDescriptionInput && !generatedDescriptionInput.value.trim()) {
        generatedDescriptionInput.value = item?.reason || "";
    }
}

function buildPdfResults(items) {
    if (!pdfResultsBody) {
        return;
    }
    pdfResultsBody.innerHTML = "";

    if (!items.length) {
        pdfResultsBody.innerHTML = '<tr><td colspan="4">No result</td></tr>';
        return;
    }

    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${item.title || ""}</td>
            <td>${item.source || ""}</td>
            <td>${item.identifier || ""}</td>
            <td></td>
        `;
        const actionCell = row.lastElementChild;
        const useBtn = document.createElement("button");
        useBtn.className = "secondary";
        useBtn.textContent = "Use PDF";
        useBtn.addEventListener("click", () => {
            if (selectedPdfUrlInput) {
                selectedPdfUrlInput.value = item.downloadUrl || "";
            }
        });
        actionCell.appendChild(useBtn);
        pdfResultsBody.appendChild(row);
    });
}

async function extractFirstTwoPagesTextFromPdfUrl(pdfUrl) {
    if (!pdfUrl) {
        return "";
    }
    if (typeof pdfjsLib === "undefined") {
        throw new Error("PDF text extraction dependency is missing.");
    }

    if (!pdfjsLib.GlobalWorkerOptions.workerSrc) {
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.worker.min.js";
    }

    const response = await fetch(pdfUrl);
    if (!response.ok) {
        throw new Error("Failed to load PDF for text extraction.");
    }

    const bytes = await response.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: bytes }).promise;
    const pageCount = Math.min(2, Number(pdf.numPages || 0));
    const pages = [];

    for (let pageNumber = 1; pageNumber <= pageCount; pageNumber += 1) {
        const page = await pdf.getPage(pageNumber);
        const textContent = await page.getTextContent();
        const text = Array.isArray(textContent?.items)
            ? textContent.items.map((item) => item.str || "").join(" ").replace(/\s+/g, " ").trim()
            : "";
        if (text) {
            pages.push(text);
        }
    }

    return pages.join("\n\n").trim();
}

function updatePdfSearchPaging(page, hasNext) {
    currentPdfSearchPage = Math.max(1, page || 1);
    currentPdfSearchHasNext = Boolean(hasNext);
    if (pdfPageInfo) {
        pdfPageInfo.textContent = `Page ${currentPdfSearchPage}`;
    }
    if (prevPdfPageBtn) {
        prevPdfPageBtn.disabled = currentPdfSearchPage <= 1;
    }
    if (nextPdfPageBtn) {
        nextPdfPageBtn.disabled = !currentPdfSearchHasNext;
    }
}

async function searchPdfSources(page = 1) {
    try {
        const title = selectedTitleInput?.value.trim() || "";
        const authorName = selectedAuthorInput?.value.trim() || "";
        const searchMode = pdfSearchModeInput?.value === "exact" ? "exact" : "partial";
        if (!title && !authorName) {
            showToast("Enter a title or author to search.", true);
            return;
        }
        currentPdfSearchQuery = { title, authorName, searchMode };
        const query = new URLSearchParams();
        if (title) {
            query.set("title", title);
        }
        if (authorName) {
            query.set("authorName", authorName);
        }
        query.set("page", String(Math.max(1, page)));
        query.set("limit", "5");
        query.set("searchMode", searchMode);

        // Show loading indicator
        if (pdfLoadingIndicator) {
            pdfLoadingIndicator.style.display = "block";
        }

        const response = await api(`/api/librarian/book-request/search-pdf?${query.toString()}`);
        const results = Array.isArray(response)
            ? response
            : Array.isArray(response?.results)
                ? response.results
                : [];
        buildPdfResults(results);
        updatePdfSearchPaging(
            Number(response?.page || page || 1),
            response?.hasNext === undefined ? results.length >= 5 : Boolean(response.hasNext)
        );
    } catch (error) {
        showToast(error.message, true);
    } finally {
        // Hide loading indicator
        if (pdfLoadingIndicator) {
            pdfLoadingIndicator.style.display = "none";
        }
    }
}

async function generateSummary() {
    try {
        if (!selectedRequest?.id) {
            showToast("Select a request first.", true);
            return;
        }
        const pdfUrl = selectedPdfUrlInput?.value.trim() || "";
        if (!pdfUrl) {
            showToast("Select a PDF download URL before generating a summary.", true);
            return;
        }
        const payload = {
            title: selectedTitleInput?.value.trim() || "",
            authorName: selectedAuthorInput?.value.trim() || "",
            genres: selectedGenresInput?.value.trim() || "",
            reason: selectedReasonInput?.value.trim() || "",
            content: await extractFirstTwoPagesTextFromPdfUrl(pdfUrl)
        };
        if (!payload.title || !payload.authorName) {
            showToast("Title and author are required to generate a summary.", true);
            return;
        }
        if (!payload.content) {
            showToast("Could not extract readable text from the first two PDF pages.", true);
            return;
        }
        const response = await api("/api/librarian/book-request/generate-description", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody(payload)
        });
        if (generatedDescriptionInput) {
            generatedDescriptionInput.value = response?.description || "";
        }
        showToast("Summary generated.", false);
    } catch (error) {
        showToast(error.message, true);
    }
}

function downloadPdf() {
    const pdfUrl = selectedPdfUrlInput?.value.trim() || "";
    if (!pdfUrl) {
        showToast("Provide a PDF download URL.", true);
        return;
    }
    const title = selectedTitleInput?.value.trim() || "download";
    const safeName = title.replace(/[^a-z0-9]+/gi, "-").replace(/^-+|-+$/g, "");
    const fileName = `${safeName || "download"}.pdf`;
    const downloadUrl = `/api/download?url=${encodeURIComponent(pdfUrl)}&filename=${encodeURIComponent(fileName)}`;

    const anchor = document.createElement("a");
    anchor.href = downloadUrl;
    anchor.download = fileName;
    anchor.rel = "noopener noreferrer";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    showToast("Starting PDF download...", false);
}

async function downloadAndUpload() {
    if (!selectedRequest?.id) {
        showToast("Select a request first.", true);
        return;
    }
    if (String(selectedRequest.status || "").toLowerCase() !== "approved") {
        showToast("Request must be approved before download and upload.", true);
        return;
    }

    const pdfUrl = selectedPdfUrlInput?.value.trim() || "";
    if (!pdfUrl) {
        showToast("Provide a PDF download URL.", true);
        return;
    }

    const title = selectedTitleInput?.value.trim() || selectedRequest.title || selectedRequest.id;
    if (!confirm(`Download and upload this requested book?\n\nTitle: ${title}\nPDF URL: ${pdfUrl}`)) {
        return;
    }

    if (downloadAndUploadBtn) {
        downloadAndUploadBtn.disabled = true;
    }
    startDownloadProgress();

    try {
        const response = await api("/api/librarian/book-request/download", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                requestId: selectedRequest.id,
                pdfUrl,
                comment: "",
                description: generatedDescriptionInput?.value.trim() || "",
                content: ""
            })
        });
        finishDownloadProgress(true, response?.message || "Downloaded and uploaded.");
        showToast(response?.message || "Downloaded and uploaded.", false);
        await refreshRequests();
    } catch (error) {
        finishDownloadProgress(false, error.message);
        showToast(error.message, true);
    } finally {
        if (downloadAndUploadBtn) {
            downloadAndUploadBtn.disabled = false;
        }
    }
}

function openAddPublishedBookPageFromRequest() {
    if (!selectedRequest?.id) {
        showToast("Select a request first.", true);
        return;
    }
    if (String(selectedRequest.status || "").toLowerCase() !== "approved") {
        showToast("Request must be approved before opening publish flow.", true);
        return;
    }

    const prefill = {
        requestId: selectedRequest.id || "",
        title: selectedTitleInput?.value.trim() || selectedRequest.title || "",
        authorNames: selectedAuthorInput?.value.trim() || selectedRequest.authorName || "",
        genres: selectedGenresInput?.value.trim() || (Array.isArray(selectedRequest.genres) ? selectedRequest.genres.join(", ") : ""),
        description: (generatedDescriptionInput?.value.trim() || selectedReasonInput?.value.trim() || "").trim(),
        sourcePdfUrl: selectedPdfUrlInput?.value.trim() || "",
        coverImagePath: ""
    };

    try {
        sessionStorage.setItem(PENDING_PUBLISHED_BOOK_PREFILL_KEY, JSON.stringify(prefill));
        window.location.href = "librarian-manage-published.html#addPublishedBookSection";
    } catch (error) {
        showToast(`Could not open publish flow: ${error.message}`, true);
    }
}

document.getElementById("refreshRequestsBtn")?.addEventListener("click", () => {
    refreshRequests().catch((error) => showToast(error.message, true));
});

document.getElementById("applyRequestFiltersBtn")?.addEventListener("click", () => {
    buildRequestRows(cachedRequests.filter(requestMatchesFilters));
});

document.getElementById("resetRequestFiltersBtn")?.addEventListener("click", () => {
    resetFilters();
    refreshRequests().catch((error) => showToast(error.message, true));
});

document.getElementById("requestSearchInput")?.addEventListener("input", () => {
    buildRequestRows(cachedRequests.filter(requestMatchesFilters));
});

document.getElementById("requestStatusFilter")?.addEventListener("change", () => {
    refreshRequests().catch((error) => showToast(error.message, true));
});

document.getElementById("searchPdfBtn")?.addEventListener("click", () => {
    searchPdfSources(1);
});

prevPdfPageBtn?.addEventListener("click", () => {
    if (currentPdfSearchPage > 1) {
        searchPdfSources(currentPdfSearchPage - 1);
    }
});

nextPdfPageBtn?.addEventListener("click", () => {
    if (currentPdfSearchHasNext) {
        searchPdfSources(currentPdfSearchPage + 1);
    }
});

document.getElementById("generateSummaryBtn")?.addEventListener("click", () => {
    generateSummary();
});

document.getElementById("downloadPdfBtn")?.addEventListener("click", () => {
    downloadPdf();
});

downloadAndUploadBtn?.addEventListener("click", () => {
    downloadAndUpload();
});

document.getElementById("uploadPdfBtn")?.addEventListener("click", () => {
    openAddPublishedBookPageFromRequest();
});

if (currentUser) {
    // Slice 7: bulk select-all + bulk approve/reject.
    document.getElementById("bulkSelectAllRequests")?.addEventListener("change", (event) => {
        const checked = !!event.target.checked;
        document.querySelectorAll(".bulk-request-checkbox").forEach((cb) => { cb.checked = checked; });
        updateBulkRequestButtonsState();
    });
    document.getElementById("bulkApproveBtn")?.addEventListener("click", () => {
        bulkReviewSelected("approve").catch((error) => showToast(error.message, true));
    });
    document.getElementById("bulkRejectBtn")?.addEventListener("click", () => {
        bulkReviewSelected("reject").catch((error) => showToast(error.message, true));
    });

    // Slice 7: column sort cycle (asc → desc → unsorted).
    document.querySelectorAll("th.sortable").forEach((th) => {
        th.style.cursor = "pointer";
        th.addEventListener("click", () => {
            const key = th.getAttribute("data-sort-key");
            if (!key) return;
            if (requestSortState.key !== key) {
                requestSortState = { key, direction: "asc" };
            } else if (requestSortState.direction === "asc") {
                requestSortState = { key, direction: "desc" };
            } else {
                requestSortState = { key: null, direction: null };
            }
            buildRequestRows(cachedRequests.filter(requestMatchesFilters));
        });
    });

    // Slice 7: Enter-to-apply on the keyword search input.
    document.getElementById("requestSearchInput")?.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            buildRequestRows(cachedRequests.filter(requestMatchesFilters));
        }
    });

    updatePdfSearchPaging(1, false);
    refreshRequests().catch((error) => showToast(error.message, true));
}