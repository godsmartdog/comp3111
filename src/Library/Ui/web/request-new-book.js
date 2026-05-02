const currentUser = getCurrentUser();
const allowedRoles = ["STUDENT", "STAFF"];

if (!currentUser || !currentUser.sessionId || !allowedRoles.includes(currentUser.role)) {
    window.location.href = currentUser?.role ? rolePage(currentUser.role) : "login.html";
}

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    const homeLink = document.getElementById("homeLink");
    if (homeLink) {
        homeLink.href = rolePage(currentUser.role);
        homeLink.textContent = `${currentUser.role.charAt(0)}${currentUser.role.slice(1).toLowerCase()} Main`;
    }

    attachLogout("logoutBtn");
}

function requestRowToHtml(item) {
    const genres = Array.isArray(item.genres) && item.genres.length > 0
        ? item.genres.map((genre) => `<span class="genre-badge">${genre}</span>`).join("")
        : '<span class="genre-badge genre-badge-empty">None</span>';
    const status = item.status || "PENDING";
    const statusLabel = status.toLowerCase();
    return `
        <tr${item.priority ? ' class="request-priority"' : ""}>
            <td>${item.priority ? "★" : ""}</td>
            <td>${item.title || ""}</td>
            <td>${item.authorName || ""}</td>
            <td><div class="genre-badge-group">${genres}</div></td>
            <td>${statusLabel}</td>
            <td>${item.requestedDate || ""}</td>
            <td>${item.approvedDate || ""}</td>
            <td>${item.uploadedDate || ""}</td>
            <td>${item.reason || ""}</td>
        </tr>
    `;
}

function getSelectedRequestGenres() {
    const genreSelect = document.getElementById("requestGenres");
    if (!genreSelect) {
        return [];
    }
    return Array.from(genreSelect.selectedOptions || [])
        .map((option) => option.value)
        .filter(Boolean);
}

async function loadRequests() {
    const body = document.getElementById("requestHistoryBody");
    if (!body) {
        return;
    }

    try {
        const items = await api("/api/book-requests");
        body.innerHTML = Array.isArray(items) && items.length > 0
            ? items.map(requestRowToHtml).join("")
            : `<tr><td colspan="8">No requests submitted yet.</td></tr>`;
    } catch (error) {
        if (error && error.status === 404) {
            body.innerHTML = `<tr><td colspan="8">No requests submitted yet.</td></tr>`;
            return;
        }
        throw error;
    }
}

function resetRequestForm() {
    document.getElementById("requestTitle").value = "";
    document.getElementById("requestAuthorName").value = "";
    const genreSelect = document.getElementById("requestGenres");
    if (genreSelect) {
        Array.from(genreSelect.options || []).forEach((option) => {
            option.selected = false;
        });
    }
    document.getElementById("requestReason").value = "";
}

async function submitRequest(event) {
    event.preventDefault();
    const status = document.getElementById("requestStatus");
    const title = document.getElementById("requestTitle").value.trim();
    const authorName = document.getElementById("requestAuthorName").value.trim();
    const genres = getSelectedRequestGenres();
    const reason = document.getElementById("requestReason").value.trim();

    if (genres.length === 0) {
        const message = "Please select at least one genre.";
        if (status) {
            status.textContent = message;
        }
        showToast(message, true);
        return;
    }

    try {
        const response = await api("/api/book-requests", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ title, authorName, genres: genres.join(","), reason })
        });

        if (status) {
            status.textContent = response?.message || "Request submitted successfully.";
        }
        showToast(response?.message || "Request submitted successfully.", false);
        resetRequestForm();
        await loadRequests();
    } catch (error) {
        if (status) {
            status.textContent = error.message;
        }
        showToast(error.message, true);
    }
}

document.getElementById("requestForm")?.addEventListener("submit", submitRequest);
document.getElementById("resetRequestBtn")?.addEventListener("click", resetRequestForm);
document.getElementById("refreshRequestsBtn")?.addEventListener("click", () => {
    loadRequests().catch((error) => showToast(error.message, true));
});

if (currentUser) {
    loadRequests().catch((error) => showToast(error.message, true));
}