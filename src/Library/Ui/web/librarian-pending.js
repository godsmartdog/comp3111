const currentUser = requireRole("LIBRARIAN");
let submissionSortEnabled = false;

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function renderPending(items) {
    const tbody = document.getElementById("pendingBody");
    if (!tbody) {
        return;
    }

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

        const readBtn = document.createElement("button");
        readBtn.className = "secondary";
        readBtn.textContent = "Read";
        readBtn.addEventListener("click", () => {
            window.location.href = `librarian-submission-reader.html?submissionId=${encodeURIComponent(item.id)}`;
        });

        const rejectBtn = document.createElement("button");
        rejectBtn.className = "danger";
        rejectBtn.textContent = "Reject";
        rejectBtn.addEventListener("click", () => review(item.id, "reject"));

        actionCell.appendChild(readBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(approveBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(rejectBtn);

        tbody.appendChild(row);
    });
}

async function refreshPending() {
    const searchInput = document.getElementById("submissionSearchInput");
    const sortDirFilter = document.getElementById("submissionSortDirFilter");

    const query = new URLSearchParams();
    const keyword = searchInput ? searchInput.value.trim() : "";
    const sortDir = sortDirFilter ? sortDirFilter.value : "asc";

    if (keyword) {
        query.set("q", keyword);
    }
    query.set("status", "pending");
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
    const sortDirFilter = document.getElementById("submissionSortDirFilter");

    if (searchInput) {
        searchInput.value = "";
    }
    if (sortDirFilter) {
        sortDirFilter.value = "asc";
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

document.getElementById("refreshPendingBtn")?.addEventListener("click", () => {
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

if (currentUser) {
    refreshPending().catch((e) => showToast(e.message, true));
}
