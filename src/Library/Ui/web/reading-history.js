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
let allHistoryItems = [];
let genreChart = null;
let durationChart = null;

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

    const parts = [`Bookmark page ${bookmark}`];
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

function renderInsights() {
    const status = document.getElementById("insightsStatus");
    const genreCanvas = document.getElementById("genrePieChart");
    const durationCanvas = document.getElementById("durationLineChart");
    if (!genreCanvas || !durationCanvas) return;

    if (typeof Chart === "undefined") {
        if (status) status.textContent = "Charts unavailable (Chart.js failed to load).";
        return;
    }

    if (!Array.isArray(historyItems) || historyItems.length === 0) {
        if (status) status.textContent = "No data for the current filter.";
        genreChart?.destroy();
        durationChart?.destroy();
        genreChart = null;
        durationChart = null;
        const gctx = genreCanvas.getContext("2d");
        gctx.clearRect(0, 0, genreCanvas.width, genreCanvas.height);
        const dctx = durationCanvas.getContext("2d");
        dctx.clearRect(0, 0, durationCanvas.width, durationCanvas.height);
        return;
    }

    if (status) status.textContent = "";

    // Genre pie chart
    const genreCounts = {};
    historyItems.forEach((item) => {
        if (Array.isArray(item.genres)) {
            item.genres.forEach((g) => {
                if (!g) return;
                genreCounts[g] = (genreCounts[g] || 0) + 1;
            });
        }
    });
    const genreLabels = Object.keys(genreCounts);
    const genreData = genreLabels.map((g) => genreCounts[g]);

    genreChart?.destroy();
    if (genreLabels.length > 0) {
        genreChart = new Chart(genreCanvas.getContext("2d"), {
            type: "doughnut",
            data: {
                labels: genreLabels,
                datasets: [{ data: genreData }]
            },
            options: {
                responsive: false,
                plugins: {
                    legend: { position: "bottom" },
                    title: { display: true, text: "Genre Distribution" }
                }
            }
        });
    } else {
        genreChart = null;
    }

    // Duration line chart
    const points = historyItems
        .map((item) => {
            const minutes = Number(item.readingDurationMinutes || 0);
            return { date: item.borrowDate || "", minutes };
        })
        .filter((p) => p.date && p.minutes > 0)
        .sort((a, b) => a.date.localeCompare(b.date));

    durationChart?.destroy();
    if (points.length > 0) {
        durationChart = new Chart(durationCanvas.getContext("2d"), {
            type: "line",
            data: {
                labels: points.map((p) => p.date),
                datasets: [{
                    label: "Reading Duration (min)",
                    data: points.map((p) => p.minutes),
                    borderColor: "#3b82f6",
                    backgroundColor: "rgba(59,130,246,0.2)",
                    fill: true,
                    tension: 0.2
                }]
            },
            options: {
                responsive: false,
                scales: {
                    y: { beginAtZero: true, title: { display: true, text: "Minutes" } },
                    x: { title: { display: true, text: "Borrow Date" } }
                }
            }
        });
    } else {
        durationChart = null;
        if (status) status.textContent = "No duration data to plot.";
    }
}

function computeBadges(items) {
    const list = Array.isArray(items) ? items : [];
    const totalRead = list.length;
    const distinctGenres = new Set();
    list.forEach((item) => {
        if (Array.isArray(item.genres)) {
            item.genres.forEach((g) => { if (g) distinctGenres.add(g); });
        }
    });
    let speedReader = false;
    list.forEach((item) => {
        if (!item.borrowDate || !item.returnedDate) return;
        const borrow = new Date(item.borrowDate);
        const ret = new Date(item.returnedDate);
        if (Number.isNaN(borrow.getTime()) || Number.isNaN(ret.getTime())) return;
        const diffDays = (ret - borrow) / (1000 * 60 * 60 * 24);
        if (diffDays >= 0 && diffDays <= 7) speedReader = true;
    });
    const totalMinutes = list.reduce((sum, item) => sum + Number(item.readingDurationMinutes || 0), 0);

    return [
        { id: "bronze", icon: "🥉", label: "Bronze Reader", earned: totalRead >= 1, threshold: "Read 1 book", progress: `${totalRead}/1` },
        { id: "silver", icon: "🥈", label: "Silver Reader", earned: totalRead >= 5, threshold: "Read 5 books", progress: `${totalRead}/5` },
        { id: "gold", icon: "🥇", label: "Gold Reader", earned: totalRead >= 10, threshold: "Read 10 books", progress: `${totalRead}/10` },
        { id: "platinum", icon: "💎", label: "Platinum Reader", earned: totalRead >= 25, threshold: "Read 25 books", progress: `${totalRead}/25` },
        { id: "variety", icon: "📚", label: "Variety Seeker", earned: distinctGenres.size >= 3, threshold: "3 distinct genres", progress: `${distinctGenres.size}/3` },
        { id: "speed", icon: "🚀", label: "Speed Reader", earned: speedReader, threshold: "Return within 7 days", progress: speedReader ? "Earned" : "Not yet" },
        { id: "marathon", icon: "🏃", label: "Marathon Reader", earned: totalMinutes >= 60, threshold: "60 min total reading", progress: `${totalMinutes}/60 min` }
    ];
}

function renderBadges() {
    const grid = document.getElementById("badgesGrid");
    if (!grid) return;
    grid.innerHTML = "";
    const badges = computeBadges(allHistoryItems);
    badges.forEach((badge) => {
        const card = document.createElement("div");
        card.className = `badge-card ${badge.earned ? "badge-earned" : "badge-locked"}`;
        card.innerHTML = `
            <div class="badge-icon">${badge.icon}</div>
            <div class="badge-label">${escapeHtml(badge.label)}</div>
            <div class="badge-status">${escapeHtml(badge.earned ? "Earned" : "Locked")}</div>
            <div class="badge-status">${escapeHtml(badge.threshold)}</div>
            <div class="badge-status">${escapeHtml(badge.progress)}</div>
        `;
        grid.appendChild(card);
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
    renderInsights();
}

async function refreshAllHistoryForBadges() {
    try {
        const items = await api("/api/borrows/history");
        allHistoryItems = Array.isArray(items) ? items : [];
    } catch (e) {
        allHistoryItems = [];
    }
    renderBadges();
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

document.getElementById("exportHistoryCsvBtn")?.addEventListener("click", async () => {
    try {
        const params = buildQueryParams();
        const query = params.toString();
        const url = query
            ? `/api/student/reading-history-export?${query}`
            : "/api/student/reading-history-export";
        const blob = await fetchProtectedBlob(url);
        const blobUrl = URL.createObjectURL(blob);
        const filename = `reading-history-${new Date().toISOString().slice(0, 10)}.csv`;
        const a = document.createElement("a");
        a.href = blobUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    } catch (error) {
        showToast(error.message || "Export failed", true);
    }
});

if (currentUser) {
    refreshHistory()
        .then(() => refreshAllHistoryForBadges())
        .catch((error) => showToast(error.message, true));
}
