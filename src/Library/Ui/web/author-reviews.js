const currentUser = requireRole("AUTHOR");
let publishedBooks = [];
let selectedBook = null;
let selectedBookReviews = [];
let sentimentChart = null;
let ratingRecapChart = null;

const SENTIMENT_POSITIVE = ["good","great","excellent","amazing","wonderful","love","loved","best","fantastic","awesome","brilliant","enjoyed","helpful","insightful","engaging","perfect","superb","favourite","favorite","masterpiece","interesting","fun","recommend"];
const SENTIMENT_NEGATIVE = ["bad","terrible","awful","worst","boring","hate","hated","poor","disappointing","confusing","weak","lame","slow","dull","mediocre","frustrating","wasted","horrible","skip","avoid"];
const POS_SET = new Set(SENTIMENT_POSITIVE);
const NEG_SET = new Set(SENTIMENT_NEGATIVE);

const REPLY_TEMPLATES = [
    "Thank you for your kind feedback. We're glad you enjoyed the book!",
    "Thank you for the honest review. We appreciate your thoughts and will consider this for future work.",
    "Thank you for taking the time to share your perspective. We'd love to hear more — feel free to reach out."
];

function classifySentiment(text) {
    const tokens = String(text || "").toLowerCase().split(/\W+/).filter(Boolean);
    let pos = 0;
    let neg = 0;
    tokens.forEach((t) => {
        if (POS_SET.has(t)) pos++;
        if (NEG_SET.has(t)) neg++;
    });
    if (pos > neg) return "positive";
    if (neg > pos) return "negative";
    return "neutral";
}

function sentimentBadgeHtml(item) {
    // Prefer backend deterministic sentiment classification if available.
    let s = (item.sentiment || "").toLowerCase().trim();
    if (!s || !["positive", "neutral", "negative"].includes(s)) {
        // Fallback to client-side keyword classification.
        s = classifySentiment(item.reviewText || "");
    }
    const label = s.charAt(0).toUpperCase() + s.slice(1);
    return `<span class="sentiment-badge sentiment-${s}">${label}</span>`;
}

