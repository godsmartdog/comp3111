const currentUser = requireRole("LIBRARIAN");
if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let submissionSortEnabled = false;

async function loadLibrarianProfile() {
    const payload = await api("/api/librarian/profile");
    document.getElementById("librarianProfileFullName").value = payload.fullName || "";
    document.getElementById("librarianProfileEmployeeId").value = payload.employeeId || "";
    const currentPasswordInput = document.getElementById("librarianProfileCurrentPassword");
    if (currentPasswordInput) {
        currentPasswordInput.value = "";
    }
}

async function saveLibrarianProfile() {
    const feedback = document.getElementById("librarianProfileFeedback");
    const fullName = document.getElementById("librarianProfileFullName").value.trim();
    const employeeId = document.getElementById("librarianProfileEmployeeId").value.trim();
    const currentPassword = document.getElementById("librarianProfileCurrentPassword")?.value || "";
    const password = document.getElementById("librarianProfilePassword").value;

    if (!fullName) {
        feedback.textContent = "Full Name cannot be empty.";
        showToast(feedback.textContent, true);
        return;
    }
    if (!employeeId) {
        feedback.textContent = "Employee ID cannot be empty.";
        showToast(feedback.textContent, true);
        return;
    }

    if (password.trim()) {
        if (!currentPassword.trim()) {
            feedback.textContent = "Current password is required to change password.";
            showToast(feedback.textContent, true);
            return;
        }

        const issues = getPasswordPolicyViolations(password);
        if (issues.length > 0) {
            feedback.textContent = issues[0];
            showToast(feedback.textContent, true);
            return;
        }
    }

    const text = await api("/api/librarian/profile", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ fullName, employeeId, password, currentPassword })
    }, false);

    feedback.textContent = text;
    const currentPasswordInput = document.getElementById("librarianProfileCurrentPassword");
    if (currentPasswordInput) {
        currentPasswordInput.value = "";
    }
    document.getElementById("librarianProfilePassword").value = "";
    if (currentUser) {
        currentUser.fullName = fullName;
        saveCurrentUser(currentUser);
        document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    showToast(text, false);
}

