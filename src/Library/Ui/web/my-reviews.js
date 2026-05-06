const currentUser = getCurrentUser();
if (!currentUser || (currentUser.role !== "STUDENT" && currentUser.role !== "STAFF")) {
    window.location.href = "index.html";
}

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    const mainPortalLink = document.getElementById("mainPortalLink");
    const booksPortalLink = document.getElementById("booksPortalLink");
    if (currentUser.role === "STAFF") {
        if (mainPortalLink) {
            mainPortalLink.href = "mainStaff.html";
        }
        if (booksPortalLink) {
            booksPortalLink.href = "staff-books.html";
        }
    }
    attachLogout("logoutBtn");
}

function renderMyReviews(items) {
    const container = document.getElementById("reviewsList");
    const status = document.getElementById("reviewsStatus");
    if (!container || !status) {
        return;
    }

    container.innerHTML = "";
    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No review records yet.";
        const emptyMsg = document.createElement("p");
        emptyMsg.className = "muted";
        emptyMsg.textContent = "Submit reviews from Reader pages after borrowing books.";
        container.appendChild(emptyMsg);
        return;
    }

    status.textContent = `Found ${items.length} submitted review(s).`;
    items.forEach((item) => {
        const card = document.createElement("div");
        card.style.cssText = "padding:15px;border:1px solid #ddd;border-radius:8px;background:#f9f9f9;";
        
        const bookTitle = escapeHtml(item.bookTitle || item.bookId);
        const rating = Number(item.rating || 0);
        const reviewText = escapeHtml(item.reviewText || "");
        const createdAt = escapeHtml(item.createdAt || "");
        const helpfulCount = Number(item.helpfulCount || 0);
        const replyText = escapeHtml(item.replyText || "");
        const repliedAt = escapeHtml(item.repliedAt || "");
        const flagged = Boolean(item.flagged);
        const flagReason = escapeHtml(item.flagReason || "");
        const flaggedAt = escapeHtml(item.flaggedAt || "");
        
        let html = `
            <div style="margin-bottom:10px;">
                <strong style="font-size:1.1em;">${bookTitle}</strong>
                <span class="muted" style="margin-left:10px;">Rating: ${rating}/5</span>
                <span class="muted" style="margin-left:10px;">Helpful: ${helpfulCount}</span>
                ${item.anonymous ? `<span class="muted" style="margin-left:10px;">(Anonymous)</span>` : ""}
            </div>
            <div style="margin-bottom:10px;white-space:pre-wrap;">
                <strong>Your Review:</strong> ${reviewText}
                ${createdAt ? `<div class="muted" style="font-size:0.85em;margin-top:4px;">Posted: ${createdAt}</div>` : ""}
            </div>
        `;
        
        if (replyText) {
            html += `
                <div style="margin-top:12px;padding:12px;background:#e8f4f8;border-left:3px solid #2196F3;border-radius:4px;">
                    <strong style="color:#1976D2;">Author's Reply:</strong>
                    <div style="margin-top:6px;white-space:pre-wrap;">${replyText}</div>
                    ${repliedAt ? `<div class="muted" style="font-size:0.85em;margin-top:6px;">Replied: ${repliedAt}</div>` : ""}
                </div>
            `;
        }
        
        if (flagged) {
            html += `
                <div style="margin-top:12px;padding:12px;background:#fff3e0;border-left:3px solid #ff9800;border-radius:4px;">
                    <strong style="color:#e65100;">Reported:</strong> ${flagReason || "Inappropriate content"}
                    ${flaggedAt ? `<div class="muted" style="font-size:0.85em;margin-top:4px;">Flagged: ${flaggedAt}</div>` : ""}
                </div>
            `;
        }
        
        card.innerHTML = html;
        container.appendChild(card);
    });
}

async function refreshMyReviews() {
    const sort = document.getElementById("myReviewSort")?.value || "recent";
    const items = await api(`/api/reviews/me?sortBy=${encodeURIComponent(sort)}`);
    renderMyReviews(items);
}

document.getElementById("myReviewSort")?.addEventListener("change", () => {
    refreshMyReviews().catch((error) => showToast(error.message, true));
});

document.getElementById("refreshReviewsBtn")?.addEventListener("click", () => {
    refreshMyReviews().catch((error) => showToast(error.message, true));
});

if (currentUser) {
    refreshMyReviews().catch((error) => {
        const status = document.getElementById("reviewsStatus");
        if (status) {
            status.textContent = "Failed to load review records.";
        }
        showToast(error.message, true);
    });
}