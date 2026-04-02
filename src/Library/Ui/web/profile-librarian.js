const currentUser = requireRole("LIBRARIAN");
if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let approvedItems = [];
let approvedPage = 1;
const approvedPageSize = 10;
let selectedLibrarianProfilePhotoFile = null;
let librarianProfilePhotoObjectUrl = null;

function updateLibrarianPasswordStrengthMeter() {
    const password = document.getElementById("librarianProfilePassword")?.value || "";
    const info = getPasswordStrengthInfo(password);
    const fill = document.getElementById("librarianProfilePasswordStrengthFill");
    const text = document.getElementById("librarianProfilePasswordStrengthText");
    if (fill) {
        fill.style.width = `${Math.max(10, (info.score / info.max) * 100)}%`;
        fill.style.background = info.color;
    }
    if (text) {
        text.textContent = info.label;
        text.style.color = info.color;
    }
}

function clearLibrarianPhotoPreview() {
    if (librarianProfilePhotoObjectUrl) {
        URL.revokeObjectURL(librarianProfilePhotoObjectUrl);
        librarianProfilePhotoObjectUrl = null;
    }
}

function setLibrarianPhotoPreview(url) {
    const preview = document.getElementById("librarianProfilePhotoPreview");
    if (!preview) {
        return;
    }
    clearLibrarianPhotoPreview();
    if (!url) {
        preview.style.display = "none";
        preview.removeAttribute("src");
        return;
    }
    preview.src = url;
    preview.style.display = "block";
}

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
    document.getElementById("librarianProfileConfirmPassword").value = "";
    updateLibrarianPasswordStrengthMeter();

    if (payload.photoUrl) {
        try {
            const blob = await fetchProtectedBlob(payload.photoUrl);
            const objectUrl = URL.createObjectURL(blob);
            librarianProfilePhotoObjectUrl = objectUrl;
            setLibrarianPhotoPreview(objectUrl);
        } catch (_) {
            setLibrarianPhotoPreview("");
        }
    } else {
        setLibrarianPhotoPreview("");
    }
}

async function saveLibrarianProfile() {
    const fullName = document.getElementById("librarianProfileFullName").value.trim();
    const employeeId = document.getElementById("librarianProfileEmployeeId").value.trim();
    const currentPassword = document.getElementById("librarianProfileCurrentPassword").value.trim();
    const password = document.getElementById("librarianProfilePassword").value;
    const confirmPassword = document.getElementById("librarianProfileConfirmPassword").value;
    const passwordChanged = Boolean(password.trim());

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

    let text;
    if (selectedLibrarianProfilePhotoFile) {
        const payload = new FormData();
        payload.append("fullName", fullName);
        payload.append("employeeId", employeeId);
        payload.append("password", password);
        payload.append("currentPassword", currentPassword);
        payload.append("photo", selectedLibrarianProfilePhotoFile, selectedLibrarianProfilePhotoFile.name);
        text = await api("/api/librarian/profile", {
            method: "POST",
            body: payload
        }, false);
    } else {
        text = await api("/api/librarian/profile", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ fullName, employeeId, password, currentPassword })
        }, false);
    }

    if (passwordChanged) {
        const successMessage = text || "Password updated successfully. Please log in again.";
        document.getElementById("librarianProfileFeedback").textContent = successMessage;
        showToast(successMessage, false);
        setTimeout(() => {
            localStorage.removeItem("currentUser");
            window.location.href = `login.html?role=${encodeURIComponent(currentUser?.role || "LIBRARIAN")}`;
        }, 1500);
        return;
    }

    if (currentUser) {
        currentUser.fullName = fullName;
        saveCurrentUser(currentUser);
        document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    document.getElementById("librarianProfileCurrentPassword").value = "";
    document.getElementById("librarianProfilePassword").value = "";
    document.getElementById("librarianProfileConfirmPassword").value = "";
    document.getElementById("librarianProfileFeedback").textContent = text;
    showToast(text, false);
    selectedLibrarianProfilePhotoFile = null;
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

document.getElementById("librarianProfilePhotoInput")?.addEventListener("change", () => {
    const file = document.getElementById("librarianProfilePhotoInput").files?.[0] || null;
    selectedLibrarianProfilePhotoFile = file;
    if (!file) {
        setLibrarianPhotoPreview("");
        return;
    }
    clearLibrarianPhotoPreview();
    const previewUrl = URL.createObjectURL(file);
    librarianProfilePhotoObjectUrl = previewUrl;
    setLibrarianPhotoPreview(previewUrl);
});

document.getElementById("librarianProfilePassword")?.addEventListener("input", updateLibrarianPasswordStrengthMeter);

if (currentUser) {
    loadLibrarianProfile().catch((e) => showToast(e.message, true));
    loadApprovedHistory().catch((e) => showToast(e.message, true));
}
