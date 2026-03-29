const currentUser = requireRole("LIBRARIAN");
if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

async function loadLibrarianProfile() {
    const payload = await api("/api/librarian/profile");
    document.getElementById("librarianProfileFullName").value = payload.fullName || "";
    document.getElementById("librarianProfileEmployeeId").value = payload.employeeId || "";
}

async function saveLibrarianProfile() {
    const feedback = document.getElementById("librarianProfileFeedback");
    const fullName = document.getElementById("librarianProfileFullName").value.trim();
    const employeeId = document.getElementById("librarianProfileEmployeeId").value.trim();
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
        body: formBody({ fullName, employeeId, password })
    }, false);

    feedback.textContent = text;
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
    const items = await api("/api/librarian/pending");
    renderPending(items);
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
        const comment = prompt(`Comment for ${action}`) || "";
        const text = await api("/api/librarian/review", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ submissionId, action, comment })
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
    refreshLibrarianNotifications().catch((e) => showToast(e.message, true));
}
