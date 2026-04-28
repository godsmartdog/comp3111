const currentUser = requireRole("AUTHOR");
let publishedBooks = [];
let selectedBook = null;
let selectedBookReviews = [];

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
        row.innerHTML = '<td colspan="4" class="muted">No book selected.</td>';
        body.appendChild(row);
        return;
    }

    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = `No reviews found for ${selectedBook.title || selectedBook.id}.`;
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="4" class="muted">No reviews available yet.</td>';
        body.appendChild(row);
        return;
    }

    status.textContent = `Found ${items.length} review(s) for ${selectedBook.title || selectedBook.id}.`;

    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${escapeHtml(item.reviewerFullName || item.username || "")}</td>
            <td>${Number(item.rating || 0)}/5</td>
            <td>${formatReviewText(item)}</td>
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

    const reviews = await api(`/api/reviews?bookId=${encodeURIComponent(bookId)}`);
    selectedBookReviews = Array.isArray(reviews) ? reviews : [];
    renderSelectedBookReviews(selectedBookReviews);
}

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
