const currentUser = requireRole("LIBRARIAN");

if (currentUser) {
    const welcome = document.getElementById("requestStatsWelcomeLine");
    if (welcome) {
        welcome.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("requestStatsLogoutBtn");
}

function formatCount(value) {
    const numeric = Number(value || 0);
    return Number.isFinite(numeric) ? numeric.toLocaleString() : "0";
}

function formatStatusLabel(status) {
    return String(status || "")
        .toLowerCase()
        .split("_")
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(" ");
}

function renderCountTable(containerId, items, emptyMessage) {
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
                    <th>Requests</th>
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

function renderStatusTable(totalByStatus) {
    const container = document.getElementById("statusStatsTable");
    if (!container) {
        return;
    }

    const entries = Object.entries(totalByStatus || {})
        .sort(([left], [right]) => left.localeCompare(right, undefined, { sensitivity: "base" }));

    if (entries.length === 0) {
        container.innerHTML = `<p class="muted">No request analytics available yet.</p>`;
        return;
    }

    container.innerHTML = `
        <div class="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>Status</th>
                    <th>Requests</th>
                </tr>
                </thead>
                <tbody>
                    ${entries.map(([status, count]) => `
                        <tr>
                            <td>${escapeHtml(formatStatusLabel(status))}</td>
                            <td>${formatCount(count)}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        </div>
    `;
}

function renderRequestStats(stats) {
    const total = Number(stats?.totalRequests || 0);
    document.getElementById("totalRequestsMetric").textContent = formatCount(total);
    renderStatusTable(stats?.totalByStatus || {});
    renderCountTable("topGenresTable", stats?.topGenres || [], "No requested genres yet.");
    renderCountTable("topAuthorsTable", stats?.topAuthors || [], "No requested authors yet.");
}

async function loadRequestStats() {
    const status = document.getElementById("requestStatsStatus");
    if (status) {
        status.textContent = "Loading request analytics...";
    }

    try {
        const stats = await api("/api/librarian/request-stats");
        renderRequestStats(stats);
        if (status) {
            status.textContent = Number(stats?.totalRequests || 0) > 0
                ? "Request analytics loaded."
                : "No request analytics available yet.";
        }
    } catch (error) {
        if (status) {
            status.textContent = error.message;
        }
        showToast(error.message, true);
    }
}

document.getElementById("refreshRequestStatsBtn")?.addEventListener("click", () => {
    loadRequestStats().catch((error) => showToast(error.message, true));
});

if (currentUser) {
    loadRequestStats().catch((error) => showToast(error.message, true));
}