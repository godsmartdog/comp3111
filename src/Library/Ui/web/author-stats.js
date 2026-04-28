const currentUser = requireRole("AUTHOR");

let readsChart = null;
let ratingChart = null;
let genreChart = null;

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

    renderSummary(summary);
    clearCharts();

    if (books.length === 0) {
        status.textContent = "No published books found. Publish books to unlock analytics.";
        return;
    }

    status.textContent = `Analytics updated for ${books.length} published book(s).`;

    drawReadsChart(books);
    drawRatingPieChart(ratingBuckets);
    drawGenreBarChart(books);
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
    refreshAuthorStats().catch((error) => {
        const status = document.getElementById("authorStatsStatus");
        if (status) {
            status.textContent = "Failed to load author analytics.";
        }
        showToast(error.message || "Failed to load analytics.", true);
    });
}
