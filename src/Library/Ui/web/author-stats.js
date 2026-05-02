const currentUser = requireRole("AUTHOR");

let readsChart = null;
let ratingChart = null;
let genreChart = null;
let trendChart = null;

let lastTimeline = [];
let trendBucket = "week";

const DASHBOARD_PREFS_KEY = "author-stats-dashboard-prefs";

if (currentUser) {
    const welcome = document.getElementById("authorStatsWelcomeLine");
    if (welcome) {
        welcome.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("authorStatsLogoutBtn");
}

function formatMetricValue(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
        return "0";
    }
    return numeric.toLocaleString();
}

function formatAverageRating(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
        return "0.00";
    }
    return numeric.toFixed(2);
}

function clearCharts() {
    if (readsChart) {
        readsChart.destroy();
        readsChart = null;
    }
    if (ratingChart) {
        ratingChart.destroy();
        ratingChart = null;
    }
    if (genreChart) {
        genreChart.destroy();
        genreChart = null;
    }
    if (trendChart) {
        trendChart.destroy();
        trendChart = null;
    }
}

function drawReadsChart(books) {
    const canvas = document.getElementById("readsBarChart");
    if (!canvas) {
        return;
    }

    const top = [...books]
        .sort((a, b) => Number(b.readCount || 0) - Number(a.readCount || 0))
        .slice(0, 8);

    readsChart = new Chart(canvas, {
        type: "bar",
        data: {
            labels: top.map((item) => item.title || item.id),
            datasets: [
                {
                    label: "Reads",
                    data: top.map((item) => Number(item.readCount || 0)),
                    backgroundColor: "rgba(41, 128, 185, 0.75)",
                    borderColor: "rgba(41, 128, 185, 1)",
                    borderWidth: 1,
                    borderRadius: 6
                },
                {
                    label: "Borrows",
                    data: top.map((item) => Number(item.borrowCount || 0)),
                    backgroundColor: "rgba(230, 126, 34, 0.65)",
                    borderColor: "rgba(230, 126, 34, 1)",
                    borderWidth: 1,
                    borderRadius: 6
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: "top" }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: { precision: 0 }
                }
            }
        }
    });
}

function drawRatingPieChart(ratingBuckets) {
    const canvas = document.getElementById("ratingPieChart");
    if (!canvas) {
        return;
    }

    const labels = ["1-star", "2-star", "3-star", "4-star", "5-star"];
    const lookup = new Map((ratingBuckets || []).map((entry) => [entry.label, Number(entry.count || 0)]));
    const values = labels.map((label) => lookup.get(label) || 0);

    ratingChart = new Chart(canvas, {
        type: "pie",
        data: {
            labels,
            datasets: [
                {
                    data: values,
                    backgroundColor: [
                        "#d35454",
                        "#e67e22",
                        "#f1c40f",
                        "#2ecc71",
                        "#2980b9"
                    ],
                    borderColor: "#ffffff",
                    borderWidth: 2
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: "bottom" }
            }
        }
    });
}

function drawGenreBarChart(books) {
    const canvas = document.getElementById("genreBarChart");
    if (!canvas) {
        return;
    }

    const genreCounter = new Map();
    books.forEach((book) => {
        const genres = Array.isArray(book.genres) ? book.genres : [];
        if (genres.length === 0) {
            genreCounter.set("Unspecified", (genreCounter.get("Unspecified") || 0) + 1);
            return;
        }
        genres.forEach((genre) => {
            const key = String(genre || "Unspecified").trim() || "Unspecified";
            genreCounter.set(key, (genreCounter.get(key) || 0) + 1);
        });
    });

    const entries = [...genreCounter.entries()]
        .sort((a, b) => b[1] - a[1])
        .slice(0, 8);

    genreChart = new Chart(canvas, {
        type: "bar",
        data: {
            labels: entries.map(([genre]) => genre),
            datasets: [
                {
                    label: "Published Books",
                    data: entries.map(([, count]) => count),
                    backgroundColor: "rgba(22, 160, 133, 0.72)",
                    borderColor: "rgba(22, 160, 133, 1)",
                    borderWidth: 1,
                    borderRadius: 6
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: { precision: 0 }
                }
            }
        }
    });
}

function renderSummary(summary) {
    document.getElementById("metricBookCount").textContent = formatMetricValue(summary.bookCount);
    document.getElementById("metricReadCount").textContent = formatMetricValue(summary.totalReadCount);
    document.getElementById("metricBorrowCount").textContent = formatMetricValue(summary.totalBorrowCount);
    document.getElementById("metricActiveBorrowCount").textContent = formatMetricValue(summary.totalActiveBorrowCount);
    document.getElementById("metricReviewCount").textContent = formatMetricValue(summary.totalReviewCount);
    document.getElementById("metricAverageRating").textContent = formatAverageRating(summary.overallAverageRating);
}

