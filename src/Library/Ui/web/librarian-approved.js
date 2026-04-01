const currentUser = requireRole("LIBRARIAN");

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
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
            <td>${item.status}</td>
        `;
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
