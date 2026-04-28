const expectedRole = document.querySelector("main")?.dataset.role || "AUTHOR";
const currentUser = requireRole(expectedRole);
let allNotifications = [];
let currentNotificationPage = 1;
const notificationPageSize = 5;

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function getTotalNotificationPages() {
    return Math.max(1, Math.ceil(allNotifications.length / notificationPageSize));
}

function updateNotificationPagerUi() {
    const totalPages = getTotalNotificationPages();
    const pageInfo = document.getElementById("notificationPageInfo");
    const prevBtn = document.getElementById("notificationPrevPageBtn");
    const nextBtn = document.getElementById("notificationNextPageBtn");

    if (pageInfo) {
        pageInfo.textContent = `${currentNotificationPage}/${totalPages}`;
    }
    if (prevBtn) {
        prevBtn.disabled = currentNotificationPage <= 1;
    }
    if (nextBtn) {
        nextBtn.disabled = currentNotificationPage >= totalPages;
    }
}

function renderCurrentNotificationPage() {
    const list = document.getElementById("notificationsList");
    const status = document.getElementById("notificationStatus");
    if (!list || !status) {
        return;
    }

    list.innerHTML = "";

    if (allNotifications.length === 0) {
        status.textContent = "No notifications.";
        updateNotificationPagerUi();
        return;
    }

    const unreadCount = allNotifications.filter((item) => !item.read).length;
    status.textContent = `Total: ${allNotifications.length}, Unread: ${unreadCount}`;

    const start = (currentNotificationPage - 1) * notificationPageSize;
    const end = start + notificationPageSize;
    const currentItems = allNotifications.slice(start, end);

    currentItems.forEach((item) => {
        const li = document.createElement("li");
        const readLabel = item.read ? "Read" : "Unread";
        const created = item.createdDate || item.createdAt || "";
        const priority = (item.priority || "NORMAL").toUpperCase();
        const category = item.category || "Other";
        const highLabel = priority === "HIGH" ? " !" : "";
        const priorityClass = `priority-${priority.toLowerCase()}`;

        li.style.padding = "10px";
        li.style.borderRadius = "8px";
        li.style.marginBottom = "8px";
        li.style.background = item.read ? "rgba(60, 80, 120, 0.12)" : "rgba(32, 53, 79, 0.2)";
        li.style.border = item.read ? "1px solid rgba(120, 140, 180, 0.35)" : "1px solid rgba(74, 116, 173, 0.45)";

        li.innerHTML = `
            <div>
                <strong>[${readLabel}] ${item.title}${highLabel}</strong>
                <span style="display:inline-block;margin-left:8px;padding:2px 8px;border-radius:999px;background:#2f4968;color:#fff;font-size:0.75rem;font-weight:700;">${category}</span>
                <span class="${priorityClass}" style="margin-left:8px;font-size:0.75rem;font-weight:700;padding:2px 8px;border-radius:999px;${priority === "HIGH" ? "background:#8b2f27;color:#fff;" : priority === "LOW" ? "background:#355b2a;color:#fff;" : "background:#2f4968;color:#fff;"}">${priority}</span>
                <div>${item.message || ""}</div>
                <small>${created}${item.readAt ? ` | read at ${item.readAt}` : ""}${item.archivedAt ? ` | archived at ${item.archivedAt}` : ""}</small>
            </div>
            <div class="notification-actions">
                <button class="secondary notification-read-btn" type="button" ${item.read ? "disabled" : ""}>Mark As Read</button>
                <button class="secondary notification-archive-btn" type="button">Archive</button>
                <button class="danger notification-delete-btn" type="button">Delete</button>
            </div>
        `;

        li.querySelector(".notification-read-btn")?.addEventListener("click", async () => {
            try {
                const payload = await api("/api/notifications/read", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({ notificationId: item.id })
                });
                showToast(payload.message || "Notification marked as read.", false);
                item.read = true;
                if (payload.readAt) {
                    item.readAt = payload.readAt;
                }
                renderCurrentNotificationPage();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        li.querySelector(".notification-delete-btn")?.addEventListener("click", async () => {
            try {
                const payload = await api("/api/notifications/delete", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({ notificationId: item.id })
                });
                showToast(payload.message || "Notification deleted.", false);
                allNotifications = allNotifications.filter((notification) => notification.id !== item.id);
                emitNotificationChangeSignal("delete");
                if (currentNotificationPage > getTotalNotificationPages()) {
                    currentNotificationPage = getTotalNotificationPages();
                }
                renderCurrentNotificationPage();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        li.querySelector(".notification-archive-btn")?.addEventListener("click", async () => {
            try {
                const payload = await api("/api/notifications/archive", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({ notificationId: item.id })
                });
                showToast(payload.message || "Notification archived.", false);
                allNotifications = allNotifications.filter((notification) => notification.id !== item.id);
                emitNotificationChangeSignal("archive");
                if (currentNotificationPage > getTotalNotificationPages()) {
                    currentNotificationPage = getTotalNotificationPages();
                }
                renderCurrentNotificationPage();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        list.appendChild(li);
    });

    updateNotificationPagerUi();
}

async function refreshNotifications() {
    const status = document.getElementById("notificationStatus");
    if (status) {
        status.textContent = "Loading notifications...";
    }

    const params = new URLSearchParams();
    params.set("scope", "active");
    params.set("sortBy", "createdAt");
    params.set("sortDir", "desc");
    const keyword = document.getElementById("notificationKeyword")?.value?.trim() || "";
    const priorityFilter = document.getElementById("notificationPriorityFilter")?.value || "all";
    const categoryFilter = document.getElementById("notificationCategoryFilter")?.value || "all";
    const createdDateFrom = document.getElementById("notificationDateFrom")?.value || "";
    const createdDateTo = document.getElementById("notificationDateTo")?.value || "";
    if (keyword) {
        params.set("q", keyword);
    }
    if (priorityFilter !== "all") {
        params.set("priority", priorityFilter);
    }
    if (categoryFilter !== "all") {
        params.set("category", categoryFilter);
    }
    if (createdDateFrom) {
        params.set("createdDateFrom", createdDateFrom);
    }
    if (createdDateTo) {
        params.set("createdDateTo", createdDateTo);
    }

    const items = await api(`/api/notifications?${params.toString()}`);
    allNotifications = Array.isArray(items) ? items : [];
    currentNotificationPage = 1;
    renderCurrentNotificationPage();
    try { updateNotificationBadge(); } catch (e) { /* ignore */ }
}

document.getElementById("refreshNotificationsBtn")?.addEventListener("click", () => {
    refreshNotifications().catch((e) => showToast(e.message, true));
});

document.getElementById("applyNotificationFiltersBtn")?.addEventListener("click", () => {
    refreshNotifications().catch((e) => showToast(e.message, true));
});

document.getElementById("notificationPrevPageBtn")?.addEventListener("click", () => {
    if (currentNotificationPage > 1) {
        currentNotificationPage -= 1;
        renderCurrentNotificationPage();
    }
});

document.getElementById("notificationNextPageBtn")?.addEventListener("click", () => {
    if (currentNotificationPage < getTotalNotificationPages()) {
        currentNotificationPage += 1;
        renderCurrentNotificationPage();
    }
});

if (currentUser) {
    refreshNotifications().catch((e) => showToast(e.message, true));
}
