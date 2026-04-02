const currentUser = requireRole("LIBRARIAN");
let rejectedItems = [];

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function safeText(value) {
    const normalized = String(value || "").trim();
    return normalized || "-";
}

function matchesText(value, filterText) {
    if (!filterText) {
        return true;
    }
    return String(value || "").toLowerCase().includes(filterText.toLowerCase());
}

function matchesGenres(genres, filterText) {
    if (!filterText) {
        return true;
    }
    return Array.isArray(genres) && genres.some((genre) => String(genre || "").toLowerCase().includes(filterText.toLowerCase()));
}

function applyRejectedFilters(items) {
    const keyword = document.getElementById("rejectedSearchInput")?.value?.trim() || "";
    const genreFilter = document.getElementById("rejectedGenreFilter")?.value?.trim() || "";
    const dateFilter = document.getElementById("rejectedDateFilter")?.value?.trim() || "";

    return items.filter((item) => {
        const keywordMatch = !keyword
            || matchesText(item.title, keyword)
            || matchesText(item.authorFullName, keyword)
            || matchesText(item.authorUsername, keyword);
        const genreMatch = matchesGenres(item.genres, genreFilter);
        const dateMatch = !dateFilter || String(item.submittedDate || "").trim() === dateFilter;
        return keywordMatch && genreMatch && dateMatch;
    });
}

function renderRejected(items) {
    const body = document.getElementById("rejectedBody");
    if (!body) {
        return;
    }

    body.innerHTML = "";
    if (!Array.isArray(items) || items.length === 0) {
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="7" class="muted">No rejected submissions found.</td>';
        body.appendChild(row);
        return;
    }

    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${safeText(item.title)}</td>
            <td>${safeText(item.authorFullName)}</td>
            <td>${Array.isArray(item.genres) && item.genres.length > 0 ? item.genres.map((genre) => safeText(genre)).join(", ") : "-"}</td>
            <td>${safeText(item.submittedDate)}</td>
            <td>${safeText(item.fileName)}</td>
            <td>${safeText(item.rejectionReason)}</td>
            <td>${safeText(item.librarianComment)}</td>
        `;
        body.appendChild(row);
    });
}

function renderFilteredRejected() {
    renderRejected(applyRejectedFilters(rejectedItems));
}

async function refreshRejected() {
    const sortDir = document.getElementById("rejectedSortDirFilter")?.value || "desc";
    const query = new URLSearchParams();
    query.set("status", "rejected");
    query.set("sortBy", "submittedDate");
    query.set("sortDir", sortDir);

    const items = await api(`/api/librarian/pending?${query.toString()}`);
    rejectedItems = Array.isArray(items) ? items : [];
    renderFilteredRejected();
}

document.getElementById("applyRejectedFiltersBtn")?.addEventListener("click", () => {
    renderFilteredRejected();
});

document.getElementById("resetRejectedFiltersBtn")?.addEventListener("click", () => {
    const searchInput = document.getElementById("rejectedSearchInput");
    const genreInput = document.getElementById("rejectedGenreFilter");
    const dateInput = document.getElementById("rejectedDateFilter");
    const sortDir = document.getElementById("rejectedSortDirFilter");
    if (searchInput) {
        searchInput.value = "";
    }
    if (genreInput) {
        genreInput.value = "";
    }
    if (dateInput) {
        dateInput.value = "";
    }
    if (sortDir) {
        sortDir.value = "desc";
    }
    refreshRejected().catch((e) => showToast(e.message, true));
});

document.getElementById("refreshRejectedBtn")?.addEventListener("click", () => {
    refreshRejected().catch((e) => showToast(e.message, true));
});

["rejectedSearchInput", "rejectedGenreFilter", "rejectedDateFilter"].forEach((id) => {
    document.getElementById(id)?.addEventListener("input", () => {
        renderFilteredRejected();
    });
});

document.getElementById("rejectedSortDirFilter")?.addEventListener("change", () => {
    refreshRejected().catch((e) => showToast(e.message, true));
});

if (currentUser) {
    refreshRejected().catch((e) => showToast(e.message, true));
}
