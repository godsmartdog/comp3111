const currentUser = requireRole("LIBRARIAN");

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

function renderApprovedBooks(items) {
    const tbody = document.getElementById("approvedBooksBody");
    const status = document.getElementById("approvedBooksStatus");
    if (!tbody || !status) {
        return;
    }

    tbody.innerHTML = "";
    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No approved books yet.";
        return;
    }

    status.textContent = `Found ${items.length} approved book(s).`;
    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${item.id}</td>
            <td>${item.title}</td>
            <td>${item.author}</td>
            <td>${item.publishDate || ""}</td>
            <td>${formatAverageRating(item)}</td>
            <td>${item.status}</td>
            <td>${item.availableCopies ?? 0}/${item.totalCopies ?? 0}</td>
            <td>
                <input class="copies-input" type="number" min="1" max="1000" value="${item.totalCopies ?? 1}" style="width:80px;">
                <button class="secondary update-copies-btn" type="button">Update</button>
            </td>
        `;

        const updateBtn = row.querySelector(".update-copies-btn");
        const copiesInput = row.querySelector(".copies-input");
        updateBtn?.addEventListener("click", async () => {
            try {
                const totalCopies = Number(copiesInput?.value || "1");
                const text = await api("/api/librarian/approved-book/copies", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({ bookId: item.id, totalCopies })
                }, false);
                showToast(text, false);
                await refreshApprovedBooks();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        tbody.appendChild(row);
    });
}

async function refreshApprovedBooks() {
    const status = document.getElementById("approvedBooksStatus");
    if (status) {
        status.textContent = "Loading approved books...";
    }

    const items = await api("/api/librarian/approved-books");
    renderApprovedBooks(items);
}

document.getElementById("refreshApprovedBooksBtn")?.addEventListener("click", () => {
    refreshApprovedBooks().catch((e) => {
        const status = document.getElementById("approvedBooksStatus");
        if (status) {
            status.textContent = "Failed to load approved books.";
        }
        showToast(e.message, true);
    });
});

if (currentUser) {
    refreshApprovedBooks().catch((e) => {
        const status = document.getElementById("approvedBooksStatus");
        if (status) {
            status.textContent = "Failed to load approved books.";
        }
        showToast(e.message, true);
    });
}
