const currentUser = requireRole("LIBRARIAN");
let cachedRequests = [];
let selectedRequest = null;
let currentPdfSearchPage = 1;
let currentPdfSearchHasNext = false;
let currentPdfSearchQuery = { title: "", authorName: "", searchMode: "partial" };

const selectedRequestIdInput = document.getElementById("selectedRequestId");
const selectedTitleInput = document.getElementById("selectedTitle");
const selectedAuthorInput = document.getElementById("selectedAuthor");
const selectedGenresInput = document.getElementById("selectedGenres");
const selectedReasonInput = document.getElementById("selectedReason");
const generatedDescriptionInput = document.getElementById("generatedDescription");
const selectedPdfUrlInput = document.getElementById("selectedPdfUrl");
const pdfResultsBody = document.getElementById("pdfResultsBody");
const pdfSearchModeInput = document.getElementById("pdfSearchMode");
const prevPdfPageBtn = document.getElementById("prevPdfPageBtn");
const nextPdfPageBtn = document.getElementById("nextPdfPageBtn");
const pdfPageInfo = document.getElementById("pdfPageInfo");

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

function buildRequestRows(items) {
    const body = document.getElementById("requestBody");
    if (!body) {
        return;
    }

    body.innerHTML = "";

    items.forEach((item) => {
        const row = document.createElement("tr");
        const genres = Array.isArray(item.genres) && item.genres.length > 0
            ? item.genres.map((genre) => `<span class="genre-badge">${genre}</span>`).join("")
            : '<span class="genre-badge genre-badge-empty">None</span>';
        const status = item.status || "PENDING";

        row.innerHTML = `
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

        actionCell.appendChild(selectBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(approveBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(uploadBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(rejectBtn);

        body.appendChild(row);
    });

    if (!items.length) {
        body.innerHTML = '<tr><td colspan="10">No matching requests.</td></tr>';
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
        pdfResultsBody.innerHTML = '<tr><td colspan="4">No PDF results found.</td></tr>';
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

async function downloadAndUpload() {
    try {
        if (!selectedRequest?.id) {
            showToast("Select a request first.", true);
            return;
        }
        if (String(selectedRequest.status || "").toLowerCase() !== "approved") {
            showToast("Request must be approved before download.", true);
            return;
        }
        const pdfUrl = selectedPdfUrlInput?.value.trim() || "";
        if (!pdfUrl) {
            showToast("Provide a PDF download URL.", true);
            return;
        }
        if (!confirm("Download this PDF and upload it to the library?")) {
            return;
        }
        const comment = (prompt("Upload comment (optional)") || "").trim();
        const description = generatedDescriptionInput?.value.trim() || "";
        const content = description ? "" : await extractFirstTwoPagesTextFromPdfUrl(pdfUrl);
        const response = await api("/api/librarian/book-request/download", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                requestId: selectedRequest.id,
                pdfUrl,
                comment,
                description,
                content
            })
        });
        showToast(response?.message || "Downloaded and uploaded.", false);
        await refreshRequests();
    } catch (error) {
        showToast(error.message, true);
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

document.getElementById("downloadAndUploadBtn")?.addEventListener("click", () => {
    downloadAndUpload();
});

if (currentUser) {
    updatePdfSearchPaging(1, false);
    refreshRequests().catch((error) => showToast(error.message, true));
}