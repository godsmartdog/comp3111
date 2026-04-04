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
    const body = document.getElementById("myReviewsBody");
    const status = document.getElementById("reviewsStatus");
    if (!body || !status) {
        return;
    }

    body.innerHTML = "";
    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No review records yet.";
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="4" class="muted">Submit reviews from Reader pages after borrowing books.</td>';
        body.appendChild(row);
        return;
    }

    status.textContent = `Found ${items.length} submitted review(s).`;
    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${item.bookTitle || item.bookId}</td>
            <td>${Number(item.rating || 0)}/5</td>
            <td>${item.reviewText || "-"}</td>
            <td>${item.updatedAt || ""}</td>
        `;
        body.appendChild(row);
    });
}

async function refreshMyReviews() {
    const items = await api("/api/reviews/me");
    renderMyReviews(items);
}

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