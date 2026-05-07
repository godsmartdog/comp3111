const currentUser = requireRole("LIBRARIAN");

if (currentUser) {
    const welcome = document.getElementById("downloadStatsWelcomeLine");
    if (welcome) {
        welcome.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("downloadStatsLogoutBtn");
}

function formatCount(value) {
    const numeric = Number(value || 0);
    return Number.isFinite(numeric) ? numeric.toLocaleString() : "0";
}

function displayText(value, fallback = "--") {
    const text = String(value || "").trim();
    return text || fallback;
}

function renderCountTable(containerId, items, countLabel, emptyMessage) {
    const container = document.getElementById(containerId);
    if (!container) {
        return;
    }

    if (!Array.isArray(items) || items.length === 0) {
        container.innerHTML = `<p class="muted">${escapeHtml(emptyMessage)}</p>`;
        return;
    }

    container.innerHTML = `
        <div class="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>Name</th>
                    <th>${escapeHtml(countLabel)}</th>
                </tr>
                </thead>
                <tbody>
                    ${items.map((item) => `
                        <tr>
                            <td>${escapeHtml(item.name || "")}</td>
                            <td>${formatCount(item.count)}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        </div>
    `;
}

function renderTrendTable(items) {
    const container = document.getElementById("downloadTrendTable");
    if (!container) {
        return;
    }

    if (!Array.isArray(items) || items.length === 0) {
        container.innerHTML = '<p class="muted">No download trend data yet.</p>';
        return;
    }

    container.innerHTML = `
        <div class="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>Date</th>
                    <th>Downloaded Books</th>
                </tr>
                </thead>
                <tbody>
                    ${items.map((item) => `
                        <tr>
                            <td>${escapeHtml(item.date || "")}</td>
                            <td>${formatCount(item.count)}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        </div>
    `;
}

function renderBooksTable(books) {
    const container = document.getElementById("downloadedBooksTable");
    if (!container) {
        return;
    }

    if (!Array.isArray(books) || books.length === 0) {
        container.innerHTML = '<p class="muted">No downloaded book stats yet.</p>';
        return;
    }

    container.innerHTML = `
        <div class="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>Title</th>
                    <th>Author</th>
                    <th>Genres</th>
                    <th>Download Count</th>
                    <th>Request Count</th>
                    <th>Uploaded Date</th>
                    <th>Requesters</th>
                </tr>
                </thead>
                <tbody>
                    ${books.map((book) => {
                        const genres = Array.isArray(book.genres) && book.genres.length > 0
                            ? book.genres.join(", ")
                            : "--";
                        const requesters = Array.isArray(book.requesters) && book.requesters.length > 0
                            ? book.requesters.join(", ")
                            : "--";
                        return `
                            <tr>
                                <td>${escapeHtml(book.title || "")}</td>
                                <td>${escapeHtml(book.author || "")}</td>
                                <td>${escapeHtml(genres)}</td>
                                <td>${formatCount(book.downloadCount)}</td>
                                <td>${formatCount(book.requestCount)}</td>
                                <td>${escapeHtml(displayText(book.uploadedDate))}</td>
                                <td>${escapeHtml(requesters)}</td>
                            </tr>
                        `;
                    }).join("")}
                </tbody>
            </table>
        </div>
    `;
}

function renderDownloadStats(stats) {
    const summary = stats?.summary || {};
    const totalDownloaded = Number(summary.totalDownloadedBooks || 0);

    document.getElementById("totalDownloadedBooksMetric").textContent = formatCount(totalDownloaded);
    document.getElementById("uniqueBooksMetric").textContent = formatCount(summary.uniqueBooks);
    document.getElementById("uniqueRequestersMetric").textContent = formatCount(summary.uniqueRequesters);
    document.getElementById("topGenreMetric").textContent = displayText(summary.topGenre, "--");
    document.getElementById("topAuthorMetric").textContent = displayText(summary.topAuthor, "--");

    renderCountTable("topDownloadedGenresTable", stats?.topGenres || [], "Downloaded Books", "No downloaded genres yet.");
    renderCountTable("topDownloadedAuthorsTable", stats?.topAuthors || [], "Downloaded Books", "No downloaded authors yet.");
    renderTrendTable(stats?.trend || []);
    renderBooksTable(stats?.books || []);
}

async function loadDownloadStats(options = {}) {
    const manual = Boolean(options.manual);
    const status = document.getElementById("downloadStatsStatus");
    if (status) {
        status.textContent = "Loading downloaded book stats...";
    }

    try {
        const stats = await api("/api/librarian/download-stats");
        renderDownloadStats(stats);
        const total = Number(stats?.summary?.totalDownloadedBooks || 0);
        if (status) {
            status.textContent = total > 0
                ? "Downloaded book stats loaded."
                : "No downloaded book stats yet.";
        }
        if (manual) {
            showToast("Downloaded book stats refreshed.", false);
        }
    } catch (error) {
        if (status) {
            status.textContent = error.message;
        }
        showToast(error.message, true);
    }
}

document.getElementById("refreshDownloadStatsBtn")?.addEventListener("click", () => {
    loadDownloadStats({ manual: true }).catch((error) => showToast(error.message, true));
});

if (currentUser) {
    loadDownloadStats().catch((error) => showToast(error.message, true));
}
