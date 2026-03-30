const currentUser = requireRole("LIBRARIAN");
if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let approvedItems = [];
let approvedPage = 1;
const approvedPageSize = 10;

function totalApprovedPages() {
    return Math.max(1, Math.ceil(approvedItems.length / approvedPageSize));
}

function updateApprovedPager() {
    const total = totalApprovedPages();
    document.getElementById("librarianHistoryPageInfo").textContent = `${approvedPage}/${total}`;
    document.getElementById("librarianHistoryPrevBtn").disabled = approvedPage <= 1;
    document.getElementById("librarianHistoryNextBtn").disabled = approvedPage >= total;
}

function renderApprovedPage() {
    const body = document.getElementById("librarianApprovedHistoryBody");
    body.innerHTML = "";

    if (approvedItems.length === 0) {
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="2" class="muted">No approved books yet.</td>';
        body.appendChild(row);
        updateApprovedPager();
        return;
    }

    const start = (approvedPage - 1) * approvedPageSize;
    const end = start + approvedPageSize;
    approvedItems.slice(start, end).forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${item.title || ""}</td>
            <td>${item.publishDate || ""}</td>
        `;
        body.appendChild(row);
    });

    updateApprovedPager();
}

async function loadLibrarianProfile() {
    const payload = await api("/api/librarian/profile");
    document.getElementById("librarianProfileFullName").value = payload.fullName || "";
    document.getElementById("librarianProfileEmployeeId").value = payload.employeeId || "";
    document.getElementById("librarianProfileCurrentPassword").value = "";
    document.getElementById("librarianProfilePassword").value = "";
}

async function saveLibrarianProfile() {
    const fullName = document.getElementById("librarianProfileFullName").value.trim();
    const employeeId = document.getElementById("librarianProfileEmployeeId").value.trim();
    const currentPassword = document.getElementById("librarianProfileCurrentPassword").value.trim();
    const password = document.getElementById("librarianProfilePassword").value;

    if (!fullName) {
        throw new Error("Full Name cannot be empty.");
    }
    if (!employeeId) {
        throw new Error("Employee ID cannot be empty.");
    }

    if (password.trim()) {
        if (!currentPassword) {
            throw new Error("Current password is required to change password.");
        }
        const issues = getPasswordPolicyViolations(password);
        if (issues.length > 0) {
            throw new Error(issues[0]);
        }
    }

    const text = await api("/api/librarian/profile", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ fullName, employeeId, password, currentPassword })
    }, false);

    if (currentUser) {
        currentUser.fullName = fullName;
        saveCurrentUser(currentUser);
        document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    document.getElementById("librarianProfileCurrentPassword").value = "";
    document.getElementById("librarianProfilePassword").value = "";
    document.getElementById("librarianProfileFeedback").textContent = text;
    showToast(text, false);
}

async function loadApprovedHistory() {
    const items = await api("/api/librarian/approved-books");
    approvedItems = Array.isArray(items) ? items : [];
    approvedPage = 1;
    renderApprovedPage();
}

document.getElementById("saveLibrarianProfileBtn").addEventListener("click", () => {
    saveLibrarianProfile().catch((e) => {
        document.getElementById("librarianProfileFeedback").textContent = e.message;
        showToast(e.message, true);
    });
});

document.getElementById("librarianHistoryPrevBtn").addEventListener("click", () => {
    if (approvedPage > 1) {
        approvedPage -= 1;
        renderApprovedPage();
    }
});

document.getElementById("librarianHistoryNextBtn").addEventListener("click", () => {
    if (approvedPage < totalApprovedPages()) {
        approvedPage += 1;
        renderApprovedPage();
    }
});

if (currentUser) {
    loadLibrarianProfile().catch((e) => showToast(e.message, true));
    loadApprovedHistory().catch((e) => showToast(e.message, true));
}
