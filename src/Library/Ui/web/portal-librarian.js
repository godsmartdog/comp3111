const currentUser = requireRole("LIBRARIAN");
if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
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

if (currentUser) {
    refreshPending().catch((e) => showToast(e.message, true));
}
