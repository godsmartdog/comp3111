const currentUser = getCurrentUser();
if (!currentUser || !["STUDENT", "STAFF"].includes(currentUser.role)) {
    window.location.href = rolePage(currentUser?.role || "");
}

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    const backPortalLink = document.getElementById("backPortalLink");
    if (backPortalLink) {
        backPortalLink.href = currentUser.role === "STAFF" ? "mainStaff.html" : "mainStudent.html";
    }

    attachLogout("logoutBtn");
}

let historyItems = [];

function formatDuration(minutes) {
    const value = Math.max(0, Math.round(Number(minutes || 0)));
    if (value <= 0) {
        return "0 min";
    }
    return `${value} min`;
}

function progressLabel(item) {
    const bookmark = Number(item.bookmarkPage || 0);
    const highlightCount = Number(item.highlightCount || 0);
    const updatedAt = formatDateOnly(item.progressUpdatedAt || "");

    if (bookmark <= 0 && highlightCount <= 0) {
        return "Not started";
    }

    const parts = [`Bookmark :page ${bookmark}`];
    parts.push(`${highlightCount} highlight(s)`);
    if (updatedAt) {
        parts.push(`updated ${updatedAt}`);
    }
    return parts.join(" · ");
}

function buildQueryParams() {
    const params = new URLSearchParams();
    const keyword = document.getElementById("historyKeyword")?.value.trim() || "";
    const author = document.getElementById("historyAuthor")?.value.trim() || "";
    const genre = document.getElementById("historyGenre")?.value.trim() || "";
    const borrowDateFrom = document.getElementById("borrowDateFrom")?.value || "";
    const borrowDateTo = document.getElementById("borrowDateTo")?.value || "";
    const returnDateFrom = document.getElementById("returnDateFrom")?.value || "";
    const returnDateTo = document.getElementById("returnDateTo")?.value || "";
    const sortBy = document.getElementById("historySortBy")?.value || "borrowDate";
    const sortDir = document.getElementById("historySortDir")?.value || "desc";

    if (keyword) params.set("q", keyword);
    if (author) params.set("author", author);
    if (genre) params.set("genre", genre);
    if (borrowDateFrom) params.set("borrowDateFrom", borrowDateFrom);
    if (borrowDateTo) params.set("borrowDateTo", borrowDateTo);
    if (returnDateFrom) params.set("returnDateFrom", returnDateFrom);
    if (returnDateTo) params.set("returnDateTo", returnDateTo);
    if (sortBy) params.set("sortBy", sortBy);
    if (sortDir) params.set("sortDir", sortDir);

    return params;
}

function renderHistory() {
    const body = document.getElementById("historyBody");
    const status = document.getElementById("historyStatus");
    if (!body || !status) {
        return;
    }

    body.innerHTML = "";

    if (!Array.isArray(historyItems) || historyItems.length === 0) {
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="7" class="muted">No reading history found for the selected filters.</td>';
        body.appendChild(row);
        status.textContent = "No reading history found.";
        return;
    }

    status.textContent = `Showing ${historyItems.length} reading history record(s).`;
    historyItems.forEach((item) => {
        const row = document.createElement("tr");
        const genres = Array.isArray(item.genres) && item.genres.length > 0 ? item.genres.join(", ") : "-";
        const author = item.authorFullName || item.authorUsername || "-";
        const returnDateRaw = item.returnedDate || "";
        const returnDate = returnDateRaw ? formatDateOnly(returnDateRaw) : (item.returned ? "Returned" : "-");

        row.innerHTML = `
            <td>${item.bookTitle || ""}</td>
            <td>${author}</td>
            <td>${genres}</td>
            <td>${formatDateOnly(item.borrowDate || "")}</td>
            <td>${returnDate}</td>
            <td>${formatDuration(item.readingDurationMinutes)}</td>
            <td>${progressLabel(item)}</td>
        `;
        body.appendChild(row);
    });
}

async function refreshHistory() {
    const status = document.getElementById("historyStatus");
    if (status) {
        status.textContent = "Loading reading history...";
    }

    const params = buildQueryParams();
    const query = params.toString();
    const items = await api(query ? `/api/borrows/history?${query}` : "/api/borrows/history");
    historyItems = Array.isArray(items) ? items : [];
    renderHistory();
}

document.getElementById("applyHistoryFiltersBtn")?.addEventListener("click", () => {
    refreshHistory().catch((error) => showToast(error.message, true));
});

document.getElementById("resetHistoryFiltersBtn")?.addEventListener("click", () => {
    const fields = ["historyKeyword", "historyAuthor", "historyGenre", "borrowDateFrom", "borrowDateTo", "returnDateFrom", "returnDateTo"];
    fields.forEach((id) => {
        const field = document.getElementById(id);
        if (field) {
            field.value = "";
        }
    });

    const sortBy = document.getElementById("historySortBy");
    const sortDir = document.getElementById("historySortDir");
    if (sortBy) {
        sortBy.value = "borrowDate";
    }
    if (sortDir) {
        sortDir.value = "desc";
    }

    refreshHistory().catch((error) => showToast(error.message, true));
});

if (currentUser) {
    refreshHistory().catch((error) => showToast(error.message, true));
}
