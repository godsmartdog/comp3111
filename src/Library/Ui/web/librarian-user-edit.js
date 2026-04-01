const currentUser = requireRole("LIBRARIAN");
let loadedProfile = null;

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function selectedUsername() {
    return (getQueryParam("username") || "").trim();
}

function updateRoleSpecificFields(role) {
    const authorGroup = document.getElementById("authorBioGroup");
    const librarianGroup = document.getElementById("librarianEmployeeGroup");

    if (authorGroup) {
        authorGroup.style.display = role === "AUTHOR" ? "block" : "none";
    }
    if (librarianGroup) {
        librarianGroup.style.display = role === "LIBRARIAN" ? "block" : "none";
    }
}

function writeProfileToForm(profile) {
    document.getElementById("editUsername").textContent = profile.username || "";
    document.getElementById("editRole").textContent = profile.role || "";
    document.getElementById("editActive").textContent = profile.active ? "Active" : "Inactive";
    document.getElementById("editFullName").value = profile.fullName || "";
    document.getElementById("editBio").value = profile.bio || "";
    document.getElementById("editEmployeeId").value = profile.employeeId || "";
    document.getElementById("editPassword").value = "";
    updateRoleSpecificFields(profile.role || "");
}

async function loadManagedUserProfile() {
    const username = selectedUsername();
    const status = document.getElementById("editUserStatus");
    if (!username) {
        if (status) {
            status.textContent = "Missing username in URL.";
        }
        throw new Error("Missing username in URL.");
    }

    const payload = await api(`/api/librarian/users/profile?username=${encodeURIComponent(username)}`);
    loadedProfile = payload;
    writeProfileToForm(payload);

    if (status) {
        status.textContent = `Editing account ${payload.username} (${payload.role}).`;
    }
}

function validateBeforeSave(profile) {
    const fullName = document.getElementById("editFullName").value.trim();
    const bio = document.getElementById("editBio").value.trim();
    const employeeId = document.getElementById("editEmployeeId").value.trim();
    const password = document.getElementById("editPassword").value;

    if (!fullName) {
        throw new Error("Full Name cannot be empty.");
    }

    if (profile.role === "LIBRARIAN" && !employeeId) {
        throw new Error("Employee ID cannot be empty for librarian account.");
    }

    if (password.trim()) {
        const issues = getPasswordPolicyViolations(password);
        if (issues.length > 0) {
            throw new Error(issues[0]);
        }
    }

    return { fullName, bio, employeeId, password };
}

async function saveManagedUserProfile() {
    if (!loadedProfile) {
        throw new Error("Profile not loaded yet.");
    }

    const nextValues = validateBeforeSave(loadedProfile);

    const text = await api("/api/librarian/users/update", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({
            username: loadedProfile.username,
            fullName: nextValues.fullName,
            bio: nextValues.bio,
            employeeId: nextValues.employeeId,
            password: nextValues.password
        })
    }, false);

    if (currentUser && loadedProfile.username === currentUser.username) {
        currentUser.fullName = nextValues.fullName;
        saveCurrentUser(currentUser);
        const welcomeLine = document.getElementById("welcomeLine");
        if (welcomeLine) {
            welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
        }
    }

    document.getElementById("editPassword").value = "";
    document.getElementById("editUserFeedback").textContent = text;
    showToast(text, false);

    await loadManagedUserProfile();
}

document.getElementById("saveUserProfileBtn")?.addEventListener("click", () => {
    saveManagedUserProfile().catch((error) => {
        document.getElementById("editUserFeedback").textContent = error.message;
        showToast(error.message, true);
    });
});

if (currentUser) {
    loadManagedUserProfile().catch((error) => {
        document.getElementById("editUserStatus").textContent = error.message;
        showToast(error.message, true);
    });
}
