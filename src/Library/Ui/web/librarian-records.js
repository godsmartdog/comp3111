const currentUser = requireRole("LIBRARIAN");

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function renderBorrowedRecords(items) {
    const body = document.getElementById("borrowedRecordsBody");
    const status = document.getElementById("borrowedRecordsStatus");
    if (!body || !status) {
        return;
    }

    body.innerHTML = "";
    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No borrowed books records found.";
        return;
    }

    status.textContent = `Found ${items.length} borrowed record(s).`;
    items.forEach((item) => {
        const row = document.createElement("tr");
        const returnDate = item.returnDate || "-";
        const bookLabel = `${item.bookTitle || item.bookId} (${item.bookId})`;
        const isReturned = item.returned === true || String(item.status || "").toLowerCase() === "returned";
        const isOverdue = item.overdue === true || (!isReturned && !!item.dueDate && new Date(item.dueDate) < new Date(new Date().toDateString()));
        const statusText = item.status || "";
        const statusCell = isOverdue
            ? `${statusText}<span class="overdue-badge">OVERDUE</span>`
            : statusText;

        if (isOverdue) {
            row.classList.add("borrowed-record-overdue");
        }

        row.innerHTML = `
            <td>${item.borrowId}</td>
            <td>${bookLabel}</td>
            <td>${item.borrowerUsername}</td>
            <td>${item.borrowDate || ""}</td>
            <td>${item.dueDate || ""}</td>
            <td>${returnDate}</td>
            <td>${statusCell}</td>
        `;
        body.appendChild(row);
    });
}

async function refreshBorrowedRecords() {
    const status = document.getElementById("borrowedRecordsStatus");
    if (status) {
        status.textContent = "Loading borrowed books records...";
    }

    const items = await api("/api/librarian/borrowed-records");
    renderBorrowedRecords(items);
}

document.getElementById("refreshBorrowedRecordsBtn")?.addEventListener("click", () => {
    refreshBorrowedRecords().catch((e) => {
        const status = document.getElementById("borrowedRecordsStatus");
        if (status) {
            status.textContent = "Failed to load borrowed books records.";
        }
        showToast(e.message, true);
    });
});

if (currentUser) {
    refreshBorrowedRecords().catch((e) => {
        const status = document.getElementById("borrowedRecordsStatus");
        if (status) {
            status.textContent = "Failed to load borrowed books records.";
        }
        showToast(e.message, true);
    });
}
