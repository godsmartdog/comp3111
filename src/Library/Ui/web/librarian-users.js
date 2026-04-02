const currentUser = requireRole("LIBRARIAN");
let currentItems = [];

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function formatDateTime(value) {
    if (!value) {
        return "-";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleString();
}

function selectedUsernames() {
    const checkboxes = Array.from(document.querySelectorAll(".user-select-box:checked"));
    return checkboxes.map((node) => node.value);
}

function updateSelectionHint() {
    const hint = document.getElementById("usersSelectionHint");
    if (!hint) {
        return;
    }
    const selected = selectedUsernames().length;
    hint.textContent = `Selected: ${selected} user(s).`;
}

function syncSelectAllState() {
    const selectAll = document.getElementById("selectAllUsers");
    if (!selectAll) {
        updateSelectionHint();
        return;
    }

    const selectable = Array.from(document.querySelectorAll(".user-select-box:not(:disabled)"));
    const selected = selectable.filter((node) => node.checked);

    if (selectable.length === 0) {
        selectAll.checked = false;
        selectAll.indeterminate = false;
        updateSelectionHint();
        return;
    }

    selectAll.checked = selected.length === selectable.length;
    selectAll.indeterminate = selected.length > 0 && selected.length < selectable.length;
    updateSelectionHint();
}

function renderUsers(items) {
    const body = document.getElementById("usersBody");
    const status = document.getElementById("usersStatus");
    if (!body || !status) {
        return;
    }

    body.innerHTML = "";
    currentItems = Array.isArray(items) ? items : [];

    if (currentItems.length === 0) {
        status.textContent = "No users found.";
        return;
    }

    const activeCount = currentItems.filter((item) => item.active).length;
    status.textContent = `Found ${currentItems.length} user(s). Active: ${activeCount}, Inactive: ${currentItems.length - activeCount}.`;

    currentItems.forEach((item) => {
        const row = document.createElement("tr");
        const activity = Array.isArray(item.recentActivity) && item.recentActivity.length > 0
            ? `<ul>${item.recentActivity.map((entry) => `<li>${entry}</li>`).join("")}</ul>`
            : "<span class='muted'>No recent activity.</span>";

        row.innerHTML = `
            <td><input class="user-select-box" type="checkbox" value="${item.username}" ${item.isCurrentLibrarian ? "disabled" : ""}></td>
            <td>${item.username}</td>
            <td>${item.fullName || ""}</td>
            <td>${item.role}</td>
            <td>${item.active ? "Active" : "Inactive"}</td>
            <td>${formatDateTime(item.lastLoginAt)}</td>
            <td>Active: ${item.activeBorrowCount || 0}<br>Total: ${item.totalBorrowCount || 0}</td>
            <td>${activity}</td>
            <td></td>
        `;

        const actionCell = row.lastElementChild;

        const editBtn = document.createElement("button");
        editBtn.className = "secondary";
        editBtn.type = "button";
        editBtn.textContent = "Edit";
        editBtn.addEventListener("click", () => openUserEditPage(item));

        const toggleBtn = document.createElement("button");
        toggleBtn.className = item.active ? "danger" : "secondary";
        toggleBtn.type = "button";
        toggleBtn.textContent = item.active ? "Deactivate" : "Activate";
        toggleBtn.disabled = !!item.isCurrentLibrarian && item.active;
        toggleBtn.addEventListener("click", () => toggleUserActive(item));

        actionCell.appendChild(editBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(toggleBtn);

        row.querySelector(".user-select-box")?.addEventListener("change", () => {
            syncSelectAllState();
        });

        body.appendChild(row);
    });

    syncSelectAllState();
}

async function loadUsers() {
    const query = new URLSearchParams();
    const q = document.getElementById("userSearchInput")?.value?.trim() || "";
    const role = document.getElementById("userRoleFilter")?.value || "all";
    const status = document.getElementById("userStatusFilter")?.value || "all";

    if (q) {
        query.set("q", q);
    }
    query.set("role", role);
    query.set("status", status);

    const items = await api(`/api/librarian/users?${query.toString()}`);
    renderUsers(items);
}

function openUserEditPage(item) {
    if (!item?.username) {
        showToast("Unable to open edit page for this user.", true);
        return;
    }
    window.location.href = `librarian-user-edit.html?username=${encodeURIComponent(item.username)}`;
}

async function toggleUserActive(item) {
    const nextActive = !item.active;
    const actionLabel = nextActive ? "activate" : "deactivate";
    if (!confirm(`Confirm ${actionLabel} account ${item.username}?`)) {
        return;
    }

    try {
        const text = await api("/api/librarian/users/deactivate", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ username: item.username, active: nextActive })
        }, false);
        showToast(text, false);
        await loadUsers();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function runBulkAction(action) {
    const usernames = selectedUsernames();
    if (usernames.length === 0) {
        showToast("Select at least one user.", true);
        return;
    }

    const label = action === "activate" ? "activate" : "deactivate";
    if (!confirm(`Confirm ${label} ${usernames.length} selected account(s)?`)) {
        return;
    }

    try {
        const text = await api("/api/librarian/users/bulk", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ action, usernames: usernames.join(",") })
        }, false);
        showToast(text, false);
        await loadUsers();
    } catch (error) {
        showToast(error.message, true);
    }
}

document.getElementById("applyUserFiltersBtn")?.addEventListener("click", () => {
    loadUsers().catch((e) => showToast(e.message, true));
});

document.getElementById("resetUserFiltersBtn")?.addEventListener("click", () => {
    const search = document.getElementById("userSearchInput");
    const role = document.getElementById("userRoleFilter");
    const status = document.getElementById("userStatusFilter");
    if (search) search.value = "";
    if (role) role.value = "all";
    if (status) status.value = "all";
    loadUsers().catch((e) => showToast(e.message, true));
});

document.getElementById("refreshUsersBtn")?.addEventListener("click", () => {
    loadUsers().catch((e) => showToast(e.message, true));
});

document.getElementById("bulkDeactivateBtn")?.addEventListener("click", () => {
    runBulkAction("deactivate").catch((e) => showToast(e.message, true));
});

document.getElementById("bulkActivateBtn")?.addEventListener("click", () => {
    runBulkAction("activate").catch((e) => showToast(e.message, true));
});

document.getElementById("selectAllUsers")?.addEventListener("change", (event) => {
    const checked = !!event.target?.checked;
    document.querySelectorAll(".user-select-box:not(:disabled)").forEach((node) => {
        node.checked = checked;
    });
    syncSelectAllState();
});

if (currentUser) {
    loadUsers().catch((e) => showToast(e.message, true));
}
