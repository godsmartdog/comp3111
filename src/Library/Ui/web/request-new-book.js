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

function readAlternativeInputs() {
    return {
        title: document.getElementById("requestTitle")?.value.trim() || "",
        author: document.getElementById("requestAuthorName")?.value.trim() || "",
        genres: getSelectedRequestGenres()
    };
}

function renderAlternativeSuggestions(items) {
    const list = document.getElementById("alternativesList");
    if (!list) {
        return;
    }

    if (!Array.isArray(items) || items.length === 0) {
        list.innerHTML = `<p>No similar available books found. You can continue submitting your request.</p>`;
        return;
    }

    list.innerHTML = `
        <div class="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>Title</th>
                    <th>Author</th>
                    <th>Genres</th>
                    <th>Availability</th>
                    <th>Match</th>
                    <th>Why</th>
                </tr>
                </thead>
                <tbody>
                    ${items.map(alternativeRowToHtml).join("")}
                </tbody>
            </table>
        </div>
    `;
}

function alternativeRowToHtml(item) {
    const genres = Array.isArray(item.genres) && item.genres.length > 0
        ? item.genres.map((genre) => `<span class="genre-badge">${escapeHtml(genre)}</span>`).join("")
        : '<span class="genre-badge genre-badge-empty">None</span>';
    const reasons = Array.isArray(item.reasons) && item.reasons.length > 0
        ? item.reasons.map(escapeHtml).join("; ")
        : "Matched request details";

    return `
        <tr>
            <td>${escapeHtml(item.title || "")}</td>
            <td>${escapeHtml(item.author || "")}</td>
            <td><div class="genre-badge-group">${genres}</div></td>
            <td>${escapeHtml(item.availability || (item.available ? "Available" : "Not available"))}</td>
            <td>${Number(item.score || 0)}</td>
            <td>${reasons}</td>
        </tr>
    `;
}

async function findAlternativeBooks() {
    const status = document.getElementById("alternativesStatus");
    const list = document.getElementById("alternativesList");
    const { title, author, genres } = readAlternativeInputs();

    if (!title && !author && genres.length === 0) {
        const message = "Enter a title, author, or genres to see suggestions.";
        if (status) {
            status.textContent = message;
        }
        if (list) {
            list.innerHTML = "";
        }
        showToast(message, true);
        return;
    }

    const params = new URLSearchParams();
    if (title) {
        params.set("title", title);
    }
    if (author) {
        params.set("author", author);
    }
    if (genres.length > 0) {
        params.set("genres", genres.join(","));
    }
    params.set("limit", "5");

    if (status) {
        status.textContent = "Finding similar available books...";
    }

    try {
        const items = await api(`/api/book-requests/alternatives?${params.toString()}`);
        renderAlternativeSuggestions(items);
        if (status) {
            status.textContent = Array.isArray(items) && items.length > 0
                ? `${items.length} similar available book${items.length === 1 ? "" : "s"} found.`
                : "No similar available books found. You can continue submitting your request.";
        }
    } catch (error) {
        if (status) {
            status.textContent = error.message;
        }
        if (list) {
            list.innerHTML = "";
        }
        showToast(error.message, true);
    }
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
    document.getElementById("alternativesStatus").textContent = "Enter a title, author, or genres to see suggestions.";
    document.getElementById("alternativesList").innerHTML = "";
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
        const message = String(error.message || "").includes("already submitted a request")
            ? "You already submitted a request for this book."
            : error.message;
        if (status) {
            status.textContent = message;
        }
        showToast(message, true);
    }
}

document.getElementById("requestForm")?.addEventListener("submit", submitRequest);
document.getElementById("findAlternativesBtn")?.addEventListener("click", () => {
    findAlternativeBooks().catch((error) => showToast(error.message, true));
});
document.getElementById("resetRequestBtn")?.addEventListener("click", resetRequestForm);
document.getElementById("refreshRequestsBtn")?.addEventListener("click", () => {
    loadRequests().catch((error) => showToast(error.message, true));
});

if (currentUser) {
    loadRequests().catch((error) => showToast(error.message, true));
}