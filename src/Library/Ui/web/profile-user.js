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
let selectedProfilePhotoFile = null;
let profilePhotoObjectUrl = null;

function updatePasswordStrengthMeter() {
    const password = document.getElementById("profilePassword")?.value || "";
    const info = getPasswordStrengthInfo(password);
    const fill = document.getElementById("profilePasswordStrengthFill");
    const text = document.getElementById("profilePasswordStrengthText");

    if (fill) {
        fill.style.width = `${Math.max(10, (info.score / info.max) * 100)}%`;
        fill.style.background = info.color;
    }
    if (text) {
        text.textContent = info.label;
        text.style.color = info.color;
    }
}

function clearProfilePhotoPreviewUrl() {
    if (profilePhotoObjectUrl) {
        URL.revokeObjectURL(profilePhotoObjectUrl);
        profilePhotoObjectUrl = null;
    }
}

function setProfilePhotoPreview(url) {
    const preview = document.getElementById("profilePhotoPreview");
    if (!preview) {
        return;
    }

    clearProfilePhotoPreviewUrl();
    if (!url) {
        preview.style.display = "none";
        preview.removeAttribute("src");
        return;
    }

    preview.src = url;
    preview.style.display = "block";
    if (url.startsWith("blob:")) {
        profilePhotoObjectUrl = url;
    }
}

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
    updatePasswordStrengthMeter();

    if (profile.photoUrl) {
        try {
            const blob = await fetchProtectedBlob(profile.photoUrl);
            const objectUrl = URL.createObjectURL(blob);
            setProfilePhotoPreview(objectUrl);
        } catch (_) {
            setProfilePhotoPreview("");
        }
    } else {
        setProfilePhotoPreview("");
    }
}

async function saveProfile() {
    const fullName = document.getElementById("profileFullName").value.trim();
    const currentPassword = document.getElementById("profileCurrentPassword").value.trim();
    const newPassword = document.getElementById("profilePassword").value;
    const confirmPassword = document.getElementById("profileConfirmPassword").value;
    const passwordChanged = Boolean(newPassword.trim());

    if (!fullName) {
        throw new Error("Full Name cannot be empty.");
    }

    if (newPassword) {
        if (!currentPassword) {
            throw new Error("Current password is required to change password.");
        }
        if (!confirmPassword.trim()) {
            throw new Error("Please confirm the new password.");
        }
        if (newPassword !== confirmPassword) {
            throw new Error("New password and confirmation do not match.");
        }
        if (currentPassword === newPassword) {
            throw new Error("New password must be different from the current password.");
        }
        const issues = getPasswordPolicyViolations(newPassword);
        if (issues.length > 0) {
            throw new Error(issues[0]);
        }
    }

    let text;
    if (selectedProfilePhotoFile) {
        const payload = new FormData();
        payload.append("fullName", fullName);
        payload.append("password", newPassword);
        payload.append("currentPassword", currentPassword);
        payload.append("photo", selectedProfilePhotoFile, selectedProfilePhotoFile.name);
        text = await api("/api/profile", {
            method: "POST",
            body: payload
        }, false);
    } else {
        text = await api("/api/profile", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ fullName, password: newPassword, currentPassword })
        }, false);
    }

    if (passwordChanged) {
        const successMessage = text || "Password updated successfully. Please log in again.";
        document.getElementById("profileFeedback").textContent = successMessage;
        showToast(successMessage, false);
        setTimeout(() => {
            localStorage.removeItem("currentUser");
            window.location.href = `login.html?role=${encodeURIComponent(currentUser?.role || "STUDENT")}`;
        }, 1500);
        return;
    }

    if (currentUser) {
        currentUser.fullName = fullName;
        saveCurrentUser(currentUser);
        document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    selectedProfilePhotoFile = null;
    document.getElementById("profilePhotoInput").value = "";
    document.getElementById("profileCurrentPassword").value = "";
    document.getElementById("profilePassword").value = "";
    document.getElementById("profileConfirmPassword").value = "";
    document.getElementById("profileFeedback").textContent = text || "Profile updated successfully.";
    showToast(text || "Profile updated successfully.", false);

    await loadProfile();
}

async function loadBorrowHistory() {
    const items = await api("/api/borrows?status=all&sortBy=borrowDate&sortDir=desc");
    historyItems = Array.isArray(items) ? items : [];
    historyPage = 1;
    renderHistoryPage();
}

document.getElementById("saveProfileBtn")?.addEventListener("click", () => {
    saveProfile().catch((e) => {
        document.getElementById("profileFeedback").textContent = e.message;
        showToast(e.message, true);
    });
});

document.getElementById("profilePhotoInput")?.addEventListener("change", () => {
    const file = document.getElementById("profilePhotoInput").files?.[0] || null;
    selectedProfilePhotoFile = file;
    if (!file) {
        setProfilePhotoPreview("");
        return;
    }

    const previewUrl = URL.createObjectURL(file);
    setProfilePhotoPreview(previewUrl);
});

document.getElementById("profilePassword")?.addEventListener("input", updatePasswordStrengthMeter);

document.getElementById("historyPrevBtn")?.addEventListener("click", () => {
    if (historyPage > 1) {
        historyPage -= 1;
        renderHistoryPage();
    }
});

document.getElementById("historyNextBtn")?.addEventListener("click", () => {
    if (historyPage < totalHistoryPages()) {
        historyPage += 1;
        renderHistoryPage();
    }
});

if (currentUser) {
    loadProfile().catch((e) => showToast(e.message, true));
    loadBorrowHistory().catch((e) => showToast(e.message, true));
}

window.addEventListener("beforeunload", () => {
    clearProfilePhotoPreviewUrl();
});
