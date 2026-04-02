const currentUser = requireRole("AUTHOR");
if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let publishedItems = [];
let publishedPage = 1;
const publishedPageSize = 10;

function totalPublishedPages() {
    return Math.max(1, Math.ceil(publishedItems.length / publishedPageSize));
}

function updatePublishedPager() {
    const total = totalPublishedPages();
    document.getElementById("authorHistoryPageInfo").textContent = `${publishedPage}/${total}`;
    document.getElementById("authorHistoryPrevBtn").disabled = publishedPage <= 1;
    document.getElementById("authorHistoryNextBtn").disabled = publishedPage >= total;
}

function renderPublishedPage() {
    const body = document.getElementById("authorPublishedHistoryBody");
    body.innerHTML = "";

    if (publishedItems.length === 0) {
        const row = document.createElement("tr");
        row.innerHTML = '<td colspan="2" class="muted">No published books yet.</td>';
        body.appendChild(row);
        updatePublishedPager();
        return;
    }

    const start = (publishedPage - 1) * publishedPageSize;
    const end = start + publishedPageSize;
    publishedItems.slice(start, end).forEach((item) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${item.title || ""}</td>
            <td>${item.publishDate || ""}</td>
        `;
        body.appendChild(row);
    });

    updatePublishedPager();
}

async function loadAuthorProfile() {
    const payload = await api("/api/author/profile");
    document.getElementById("authorProfileFullName").value = payload.fullName || "";
    document.getElementById("authorProfileBio").value = payload.bio || "";
    document.getElementById("authorProfileCurrentPassword").value = "";
    document.getElementById("authorProfilePassword").value = "";
    document.getElementById("authorProfileConfirmPassword").value = "";
}

async function saveAuthorProfile() {
    const fullName = document.getElementById("authorProfileFullName").value.trim();
    const bio = document.getElementById("authorProfileBio").value.trim();
    const currentPassword = document.getElementById("authorProfileCurrentPassword").value.trim();
    const password = document.getElementById("authorProfilePassword").value;
    const confirmPassword = document.getElementById("authorProfileConfirmPassword").value;
    const passwordChanged = Boolean(password.trim());

    if (!fullName) {
        throw new Error("Full Name cannot be empty.");
    }
    if (!bio) {
        throw new Error("Bio cannot be empty.");
    }
    if (password.trim()) {
        if (!currentPassword) {
            throw new Error("Current password is required to change password.");
        }
        if (!confirmPassword.trim()) {
            throw new Error("Please confirm the new password.");
        }
        if (password !== confirmPassword) {
            throw new Error("New password and confirmation do not match.");
        }
        const issues = getPasswordPolicyViolations(password);
        if (issues.length > 0) {
            throw new Error(issues[0]);
        }
    }

    const text = await api("/api/author/profile", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ fullName, bio, password, currentPassword })
    }, false);

    if (passwordChanged) {
        const successMessage = text || "Password updated successfully. Please log in again.";
        document.getElementById("authorProfileFeedback").textContent = successMessage;
        showToast(successMessage, false);
        setTimeout(() => {
            localStorage.removeItem("currentUser");
            window.location.href = `login.html?role=${encodeURIComponent(currentUser?.role || "AUTHOR")}`;
        }, 1500);
        return;
    }

    if (currentUser) {
        currentUser.fullName = fullName;
        saveCurrentUser(currentUser);
        document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    document.getElementById("authorProfileCurrentPassword").value = "";
    document.getElementById("authorProfilePassword").value = "";
    document.getElementById("authorProfileConfirmPassword").value = "";
    document.getElementById("authorProfileFeedback").textContent = text;
    showToast(text, false);
}

async function loadPublishedHistory() {
    const items = await api("/api/author/published-books");
    publishedItems = Array.isArray(items) ? items : [];
    publishedPage = 1;
    renderPublishedPage();
}

document.getElementById("saveAuthorProfileBtn").addEventListener("click", () => {
    saveAuthorProfile().catch((e) => {
        document.getElementById("authorProfileFeedback").textContent = e.message;
        showToast(e.message, true);
    });
});

document.getElementById("authorHistoryPrevBtn").addEventListener("click", () => {
    if (publishedPage > 1) {
        publishedPage -= 1;
        renderPublishedPage();
    }
});

document.getElementById("authorHistoryNextBtn").addEventListener("click", () => {
    if (publishedPage < totalPublishedPages()) {
        publishedPage += 1;
        renderPublishedPage();
    }
});

if (currentUser) {
    loadAuthorProfile().catch((e) => showToast(e.message, true));
    loadPublishedHistory().catch((e) => showToast(e.message, true));
}