function renderFeedbackAnalytics(items) {
    const status = document.getElementById("feedbackAnalyticsStatus");
    const sentCanvas = document.getElementById("sentimentBreakdownChart");
    const ratingCanvas = document.getElementById("ratingDistributionRecap");
    if (!sentCanvas || !ratingCanvas) return;

    if (sentimentChart) { sentimentChart.destroy(); sentimentChart = null; }
    if (ratingRecapChart) { ratingRecapChart.destroy(); ratingRecapChart = null; }

    if (typeof Chart === "undefined") {
        if (status) status.textContent = "Charts unavailable (Chart.js failed to load).";
        return;
    }

    if (!Array.isArray(items) || items.length === 0) {
        if (status) status.textContent = "No review data yet.";
        sentCanvas.style.display = "none";
        ratingCanvas.style.display = "none";
        return;
    }

    sentCanvas.style.display = "";
    ratingCanvas.style.display = "";
    if (status) status.textContent = `Aggregated across ${items.length} review(s) for the selected book.`;

    const counts = { positive: 0, neutral: 0, negative: 0 };
    items.forEach((it) => {
        // Use backend deterministic sentiment if available, otherwise fall back to client-side classification.
        let sentiment = (it.sentiment || "").toLowerCase().trim();
        if (!sentiment || !["positive", "neutral", "negative"].includes(sentiment)) {
            sentiment = classifySentiment(it.reviewText || "");
        }
        counts[sentiment]++;
    });

    sentimentChart = new Chart(sentCanvas, {
        type: "doughnut",
        data: {
            labels: ["Positive", "Neutral", "Negative"],
            datasets: [{
                data: [counts.positive, counts.neutral, counts.negative],
                backgroundColor: ["#28a745", "#6c757d", "#dc3545"]
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: "bottom" } }
        }
    });

    const ratingBuckets = [0, 0, 0, 0, 0];
    items.forEach((it) => {
        const r = Math.max(1, Math.min(5, Number(it.rating || 0)));
        if (r >= 1 && r <= 5) ratingBuckets[r - 1]++;
    });

    ratingRecapChart = new Chart(ratingCanvas, {
        type: "bar",
        data: {
            labels: ["1★", "2★", "3★", "4★", "5★"],
            datasets: [{
                label: "Reviews",
                data: ratingBuckets,
                backgroundColor: ["#d35454", "#e67e22", "#f1c40f", "#2ecc71", "#2980b9"]
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
        }
    });
}

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function formatAverageRating(item) {
    const rating = Number(item?.averageRating);
    const count = Number(item?.reviewCount || 0);
    if (!Number.isFinite(rating) || count <= 0) {
        return "-";
    }
    return `${rating.toFixed(2)} (${count})`;
}

function formatReviewText(item) {
    const parts = [item?.reviewText || ""];
    if (item?.replyText) {
        parts.push(`Reply: ${item.replyText}`);
    }
    if (item?.flagged) {
        parts.push(`Reported${item.flagReason ? `: ${item.flagReason}` : ""}`);
    }
    return escapeHtml(parts.filter(Boolean).join(" | "));
}

function renderPublishedBooks(items) {
    const body = document.getElementById("publishedBooksBody");
    const status = document.getElementById("bookStatus");
    if (!body || !status) {
        return;
    }

    body.innerHTML = "";
    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No published books found.";
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="5" class="muted">No published books yet.</td>';
        body.appendChild(row);
        renderSelectedBookReviews([]);
        return;
    }

    status.textContent = `Found ${items.length} published book(s).`;

    items.forEach((item) => {
        const row = document.createElement("tr");
        const actionCell = document.createElement("td");
        row.innerHTML = `
            <td>${escapeHtml(item.title || "")}</td>
            <td>${Array.isArray(item.genres) ? escapeHtml(item.genres.join(", ")) : ""}</td>
            <td>${escapeHtml(formatDateOnly(item.publishDate || ""))}</td>
            <td>${formatAverageRating(item)}</td>
        `;

        const button = document.createElement("button");
        button.className = "secondary";
        button.type = "button";
        button.textContent = "View Reviews";
        button.addEventListener("click", () => {
            selectedBook = item;
            const heading = document.getElementById("selectedBookHeading");
            if (heading) {
                heading.textContent = `Reviews for ${item.title || item.id}`;
            }
            loadReviewsForSelectedBook(item.id).catch((error) => showToast(error.message, true));
        });

        actionCell.appendChild(button);
        row.appendChild(actionCell);
        body.appendChild(row);
    });
}

function renderSelectedBookReviews(items) {
    const body = document.getElementById("authorReviewsBody");
    const status = document.getElementById("reviewStatus");
    if (!body || !status) {
        return;
    }

    body.innerHTML = "";
    if (!selectedBook) {
        status.textContent = "Select a published book to view its reviews.";
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="5" class="muted">No book selected.</td>';
        body.appendChild(row);
        renderFeedbackAnalytics([]);
        return;
    }

    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = `No reviews found for ${selectedBook.title || selectedBook.id}.`;
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="5" class="muted">No reviews available yet.</td>';
        body.appendChild(row);
        renderFeedbackAnalytics([]);
        return;
    }

    status.textContent = `Found ${items.length} review(s) for ${selectedBook.title || selectedBook.id}.`;

    items.forEach((item) => {
        const reviewerLabel = item.anonymous
            ? "Anonymous"
            : (item.reviewerFullName || item.username || "");
        const row = document.createElement("tr");
        const sentimentBadge = sentimentBadgeHtml(item);
        row.innerHTML = `
            <td>${escapeHtml(reviewerLabel)}</td>
            <td>${Number(item.rating || 0)}/5</td>
            <td>${Number(item.helpfulCount || 0)}</td>
            <td>${formatReviewText(item)} ${sentimentBadge}</td>
            <td>
                <div>${item.replyText ? `<strong>Reply:</strong> ${escapeHtml(item.replyText)}` : ""}</div>
                <div>${item.flagged ? `<strong>Reported</strong>${item.flagReason ? `: ${escapeHtml(item.flagReason)}` : ""}` : ""}</div>
                <div class="toolbar" style="margin-top:8px;">
                    <button class="secondary reply-btn" type="button">Reply</button>
                    <button class="secondary flag-btn" type="button">Flag</button>
                </div>
                <div class="reply-panel" style="display:none; margin-top:8px;">
                    <select class="reply-template-select">
                        <option value="" selected disabled>(Choose a template…)</option>
                        ${REPLY_TEMPLATES.map((t, i) => `<option value="${i}">${escapeHtml(t.length > 60 ? t.slice(0, 60) + "…" : t)}</option>`).join("")}
                    </select>
                    <textarea class="reply-textarea" rows="3" style="width:100%; margin-top:6px;" placeholder="Write your reply..."></textarea>
                    <div class="toolbar" style="margin-top:6px;">
                        <button class="reply-send-btn" type="button">Send Reply</button>
                        <button class="secondary reply-cancel-btn" type="button">Cancel</button>
                    </div>
                </div>
            </td>
        `;

        const replyBtn = row.querySelector(".reply-btn");
        const replyPanel = row.querySelector(".reply-panel");
        const replyTextarea = row.querySelector(".reply-textarea");
        const replyTemplateSelect = row.querySelector(".reply-template-select");
        const replySendBtn = row.querySelector(".reply-send-btn");
        const replyCancelBtn = row.querySelector(".reply-cancel-btn");

        replyBtn?.addEventListener("click", () => {
            if (!replyPanel) return;
            replyPanel.style.display = replyPanel.style.display === "none" ? "" : "none";
            if (replyPanel.style.display !== "none" && replyTextarea && !replyTextarea.value) {
                replyTextarea.value = item.replyText || "";
                replyTextarea.focus();
            }
        });

        replyTemplateSelect?.addEventListener("change", () => {
            const idx = Number(replyTemplateSelect.value);
            if (!Number.isNaN(idx) && REPLY_TEMPLATES[idx] && replyTextarea) {
                const sep = replyTextarea.value && !replyTextarea.value.endsWith(" ") && !replyTextarea.value.endsWith("\n") ? " " : "";
                replyTextarea.value = `${replyTextarea.value}${sep}${REPLY_TEMPLATES[idx]}`;
                replyTextarea.focus();
            }
            replyTemplateSelect.value = "";
        });

        replyCancelBtn?.addEventListener("click", () => {
            if (replyPanel) replyPanel.style.display = "none";
        });

        replySendBtn?.addEventListener("click", async () => {
            try {
                const trimmed = (replyTextarea?.value || "").trim();
                if (!trimmed) {
                    throw new Error("Reply cannot be empty.");
                }
                const payload = await api("/api/author/reviews/reply", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({ reviewId: item.reviewId, replyText: trimmed })
                });
                showToast("Reply sent and notification delivered to the reviewer.", false);
                item.replyText = payload.replyText || trimmed;
                item.repliedAt = payload.repliedAt || item.repliedAt;
                renderSelectedBookReviews(items);
            } catch (error) {
                showToast(error.message, true);
            }
        });

        row.querySelector(".flag-btn")?.addEventListener("click", async () => {
            try {
                const reason = prompt("Flag reason (optional, e.g. abusive language):", item.flagReason || "Inappropriate content") || "Inappropriate content";
                const payload = await api("/api/author/reviews/flag", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({ reviewId: item.reviewId, reason })
                });

                showToast("Review flagged for moderation.", false);
                item.flagged = true;
                item.flagReason = payload.flagReason || reason;
                item.flaggedAt = payload.flaggedAt || item.flaggedAt;
                renderSelectedBookReviews(items);
            } catch (error) {
                showToast(error.message, true);
            }
        });

        body.appendChild(row);
    });

    renderFeedbackAnalytics(items);
}