function renderPending(items) {
    const tbody = document.getElementById("pendingBody");
    tbody.innerHTML = "";

    items.forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${item.title}</td>
            <td>${item.authorFullName}</td>
            <td>${item.status || ""}</td>
            <td>${item.submittedDate}</td>
            <td>${item.fileName}</td>
            <td></td>
        `;

        const actionCell = row.lastElementChild;

        const approveBtn = document.createElement("button");
        approveBtn.className = "secondary";
        approveBtn.textContent = "Approve";
        approveBtn.addEventListener("click", () => review(item.id, "approve"));

        const rejectBtn = document.createElement("button");
        rejectBtn.className = "danger";
        rejectBtn.textContent = "Reject";
        rejectBtn.addEventListener("click", () => review(item.id, "reject"));

        actionCell.appendChild(approveBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(rejectBtn);

        tbody.appendChild(row);
    });
}

async function refreshPending() {
    const searchInput = document.getElementById("submissionSearchInput");
    const statusFilter = document.getElementById("submissionStatusFilter");
    const sortDirFilter = document.getElementById("submissionSortDirFilter");

    const query = new URLSearchParams();
    const keyword = searchInput ? searchInput.value.trim() : "";
    const status = statusFilter ? statusFilter.value : "pending";
    const sortDir = sortDirFilter ? sortDirFilter.value : "asc";

    if (keyword) {
        query.set("q", keyword);
    }
    if (status) {
        query.set("status", status);
    }
    if (submissionSortEnabled) {
        query.set("sortBy", "submittedDate");
        query.set("sortDir", sortDir || "asc");
    }

    const path = `/api/librarian/pending?${query.toString()}`;
    const items = await api(path);
    renderPending(items);
}

function resetSubmissionFilters() {
    const searchInput = document.getElementById("submissionSearchInput");
    const statusFilter = document.getElementById("submissionStatusFilter");
    const sortDirFilter = document.getElementById("submissionSortDirFilter");

    if (searchInput) {
        searchInput.value = "";
    }
    if (statusFilter) {
        statusFilter.value = "pending";
    }
    if (sortDirFilter) {
        sortDirFilter.value = "asc";
    }
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

    try {
        const items = await api("/api/librarian/approved-books");
        renderApprovedBooks(items);
    } catch (error) {
        if (status) {
            status.textContent = "Failed to load approved books.";
        }
        throw error;
    }
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

    try {
        const items = await api("/api/librarian/borrowed-records");
        renderBorrowedRecords(items);
    } catch (error) {
        if (status) {
            status.textContent = "Failed to load borrowed books records.";
        }
        throw error;
    }
}

async function refreshLibrarianNotifications() {
    const status = document.getElementById("librarianNotificationStatus");
    const list = document.getElementById("librarianNotificationsList");
    if (!status || !list) {
        return;
    }

    status.textContent = "Loading notifications...";

    try {
        const items = await api("/api/librarian/notifications");
        list.innerHTML = "";

        if (!Array.isArray(items) || items.length === 0) {
            status.textContent = "No notifications.";
            return;
        }

        const unreadCount = items.filter((item) => !item.read).length;
        status.textContent = `Total: ${items.length}, Unread: ${unreadCount}`;

        items.forEach((item) => {
            const li = document.createElement("li");
            const readLabel = item.read ? "Read" : "Unread";
            li.innerHTML = `
                <div>
                    <strong>[${readLabel}] ${item.title}</strong>
                    <div>${item.message || ""}</div>
                    <small>${item.createdAt || ""}</small>
                </div>
                <button class="secondary" type="button" ${item.read ? "disabled" : ""}>Mark As Read</button>
            `;

            const readBtn = li.querySelector("button");
            readBtn.addEventListener("click", async () => {
                try {
                    const payload = await api("/api/librarian/notifications/read", {
                        method: "POST",
                        headers: { "Content-Type": "application/x-www-form-urlencoded" },
                        body: formBody({ notificationId: item.id })
                    });
                    showToast(payload.message || "Notification marked as read.", false);
                    await refreshLibrarianNotifications();
                } catch (error) {
                    showToast(error.message, true);
                }
            });

            list.appendChild(li);
        });
    } catch (error) {
        status.textContent = "Failed to load notifications.";
        throw error;
    }
}

async function review(submissionId, action) {
    try {
        let comment = "";
        let reason = "";

        if (action === "reject") {
            reason = (prompt("Rejection reason (optional, max 500 characters)") || "").trim();
            if (reason.length > 500) {
                showToast("Rejection reason must be at most 500 characters.", true);
                return;
            }
            comment = reason ? "Rejected" : "Rejected";
        } else {
            comment = prompt(`Comment for ${action}`) || "";
        }

        const text = await api("/api/librarian/review", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ submissionId, action, comment, reason })
        }, false);

        showToast(text, false);
        await refreshPending();
    } catch (error) {
        showToast(error.message, true);
    }
}

document.getElementById("refreshPendingBtn").addEventListener("click", () => {
    refreshPending().catch((e) => showToast(e.message, true));
});

document.getElementById("applySubmissionFiltersBtn")?.addEventListener("click", () => {
    submissionSortEnabled = true;
    refreshPending().catch((e) => showToast(e.message, true));
});

document.getElementById("resetSubmissionFiltersBtn")?.addEventListener("click", () => {
    submissionSortEnabled = false;
    resetSubmissionFilters();
    refreshPending().catch((e) => showToast(e.message, true));
});

document.getElementById("saveLibrarianProfileBtn")?.addEventListener("click", () => {
    saveLibrarianProfile().catch((e) => {
        const feedback = document.getElementById("librarianProfileFeedback");
        if (feedback) {
            feedback.textContent = e.message;
        }
        showToast(e.message, true);
    });
});

document.getElementById("refreshApprovedBooksBtn")?.addEventListener("click", () => {
    refreshApprovedBooks().catch((e) => showToast(e.message, true));
});

document.getElementById("refreshBorrowedRecordsBtn")?.addEventListener("click", () => {
    refreshBorrowedRecords().catch((e) => showToast(e.message, true));
});

document.getElementById("refreshLibrarianNotificationsBtn")?.addEventListener("click", () => {
    refreshLibrarianNotifications().catch((e) => showToast(e.message, true));
});

if (currentUser) {
    loadLibrarianProfile().catch((e) => {
        const feedback = document.getElementById("librarianProfileFeedback");
        if (feedback) {
            feedback.textContent = "Failed to load librarian profile.";
        }
        showToast(e.message, true);
    });
    refreshPending().catch((e) => showToast(e.message, true));
    refreshApprovedBooks().catch((e) => showToast(e.message, true));
    refreshBorrowedRecords().catch((e) => showToast(e.message, true));
    refreshLibrarianNotifications().catch((e) => showToast(e.message, true));
}
