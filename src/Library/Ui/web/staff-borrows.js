const expectedRole = document.querySelector("main")?.dataset.role || "STAFF";
const currentUser = requireRole(expectedRole);

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

async function refreshBorrows() {
    const list = document.getElementById("borrows");
    if (!list) {
        return;
    }

    const selectAll = document.getElementById("selectAllBorrows");
    if (selectAll) {
        selectAll.checked = false;
    }

    const params = new URLSearchParams();
    const status = document.getElementById("borrowStatusFilter")?.value || "active";
    const sortBy = document.getElementById("borrowSortBy")?.value || "";
    const sortDir = document.getElementById("borrowSortDir")?.value || "asc";

    if (status) {
        params.set("status", status);
    }
    if (sortBy) {
        params.set("sortBy", sortBy);
        params.set("sortDir", sortDir);
    }

    const query = params.toString();
    const items = await api(query ? `/api/borrows?${query}` : "/api/borrows");
    list.innerHTML = "";

    if (!Array.isArray(items) || items.length === 0) {
        const li = document.createElement("li");
        li.className = "muted";
        li.textContent = "No borrow records yet.";
        list.appendChild(li);
        return;
    }

    items.forEach((item) => {
        const li = document.createElement("li");
        const isReturned = item.returned === true || String(item.status || "").toLowerCase() === "returned";
        const warningLabel = item.overdue
            ? "OVERDUE"
            : item.dueSoon
                ? `DUE SOON: ${item.daysUntilDue} day(s)`
                : "";

        if (item.overdue) {
            li.style.background = "rgba(139, 47, 39, 0.12)";
            li.style.border = "1px solid rgba(139, 47, 39, 0.45)";
        } else if (item.dueSoon) {
            li.style.background = "rgba(176, 116, 29, 0.12)";
            li.style.border = "1px solid rgba(176, 116, 29, 0.4)";
        }

        if (isReturned) {
            const returnedDate = item.returnedDate ? ` on ${formatDateOnly(item.returnedDate)}` : "";
            li.innerHTML = `<div class="borrow-item-row"><span>${item.bookTitle} (Returned${returnedDate})</span></div>`;
            list.appendChild(li);
            return;
        }

        li.innerHTML = `
            <div class="borrow-item-row">
                <input type="checkbox" class="borrow-select-box" data-book-id="${item.bookId}" data-book-title="${escapeHtml(item.bookTitle)}" aria-label="Select ${escapeHtml(item.bookTitle)}">
                <span>${item.bookTitle} (borrowed ${formatDateOnly(item.borrowDate || "")}, due ${formatDateOnly(item.dueDate || "")})${warningLabel ? ` [${warningLabel}]` : ""}</span>
                <div class="borrow-item-actions">
                    <a class="link-btn" href="staff-reader.html?bookId=${encodeURIComponent(item.bookId)}">Read</a>
                    <button class="secondary" type="button">Return</button>
                </div>
            </div>
        `;

        li.querySelector(".borrow-select-box")?.addEventListener("change", updateReturnSelectedButton);

        li.querySelector("button")?.addEventListener("click", async () => {
            try {
                if (!confirm(`Confirm return \"${item.bookTitle}\"?`)) {
                    return;
                }
                const text = await api("/api/return", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({ bookId: item.bookId })
                }, false);
                showToast(text, false);
                await refreshBorrows();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        list.appendChild(li);
    });

    updateReturnSelectedButton();
}

function getBorrowSelectBoxes() {
    return Array.from(document.querySelectorAll(".borrow-select-box"));
}

function updateReturnSelectedButton() {
    const button = document.getElementById("returnSelectedBtn");
    if (!button) {
        return;
    }
    const selected = getBorrowSelectBoxes().filter((cb) => cb.checked);
    button.textContent = `Return Selected (${selected.length})`;
    button.disabled = selected.length === 0;
}

document.getElementById("selectAllBorrows")?.addEventListener("change", (event) => {
    const checked = event.target.checked === true;
    getBorrowSelectBoxes().forEach((cb) => { cb.checked = checked; });
    updateReturnSelectedButton();
});

document.getElementById("returnSelectedBtn")?.addEventListener("click", async () => {
    const ids = getBorrowSelectBoxes().filter((cb) => cb.checked).map((cb) => cb.dataset.bookId);
    if (ids.length === 0) {
        return;
    }
    if (!confirm(`Confirm return ${ids.length} selected book(s)?`)) {
        return;
    }
    try {
        const payload = await api("/api/return-bulk", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ bookIds: ids.join(",") })
        });
        const failedCount = Array.isArray(payload.failed) ? payload.failed.length : 0;
        showToast(`Returned ${payload.succeeded || 0} book(s)${failedCount ? `; ${failedCount} failed` : ""}.`, failedCount > 0);
        if (failedCount) {
            console.warn("Bulk return failures:", payload.failed);
        }
        await refreshBorrows();
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("applyBorrowFiltersBtn")?.addEventListener("click", () => {
    refreshBorrows().catch((e) => showToast(e.message, true));
});

document.getElementById("resetBorrowFiltersBtn")?.addEventListener("click", () => {
    const status = document.getElementById("borrowStatusFilter");
    const sortBy = document.getElementById("borrowSortBy");
    const sortDir = document.getElementById("borrowSortDir");

    if (status) {
        status.value = "active";
    }
    if (sortBy) {
        sortBy.value = "";
    }
    if (sortDir) {
        sortDir.value = "asc";
    }

    refreshBorrows().catch((e) => showToast(e.message, true));
});

document.getElementById("checkBorrowRemindersBtn")?.addEventListener("click", async () => {
    try {
        const payload = await api("/api/borrow/reminders/check", {
            method: "POST"
        });
        showToast(`Reminder check complete. Generated ${payload.generated || 0} reminder(s).`, false);
        await refreshBorrows();
    } catch (error) {
        showToast(error.message, true);
    }
});

if (currentUser) {
    refreshBorrows().catch((e) => showToast(e.message, true));
}