async function loadPublishedBooks() {
    const status = document.getElementById("bookStatus");
    if (status) {
        status.textContent = "Loading your published books...";
    }

    const items = await api("/api/author/published-books");
    publishedBooks = Array.isArray(items) ? items : [];
    renderPublishedBooks(publishedBooks);

    if (publishedBooks.length > 0 && !selectedBook) {
        selectedBook = publishedBooks[0];
        const heading = document.getElementById("selectedBookHeading");
        if (heading) {
            heading.textContent = `Reviews for ${selectedBook.title || selectedBook.id}`;
        }
        await loadReviewsForSelectedBook(selectedBook.id);
    }
}

async function loadReviewsForSelectedBook(bookId) {
    const status = document.getElementById("reviewStatus");
    if (status) {
        status.textContent = "Loading reviews...";
    }

    const sort = document.getElementById("authorReviewSort")?.value || "recent";
    const reviews = await api(`/api/reviews?bookId=${encodeURIComponent(bookId)}&sortBy=${encodeURIComponent(sort)}`);
    selectedBookReviews = Array.isArray(reviews) ? reviews : [];
    renderSelectedBookReviews(selectedBookReviews);
}

document.getElementById("authorReviewSort")?.addEventListener("change", () => {
    if (selectedBook?.id) {
        loadReviewsForSelectedBook(selectedBook.id).catch((error) => showToast(error.message, true));
    }
});

document.getElementById("refreshBooksBtn")?.addEventListener("click", () => {
    loadPublishedBooks().catch((error) => showToast(error.message, true));
});

if (currentUser) {
    loadPublishedBooks().catch((error) => {
        const status = document.getElementById("bookStatus");
        if (status) {
            status.textContent = "Failed to load published books.";
        }
        showToast(error.message, true);
    });
}
