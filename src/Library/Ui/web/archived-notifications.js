const currentUser = getCurrentUser();
if (!currentUser || !currentUser.sessionId) {
    window.location.href = `login.html?sessionMessage=${encodeURIComponent("Please login to view archived notifications.")}`;
}

let allNotifications = [];
let currentNotificationPage = 1;
const notificationPageSize = 10;
let stopWatchingNotificationChanges = null;

function getTotalNotificationPages() {
    return Math.max(1, Math.ceil(allNotifications.length / notificationPageSize));
}

function updateNotificationPagerUi() {
    const totalPages = getTotalNotificationPages();
    const pageInfo = document.getElementById("notificationPageInfo");
    const prevBtn = document.getElementById("notificationPrevPageBtn");
    const nextBtn = document.getElementById("notificationNextPageBtn");
    if (pageInfo) pageInfo.textContent = `${currentNotificationPage}/${totalPages}`;
    if (prevBtn) prevBtn.disabled = currentNotificationPage <= 1;
    if (nextBtn) nextBtn.disabled = currentNotificationPage >= totalPages;
}

function renderCurrentNotificationPage() {
    const list = document.getElementById("notificationsList");
    const status = document.getElementById("notificationStatus");
    if (!list || !status) return;
    list.innerHTML = "";
    if (allNotifications.length === 0) {
        status.textContent = "No archived notifications.";
        updateNotificationPagerUi();
        return;
    }

    const start = (currentNotificationPage - 1) * notificationPageSize;
    const end = start + notificationPageSize;
    const currentItems = allNotifications.slice(start, end);

    currentItems.forEach((item) => {
        const li = document.createElement("li");
        const created = formatDateOnly(item.createdDate || item.createdAt || "");
        const readLabel = item.read ? "Read" : "Unread";
        li.style.padding = "10px";
        li.style.borderRadius = "8px";
        li.style.marginBottom = "8px";
        li.style.background = item.read ? "rgba(60, 80, 120, 0.12)" : "rgba(32, 53, 79, 0.12)";
        li.innerHTML = `
            <div>
                <strong>[${readLabel}] ${item.title}</strong>
                <div>${item.message || ""}</div>
                <small>${created}${item.readAt ? ` | read at ${formatDateOnly(item.readAt)}` : ""}${item.archivedAt ? ` | archived at ${formatDateOnly(item.archivedAt)}` : ""}</small>
            </div>
            <div class="notification-actions">
                <button class="secondary notification-read-btn" type="button" ${item.read ? "disabled" : ""}>Mark As Read</button>
                <button class="secondary notification-unarchive-btn" type="button">Unarchive</button>
                <button class="danger notification-delete-btn" type="button">Delete</button>
            </div>
        `;

        li.querySelector('.notification-read-btn')?.addEventListener('click', async () => {
            try {
                const payload = await api('/api/notifications/read', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: formBody({ notificationId: item.id })
                });
                showToast(payload.message || 'Notification marked as read.', false);
                item.read = true;
                if (payload.readAt) item.readAt = payload.readAt;
                renderCurrentNotificationPage();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        li.querySelector('.notification-unarchive-btn')?.addEventListener('click', async () => {
            try {
                const payload = await api('/api/notifications/unarchive', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: formBody({ notificationId: item.id })
                });
                showToast(payload.message || 'Notification unarchived.', false);
                allNotifications = allNotifications.filter((n) => n.id !== item.id);
                if (currentNotificationPage > getTotalNotificationPages()) currentNotificationPage = getTotalNotificationPages();
                renderCurrentNotificationPage();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        li.querySelector('.notification-delete-btn')?.addEventListener('click', async () => {
            try {
                const payload = await api('/api/notifications/delete', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: formBody({ notificationId: item.id })
                });
                showToast(payload.message || 'Notification deleted.', false);
                allNotifications = allNotifications.filter((n) => n.id !== item.id);
                if (currentNotificationPage > getTotalNotificationPages()) currentNotificationPage = getTotalNotificationPages();
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
    const status = document.getElementById('notificationStatus');
    if (status) status.textContent = 'Loading archived notifications...';
    const params = new URLSearchParams();
    params.set('scope', 'archived');
    params.set('sortBy', 'createdAt');
    params.set('sortDir', 'desc');
    const keyword = document.getElementById('notificationKeyword')?.value?.trim() || '';
    const priorityFilter = document.getElementById('notificationPriorityFilter')?.value || 'all';
    const categoryFilter = document.getElementById('notificationCategoryFilter')?.value || 'all';
    const createdDateFrom = document.getElementById('notificationDateFrom')?.value || '';
    const createdDateTo = document.getElementById('notificationDateTo')?.value || '';
    if (keyword) params.set('q', keyword);
    if (priorityFilter !== 'all') params.set('priority', priorityFilter);
    if (categoryFilter !== 'all') params.set('category', categoryFilter);
    if (createdDateFrom) params.set('createdDateFrom', createdDateFrom);
    if (createdDateTo) params.set('createdDateTo', createdDateTo);

    const items = await api(`/api/notifications?${params.toString()}`);
    allNotifications = Array.isArray(items) ? items : [];
    currentNotificationPage = 1;
    renderCurrentNotificationPage();
}

document.getElementById('refreshNotificationsBtn')?.addEventListener('click', () => {
    refreshNotifications().catch((e) => showToast(e.message, true));
});

document.getElementById('applyNotificationFiltersBtn')?.addEventListener('click', () => {
    refreshNotifications().catch((e) => showToast(e.message, true));
});

document.getElementById('notificationPrevPageBtn')?.addEventListener('click', () => {
    if (currentNotificationPage > 1) {
        currentNotificationPage -= 1;
        renderCurrentNotificationPage();
    }
});

document.getElementById('notificationNextPageBtn')?.addEventListener('click', () => {
    if (currentNotificationPage < getTotalNotificationPages()) {
        currentNotificationPage += 1;
        renderCurrentNotificationPage();
    }
});

if (currentUser) {
    const welcomeLine = document.getElementById('welcomeLine');
    if (welcomeLine) welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    const rolePortalBtn = document.getElementById('rolePortalBtn');
    if (rolePortalBtn) {
        rolePortalBtn.href = rolePage(currentUser.role);
        rolePortalBtn.textContent = 'Back to Portal';
    }
    stopWatchingNotificationChanges = watchNotificationChangeSignal(() => {
        refreshNotifications().catch((e) => showToast(e.message, true));
    });
    attachLogout('logoutBtn');
    refreshNotifications().catch((e) => showToast(e.message, true));
}

window.addEventListener('beforeunload', () => {
    if (typeof stopWatchingNotificationChanges === 'function') {
        stopWatchingNotificationChanges();
    }
});
