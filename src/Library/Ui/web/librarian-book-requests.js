const currentUser = requireRole("LIBRARIAN");
let cachedRequests = [];

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
            <td>${item.requestedDate || ""}</td>
            <td>${item.approvedDate || ""}</td>
            <td>${item.uploadedDate || ""}</td>
            <td>${item.reason || ""}</td>
            <td></td>
        `;

        const actionCell = row.lastElementChild;
        const requestId = item.id || "";

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

if (currentUser) {
    refreshRequests().catch((error) => showToast(error.message, true));
}