const currentUser = requireRole("LIBRARIAN");
let submissionSortEnabled = false;
let cachedPendingItems = [];

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function renderPending(items) {
    const tbody = document.getElementById("pendingBody");
    if (!tbody) {
        return;
    }

    tbody.innerHTML = "";

    items.forEach((item) => {
        const submissionId = item.id || item.submissionId || "";
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${item.title}</td>
            <td>${item.authorFullName}</td>
            <td>${item.status || ""}</td>
            <td>${item.submittedDate}</td>
            <td>${item.fileName}</td>
            <td></td>
        `;

        const actionCell = row.lastElementChild;

        const approveBtn = document.createElement("button");
        approveBtn.className = "secondary";
        approveBtn.textContent = "Approve";
        approveBtn.addEventListener("click", () => review(submissionId, "approve"));

        const readBtn = document.createElement("button");
        readBtn.className = "secondary";
        readBtn.textContent = "Read";
        readBtn.addEventListener("click", () => {
            if (!submissionId) {
                showToast("Submission ID is missing for this row.", true);
                return;
            }
            window.location.href = `librarian-submission-reader.html?submissionId=${encodeURIComponent(submissionId)}`;
        });

        const rejectBtn = document.createElement("button");
        rejectBtn.className = "danger";
        rejectBtn.textContent = "Reject";
        rejectBtn.addEventListener("click", () => review(submissionId, "reject"));

        actionCell.appendChild(readBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(approveBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(rejectBtn);

        tbody.appendChild(row);
    });
}

function matchesTextFilter(value, filterText, matchMode) {
    if (!filterText) {
        return true;
    }

    const normalizedValue = String(value || "").trim().toLowerCase();
    const normalizedFilter = filterText.trim().toLowerCase();
    if (matchMode === "exact") {
        return normalizedValue === normalizedFilter;
    }
    return normalizedValue.includes(normalizedFilter);
}

function matchesAnyFilter(values, filterText, matchMode) {
    if (!filterText) {
        return true;
    }

    const normalizedValues = Array.isArray(values)
        ? values.map((value) => String(value || "").trim().toLowerCase()).filter(Boolean)
        : [];
    const normalizedFilter = filterText.trim().toLowerCase();

    if (matchMode === "exact") {
        return normalizedValues.some((value) => value === normalizedFilter);
    }
    return normalizedValues.some((value) => value.includes(normalizedFilter));
}

function applyPendingAdvancedFilters(items) {
    const searchInput = document.getElementById("submissionSearchInput");
    const genreInput = document.getElementById("submissionGenreFilter");
    const dateInput = document.getElementById("submissionDateFilter");
    const matchMode = "contains";

    const keyword = searchInput ? searchInput.value.trim() : "";
    const genreFilter = genreInput ? genreInput.value.trim() : "";
    const submittedDateFilter = dateInput ? dateInput.value.trim() : "";

    return items.filter((item) => {
        const titleMatch = matchesTextFilter(item.title, keyword, matchMode);
        const authorMatch = matchesTextFilter(item.authorFullName, keyword, matchMode);
        const usernameMatch = matchesTextFilter(item.authorUsername, keyword, matchMode);
        const genreMatch = matchesAnyFilter(item.genres, genreFilter, matchMode);
        const dateMatch = !submittedDateFilter || String(item.submittedDate || "").trim() === submittedDateFilter;
        return (titleMatch || authorMatch || usernameMatch) && genreMatch && dateMatch;
    });
}

async function refreshPending() {
    const sortDirFilter = document.getElementById("submissionSortDirFilter");

    const query = new URLSearchParams();
    const sortDir = sortDirFilter ? sortDirFilter.value : "asc";

    query.set("status", "pending");
    if (submissionSortEnabled) {
        query.set("sortBy", "submittedDate");
        query.set("sortDir", sortDir || "asc");
    }

    const path = `/api/librarian/pending?${query.toString()}`;
    const items = await api(path);
    cachedPendingItems = Array.isArray(items) ? items : [];
    renderPending(applyPendingAdvancedFilters(cachedPendingItems));
}

function resetSubmissionFilters() {
    const searchInput = document.getElementById("submissionSearchInput");
    const genreInput = document.getElementById("submissionGenreFilter");
    const dateInput = document.getElementById("submissionDateFilter");
    const sortDirFilter = document.getElementById("submissionSortDirFilter");

    if (searchInput) {
        searchInput.value = "";
    }
    if (genreInput) {
        genreInput.value = "";
    }
    if (dateInput) {
        dateInput.value = "";
    }
    if (sortDirFilter) {
        sortDirFilter.value = "asc";
    }
}

async function review(submissionId, action) {
    try {
        let comment = "";
        let reason = "";

        if (action === "reject") {
            reason = (prompt("Rejection reason (optional, max 500 characters)") || "").trim();
            if (reason.length > 500) {
                showToast("Rejection reason must be at most 500 characters.", true);
                return;
            }
            comment = reason ? "Rejected" : "Rejected";
        } else {
            comment = prompt(`Comment for ${action}`) || "";
        }

        const text = await api("/api/librarian/review", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ submissionId, action, comment, reason })
        }, false);

        showToast(text, false);
        await refreshPending();
    } catch (error) {
        showToast(error.message, true);
    }
}

document.getElementById("refreshPendingBtn")?.addEventListener("click", () => {
    refreshPending().catch((e) => showToast(e.message, true));
});

document.getElementById("submissionSearchInput")?.addEventListener("input", () => {
    renderPending(applyPendingAdvancedFilters(cachedPendingItems));
});

document.getElementById("submissionGenreFilter")?.addEventListener("input", () => {
    renderPending(applyPendingAdvancedFilters(cachedPendingItems));
});

document.getElementById("submissionDateFilter")?.addEventListener("change", () => {
    renderPending(applyPendingAdvancedFilters(cachedPendingItems));
});

document.getElementById("applySubmissionFiltersBtn")?.addEventListener("click", () => {
    submissionSortEnabled = true;
    refreshPending().catch((e) => showToast(e.message, true));
});

document.getElementById("resetSubmissionFiltersBtn")?.addEventListener("click", () => {
    submissionSortEnabled = false;
    resetSubmissionFilters();
    refreshPending().catch((e) => showToast(e.message, true));
});

if (currentUser) {
    refreshPending().catch((e) => showToast(e.message, true));
}
