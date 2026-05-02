const currentUser = requireRole("LIBRARIAN");

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

let borrowedRecords = [];
let activeTab = "all";

function todayIso() {
    return new Date().toISOString().slice(0, 10);
}

function isRecordOverdue(item) {
    if (item.overdue === true) return true;
    const isReturned = item.returned === true || String(item.status || "").toLowerCase() === "returned";
    if (isReturned) return false;
    const due = item.dueDate || "";
    return Boolean(due) && due < todayIso();
}

function isRecordReturned(item) {
    return item.returned === true || String(item.status || "").toLowerCase() === "returned";
}

function applyRecordFilters(items) {
    const keyword = (document.getElementById("recordsKeyword")?.value || "").trim().toLowerCase();
    return items.filter((item) => {
        if (keyword) {
            const haystack = [
                item.bookTitle || "",
                item.borrowerUsername || "",
                item.borrowId || ""
            ].join(" ").toLowerCase();
            if (!haystack.includes(keyword)) return false;
        }
        if (activeTab === "active") {
            return !isRecordReturned(item) && !isRecordOverdue(item);
        }
        if (activeTab === "overdue") {
            return !isRecordReturned(item) && isRecordOverdue(item);
        }
        if (activeTab === "returned") {
            return isRecordReturned(item);
        }
        return true;
    });
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

    status.textContent = `Showing ${items.length} borrowed record(s).`;
    items.forEach((item) => {
        const row = document.createElement("tr");
        const returnDate = formatDateOnly(item.returnDate || "") || "-";
        const bookLabel = `${item.bookTitle || item.bookId} (${item.bookId})`;
        const isReturned = isRecordReturned(item);
        const isOverdue = !isReturned && isRecordOverdue(item);
        const statusText = item.status || "";
        const statusCell = isOverdue
            ? `${statusText}<span class="overdue-badge">OVERDUE</span>`
            : statusText;
        const dueCell = isOverdue
            ? `${formatDateOnly(item.dueDate || "")}<span class="overdue-badge">OVERDUE</span>`
            : formatDateOnly(item.dueDate || "");

        if (isOverdue) {
            row.classList.add("borrowed-record-overdue");
            row.classList.add("record-overdue");
        }

        row.innerHTML = `
            <td>${item.borrowId}</td>
            <td>${bookLabel}</td>
            <td>${item.borrowerUsername}</td>
            <td>${formatDateOnly(item.borrowDate || "")}</td>
            <td>${dueCell}</td>
            <td>${returnDate}</td>
            <td>${statusCell}</td>
        `;
        body.appendChild(row);
    });
}

function rerender() {
    renderBorrowedRecords(applyRecordFilters(borrowedRecords));
}

async function refreshBorrowedRecords() {
    const status = document.getElementById("borrowedRecordsStatus");
    if (status) {
        status.textContent = "Loading borrowed books records...";
    }

    const items = await api("/api/librarian/borrowed-records");
    borrowedRecords = Array.isArray(items) ? items : [];
    rerender();
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

document.getElementById("applyRecordsFiltersBtn")?.addEventListener("click", rerender);

document.getElementById("resetRecordsFiltersBtn")?.addEventListener("click", () => {
    const k = document.getElementById("recordsKeyword");
    if (k) k.value = "";
    activeTab = "all";
    document.querySelectorAll(".tab-btn").forEach((btn) => {
        btn.classList.toggle("active", btn.dataset.filter === "all");
    });
    rerender();
});

document.getElementById("recordsKeyword")?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
        rerender();
    }
});

document.querySelectorAll(".tab-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
        activeTab = btn.dataset.filter || "all";
        document.querySelectorAll(".tab-btn").forEach((b) => {
            b.classList.toggle("active", b === btn);
        });
        rerender();
    });
});

document.getElementById("exportRecordsCsvBtn")?.addEventListener("click", () => {
    const params = new URLSearchParams();
    const keyword = (document.getElementById("recordsKeyword")?.value || "").trim();
    if (keyword) params.set("keyword", keyword);
    if (activeTab && activeTab !== "all") params.set("tab", activeTab);
    const qs = params.toString();
    window.location.href = `/api/librarian/borrowed-records-export${qs ? `?${qs}` : ""}`;
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
