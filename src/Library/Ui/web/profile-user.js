const currentUser = getCurrentUser();
if (!currentUser || !currentUser.sessionId) {
    window.location.href = "login.html";
}
if (currentUser && !["STUDENT", "STAFF"].includes(currentUser.role)) {
    window.location.href = rolePage(currentUser.role);
}

if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    document.getElementById("profilePageTitle").textContent = `${currentUser.role === "STAFF" ? "Staff" : "Student"} Profile`;
    document.getElementById("backPortalLink").href = currentUser.role === "STAFF" ? "mainStaff.html" : "mainStudent.html";
    attachLogout("logoutBtn");
}

let historyItems = [];
let historyPage = 1;
const historyPageSize = 10;

function totalHistoryPages() {
    return Math.max(1, Math.ceil(historyItems.length / historyPageSize));
}

function updateHistoryPager() {
    const total = totalHistoryPages();
    document.getElementById("historyPageInfo").textContent = `${historyPage}/${total}`;
    document.getElementById("historyPrevBtn").disabled = historyPage <= 1;
    document.getElementById("historyNextBtn").disabled = historyPage >= total;
}

function renderHistoryPage() {
    const body = document.getElementById("borrowHistoryBody");
    body.innerHTML = "";

    if (historyItems.length === 0) {
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="5" class="muted">No borrow history found.</td>';
        body.appendChild(row);
        updateHistoryPager();
        return;
    }

    const start = (historyPage - 1) * historyPageSize;
    const end = start + historyPageSize;
    historyItems.slice(start, end).forEach((item) => {
        const row = document.createElement("tr");
        const returned = item.returned === true || String(item.status || "").toLowerCase() === "returned";
        row.innerHTML = `
            <td>${item.bookTitle || ""}</td>
            <td>${item.borrowDate || ""}</td>
            <td>${item.dueDate || ""}</td>
            <td>${item.returnedDate || "-"}</td>
            <td>${returned ? "Returned" : (item.overdue ? "Overdue" : "Active")}</td>
        `;
        body.appendChild(row);
    });

    updateHistoryPager();
}

async function loadProfile() {
    const profile = await api("/api/profile");
    document.getElementById("profileFullName").value = profile.fullName || "";
    document.getElementById("profileCurrentPassword").value = "";
    document.getElementById("profilePassword").value = "";
    document.getElementById("profileConfirmPassword").value = "";
    document.getElementById("profileFeedback").textContent = "";
}

async function saveProfile() {
    const fullName = document.getElementById("profileFullName").value.trim();
    const currentPassword = document.getElementById("profileCurrentPassword").value.trim();
    const newPassword = document.getElementById("profilePassword").value.trim();
    const confirmPassword = document.getElementById("profileConfirmPassword").value.trim();
    const passwordChanged = Boolean(newPassword);

    if (!fullName) {
        throw new Error("Full Name cannot be empty.");
    }

    if (newPassword) {
        if (!currentPassword) {
            throw new Error("Current password is required to change password.");
        }
        if (!confirmPassword) {
            throw new Error("Please confirm the new password.");
        }
        if (newPassword !== confirmPassword) {
            throw new Error("New password and confirmation do not match.");
        }
        const issues = getPasswordPolicyViolations(newPassword);
        if (issues.length > 0) {
            throw new Error(issues[0]);
        }
    }

    await api("/api/profile", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ fullName, password: newPassword, currentPassword })
    }, false);

    if (passwordChanged) {
        localStorage.removeItem("currentUser");
        window.location.href = `login.html?role=${encodeURIComponent(currentUser?.role || expectedRole)}`;
        return;
    }

    if (currentUser) {
        currentUser.fullName = fullName;
        saveCurrentUser(currentUser);
        document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    document.getElementById("profileCurrentPassword").value = "";
    document.getElementById("profilePassword").value = "";
    document.getElementById("profileConfirmPassword").value = "";
    document.getElementById("profileFeedback").textContent = "Profile updated successfully.";
    showToast("Profile updated successfully.", false);
}

async function loadBorrowHistory() {
    const items = await api("/api/borrows?status=all&sortBy=borrowDate&sortDir=desc");
    historyItems = Array.isArray(items) ? items : [];
    historyPage = 1;
    renderHistoryPage();
}

document.getElementById("saveProfileBtn").addEventListener("click", () => {
    saveProfile().catch((e) => {
        document.getElementById("profileFeedback").textContent = e.message;
        showToast(e.message, true);
    });
});

document.getElementById("historyPrevBtn").addEventListener("click", () => {
    if (historyPage > 1) {
        historyPage -= 1;
        renderHistoryPage();
    }
});

document.getElementById("historyNextBtn").addEventListener("click", () => {
    if (historyPage < totalHistoryPages()) {
        historyPage += 1;
        renderHistoryPage();
    }
});

if (currentUser) {
    loadProfile().catch((e) => showToast(e.message, true));
    loadBorrowHistory().catch((e) => showToast(e.message, true));
}
