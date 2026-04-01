const expectedRole = document.querySelector("main")?.dataset.role || "LIBRARIAN";
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

        li.innerHTML = `
            <div>
                <strong>[${readLabel}] ${item.title}</strong>
                <div>${item.message || ""}</div>
                <small>${item.createdAt || ""}</small>
            </div>
            <div class="notification-actions">
                <button class="secondary notification-read-btn" type="button" ${item.read ? "disabled" : ""}>Mark As Read</button>
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

    const items = await api("/api/notifications?scope=active&sortBy=createdAt&sortDir=desc");
    allNotifications = Array.isArray(items) ? items : [];
    currentNotificationPage = 1;
    renderCurrentNotificationPage();
}

document.getElementById("refreshNotificationsBtn")?.addEventListener("click", () => {
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
