const currentUser = requireRole("AUTHOR");

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function renderAuthorReviews(items) {
    const body = document.getElementById("authorReviewsBody");
    const status = document.getElementById("reviewStatus");
    if (!body || !status) {
        return;
    }

    body.innerHTML = "";
    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No reviews found for your published books.";
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="5" class="muted">No reviews available yet.</td>';
        body.appendChild(row);
        return;
    }

    status.textContent = `Found ${items.length} review(s).`;

    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${escapeHtml(item.bookTitle || item.bookId || "")}</td>
            <td>${escapeHtml(item.reviewerFullName || item.username || "")}</td>
            <td>${Number(item.rating || 0)}/5</td>
            <td>${escapeHtml(item.reviewText || "")}</td>
            <td>
                <div>${item.replyText ? `<strong>Reply:</strong> ${escapeHtml(item.replyText)}` : ""}</div>
                <div>${item.flagged ? `<strong>Reported</strong>${item.flagReason ? `: ${escapeHtml(item.flagReason)}` : ""}` : ""}</div>
                <div class="toolbar" style="margin-top:8px;">
                    <button class="secondary reply-btn" type="button">Reply</button>
                    <button class="secondary flag-btn" type="button">Flag</button>
                </div>
            </td>
        `;

        row.querySelector(".reply-btn")?.addEventListener("click", async () => {
            try {
                const replyText = prompt(`Reply to ${item.reviewerFullName || item.username}:`, item.replyText || "");
                if (replyText === null) {
                    return;
                }
                const trimmed = replyText.trim();
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
                renderAuthorReviews(items);
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
                renderAuthorReviews(items);
            } catch (error) {
                showToast(error.message, true);
            }
        });

        body.appendChild(row);
    });
}

async function refreshReviews() {
    const items = await api("/api/author/reviews");
    renderAuthorReviews(items);
}

document.getElementById("refreshReviewsBtn")?.addEventListener("click", () => {
    refreshReviews().catch((error) => showToast(error.message, true));
});

if (currentUser) {
    refreshReviews().catch((error) => {
        const status = document.getElementById("reviewStatus");
        if (status) {
            status.textContent = "Failed to load reviews.";
        }
        showToast(error.message, true);
    });
}