function isoWeekKey(d) {
    // ISO week: Thursday in current week decides the year.
    const date = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const dayNum = (date.getUTCDay() + 6) % 7; // Mon=0
    date.setUTCDate(date.getUTCDate() - dayNum + 3);
    const firstThursday = new Date(Date.UTC(date.getUTCFullYear(), 0, 4));
    const week = 1 + Math.round(((date - firstThursday) / 86400000 - 3 + ((firstThursday.getUTCDay() + 6) % 7)) / 7);
    return `${date.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}

function bucketTimeline(timeline, bucket) {
    const buckets = new Map();
    (timeline || []).forEach((entry) => {
        if (!entry || !entry.date) return;
        const d = new Date(`${entry.date}T00:00:00Z`);
        if (Number.isNaN(d.getTime())) return;
        let key;
        if (bucket === "month") {
            key = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
        } else {
            key = isoWeekKey(d);
        }
        buckets.set(key, (buckets.get(key) || 0) + Number(entry.count || 0));
    });
    return [...buckets.entries()].sort((a, b) => a[0].localeCompare(b[0]));
}

function drawTrendChart(timeline) {
    const canvas = document.getElementById("trendChart");
    const status = document.getElementById("trendStatus");
    if (!canvas) return;

    if (trendChart) {
        trendChart.destroy();
        trendChart = null;
    }

    const points = bucketTimeline(timeline, trendBucket);
    if (points.length === 0) {
        if (status) status.textContent = "No borrow data yet.";
        canvas.style.display = "none";
        return;
    }
    canvas.style.display = "";
    if (status) status.textContent = "";

    trendChart = new Chart(canvas, {
        type: "line",
        data: {
            labels: points.map(([k]) => k),
            datasets: [{
                label: trendBucket === "month" ? "Borrows / Month" : "Borrows / Week",
                data: points.map(([, v]) => v),
                borderColor: "#8e44ad",
                backgroundColor: "rgba(142,68,173,0.2)",
                fill: true,
                tension: 0.25
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
        }
    });
}

function loadDashboardPrefs() {
    try {
        const raw = localStorage.getItem(DASHBOARD_PREFS_KEY);
        return raw ? JSON.parse(raw) : {};
    } catch (e) {
        return {};
    }
}

function saveDashboardPrefs(prefs) {
    try {
        localStorage.setItem(DASHBOARD_PREFS_KEY, JSON.stringify(prefs));
    } catch (e) {
        // ignore quota
    }
}

function applyDashboardVisibility(section, visible) {
    document.querySelectorAll(`[data-section="${section}"]`).forEach((el) => {
        if (el.tagName === "INPUT") return;
        // For metric ids, we want to hide the parent .metric-card; the article itself has data-section.
        el.style.display = visible ? "" : "none";
    });
}

function initDashboardPrefs() {
    const prefs = loadDashboardPrefs();
    document.querySelectorAll('#dashboardPrefs input[type="checkbox"]').forEach((cb) => {
        const section = cb.dataset.section;
        if (Object.prototype.hasOwnProperty.call(prefs, section)) {
            cb.checked = !!prefs[section];
        }
        applyDashboardVisibility(section, cb.checked);
        cb.addEventListener("change", () => {
            const all = loadDashboardPrefs();
            all[section] = cb.checked;
            saveDashboardPrefs(all);
            applyDashboardVisibility(section, cb.checked);
        });
    });
}

document.querySelectorAll('#trendBucketStrip .tab-btn').forEach((btn) => {
    btn.addEventListener("click", () => {
        trendBucket = btn.dataset.bucket || "week";
        document.querySelectorAll('#trendBucketStrip .tab-btn').forEach((b) => {
            b.classList.toggle("active", b === btn);
        });
        drawTrendChart(lastTimeline);
    });
});

document.getElementById("exportStatsCsvBtn")?.addEventListener("click", async () => {
    try {
        const blob = await fetchProtectedBlob("/api/author/stats-export");
        const blobUrl = URL.createObjectURL(blob);
        const filename = `author-stats-${new Date().toISOString().slice(0, 10)}.csv`;
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

async function refreshAuthorStats() {
    const status = document.getElementById("authorStatsStatus");
    if (!status) {
        return;
    }

    status.textContent = "Loading author analytics...";

    const payload = await api("/api/author/published-stats");
    const summary = payload?.summary || {};
    const books = Array.isArray(payload?.books) ? payload.books : [];
    const ratingBuckets = Array.isArray(payload?.ratingBuckets) ? payload.ratingBuckets : [];
    lastTimeline = Array.isArray(payload?.borrowsTimeline) ? payload.borrowsTimeline : [];

    renderSummary(summary);
    clearCharts();

    if (books.length === 0) {
        status.textContent = "No published books found. Publish books to unlock analytics.";
        drawTrendChart(lastTimeline);
        return;
    }

    status.textContent = `Analytics updated for ${books.length} published book(s).`;

    drawReadsChart(books);
    drawRatingPieChart(ratingBuckets);
    drawGenreBarChart(books);
    drawTrendChart(lastTimeline);
}

document.getElementById("refreshAuthorStatsBtn")?.addEventListener("click", () => {
    refreshAuthorStats().catch((error) => {
        const status = document.getElementById("authorStatsStatus");
        if (status) {
            status.textContent = "Failed to load author analytics.";
        }
        showToast(error.message || "Failed to load analytics.", true);
    });
});

window.addEventListener("beforeunload", () => {
    clearCharts();
});

if (currentUser) {
    initDashboardPrefs();
    refreshAuthorStats().catch((error) => {
        const status = document.getElementById("authorStatsStatus");
        if (status) {
            status.textContent = "Failed to load author analytics.";
        }
        showToast(error.message || "Failed to load analytics.", true);
    });
}
