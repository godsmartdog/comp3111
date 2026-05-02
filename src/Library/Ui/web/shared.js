function showToast(message, isError) {
    const toast = document.getElementById("toast");
    if (!toast) {
        return;
    }

    toast.textContent = message;
    toast.style.background = isError ? "#8b2f27" : "#20354f";
    toast.classList.add("show");
    setTimeout(() => toast.classList.remove("show"), 2200);
}

function parseSnapshotPayload(raw) {
    if (!raw) {
        return {};
    }
    try {
        return JSON.parse(raw);
    } catch (e) {
        return {};
    }
}

function stringifySnapshotPayload(payload) {
    if (payload === undefined || payload === null || payload === "") {
        return "";
    }
    if (typeof payload === "string") {
        return payload;
    }
    try {
        return JSON.stringify(payload);
    } catch (e) {
        return "";
    }
}

async function api(path, options = {}, expectJson = true) {
    const merged = { ...options };
    const headers = { ...(options.headers || {}) };
    const current = getCurrentUser();
    if (current && current.sessionId) {
        headers["X-Session-Id"] = current.sessionId;
    }
    merged.headers = headers;

    const response = await fetch(path, merged);
    if (!response.ok) {
        const text = await response.text();
        const error = new Error(text || "Request failed.");
        error.status = response.status;
        if (response.status === 401) {
            const role = current?.role || "";
            const redirect = role
                ? `login.html?role=${encodeURIComponent(role)}&sessionMessage=${encodeURIComponent(text || "Session expired. Please login again.")}`
                : `login.html?sessionMessage=${encodeURIComponent(text || "Session expired. Please login again.")}`;
            localStorage.removeItem("currentUser");
            window.location.href = redirect;
        }
        throw error;
    }
    return expectJson ? response.json() : response.text();
}

    async function fetchProtectedBlob(url) {
        const headers = {};
        const current = getCurrentUser();
        if (current?.sessionId) {
            headers["X-Session-Id"] = current.sessionId;
        }
        const response = await fetch(url, { headers });
        if (!response.ok) {
            throw new Error(await response.text());
        }
        return response.blob();
    }

    function getPasswordStrengthInfo(password) {
        const value = String(password || "");
        const checks = [
            value.length >= 8,
            !value.includes(" "),
            /[A-Z]/.test(value),
            /[a-z]/.test(value),
            /\d/.test(value),
            /[^A-Za-z0-9]/.test(value)
        ];
        const score = checks.filter(Boolean).length;

        let label = "Weak";
        let color = "#c0392b";
        if (score >= 5) {
            label = "Very Strong";
            color = "#1f7a3b";
        } else if (score === 4) {
            label = "Strong";
            color = "#2f7d32";
        } else if (score === 3) {
            label = "Medium";
            color = "#b56d00";
        }

        return { score, max: checks.length, label, color };
    }

function formBody(payload) {
    const params = new URLSearchParams();
    Object.entries(payload).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
            params.set(key, String(value));
        }
    });
    return params;
}

function parseGenres(raw) {
    return raw
        .split(",")
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .join(",");
}

function rolePage(role) {
    if (role === "STUDENT") return "mainStudent.html";
    if (role === "STAFF") return "mainStaff.html";
    if (role === "AUTHOR") return "mainAuthor.html";
    if (role === "LIBRARIAN") return "mainLibrarian.html";
    return "index.html";
}

function escapeHtml(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function formatDateOnly(value) {
    if (!value) {
        return "";
    }

    const raw = String(value).trim();
    if (!raw) {
        return "";
    }

    const datePart = raw.split("T")[0].split(" ")[0];
    const match = datePart.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/);
    if (match) {
        const year = match[1];
        const month = match[2].padStart(2, "0");
        const day = match[3].padStart(2, "0");
        return `${year}-${month}-${day}`;
    }

    const parsed = new Date(raw);
    if (!Number.isNaN(parsed.getTime())) {
        return parsed.toISOString().slice(0, 10);
    }

    return datePart;
}

function formatReviewDisplayHtml(item) {
    const isAnonymous = Boolean(item?.anonymous);
    const reviewerName = isAnonymous
        ? "Anonymous"
        : (item?.reviewerFullName || item?.username || "Unknown reviewer");
    const reviewer = escapeHtml(reviewerName);
    const rating = escapeHtml(`${Number(item?.rating || 0)}/5`);
    const reviewText = escapeHtml(item?.reviewText || "");
    const replyText = escapeHtml(item?.replyText || "");
    const replyAt = escapeHtml(formatDateOnly(item?.repliedAt || ""));
    const flagged = Boolean(item?.flagged);
    const flagReason = escapeHtml(item?.flagReason || "");
    const flaggedAt = escapeHtml(formatDateOnly(item?.flaggedAt || ""));
    const replyLine = replyText
        ? `<div class="muted" style="margin-top:6px;">Author reply${replyAt ? ` (${replyAt})` : ""}: ${replyText}</div>`
        : "";
    const flagLine = flagged
        ? `<div class="muted" style="margin-top:6px;">Reported${flaggedAt ? ` (${flaggedAt})` : ""}${flagReason ? `: ${flagReason}` : ""}</div>`
        : "";

    return `
        <div style="white-space:pre-wrap;">
            <strong>${reviewer}: ${rating} - ${reviewText}</strong>
            ${replyLine}
            ${flagLine}
        </div>
    `;
}

function promptForCurrentPassword() {
    const value = window.prompt("Enter your current password to save profile changes:", "");
    if (value === null) {
        return null;
    }
    return value.trim();
}

const NOTIFICATION_CHANGE_SIGNAL_KEY = "notification-change-signal";

function emitNotificationChangeSignal(action) {
    try {
        const payload = JSON.stringify({
            action: action || "update",
            ts: Date.now()
        });
        localStorage.setItem(NOTIFICATION_CHANGE_SIGNAL_KEY, payload);
    } catch (e) {
        // Best effort only.
    }
}

function watchNotificationChangeSignal(onChange) {
    if (typeof onChange !== "function") {
        return () => {};
    }

    const handler = (event) => {
        if (event.key !== NOTIFICATION_CHANGE_SIGNAL_KEY || !event.newValue) {
            return;
        }
        onChange(event.newValue);
    };

    window.addEventListener("storage", handler);
    return () => window.removeEventListener("storage", handler);
}

// Updates a small notification badge in the topbar showing unread count.
async function updateNotificationBadge() {
    try {
        const current = getCurrentUser();
        if (!current?.sessionId) return;
        const items = await api('/api/notifications?scope=active');
        const unread = Array.isArray(items) ? items.filter((i) => !i.read).length : 0;

        const container = document.querySelector('.top-actions');
        if (!container) return;

        const badge = container.querySelector('.notification-badge');
        if (!badge) return; // Do not auto-create badge; only update existing element
        badge.textContent = `Notifications (${unread})`;
        if (unread === 0) {
            badge.classList.add('muted');
        } else {
            badge.classList.remove('muted');
        }
    } catch (e) {
        // Silent failure — badge is non-critical
    }
}

const DEV_CRASH_QUERY_KEY = "devCrash";
const DEV_CRASH_ENABLED_KEY = "devCrashEnabled";
const DEV_RANDOM_CRASH_CHANCE_KEY = "devRandomCrashChance";
const DEV_CRASH_HEADER = "X-Crash-Test-Hook";
const DEV_CRASH_TOKEN = "enable";
let devRandomCrashTimer = null;

function syncDevCrashModeFromQuery() {
    const url = new URL(window.location.href);
    const value = url.searchParams.get(DEV_CRASH_QUERY_KEY);
    if (value === "1") {
        localStorage.setItem(DEV_CRASH_ENABLED_KEY, "1");
    }
    if (value === "0") {
        localStorage.removeItem(DEV_CRASH_ENABLED_KEY);
    }
}

function isDevCrashModeEnabled() {
    syncDevCrashModeFromQuery();
    return localStorage.getItem(DEV_CRASH_ENABLED_KEY) === "1";
}

function getDevRandomCrashChance() {
    const raw = localStorage.getItem(DEV_RANDOM_CRASH_CHANCE_KEY);
    const value = Number(raw);
    if (Number.isNaN(value) || value < 0 || value > 1) {
        return 0;
    }
    return value;
}

function redirectToRoleHomeWithMessage(message) {
    const current = getCurrentUser();
    const target = rolePage(current?.role || "");
    if (message) {
        showToast(message, true);
    }
    window.location.href = target;
}

async function invokeDevCrashEndpoint(snapshot, crashAction) {
    const current = getCurrentUser();
    if (!current?.sessionId) {
        throw new Error("Cannot run crash test without an active session.");
    }

    const form = formBody({
        portalKey: snapshot?.portalKey || window.location.pathname,
        lastViewKey: snapshot?.lastViewKey || "default",
        lastAction: snapshot?.lastAction || "crash-test",
        statePayload: stringifySnapshotPayload(snapshot?.statePayload || {})
    });

    const response = await fetch("/api/dev/crash-test", {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            "X-Session-Id": current.sessionId,
            [DEV_CRASH_HEADER]: DEV_CRASH_TOKEN
        },
        body: form
    });

    if (!response.ok) {
        throw new Error((await response.text()) || "Crash test request failed.");
    }

    showToast("Crash simulated. Reloading page...", false);
    setTimeout(() => {
        if (crashAction === "random") {
            throw new Error("Random crash simulation triggered.");
        }
        throw new Error("Manual crash simulation triggered.");
    }, 120);
}

async function triggerCrashSimulation(reason) {
    const context = window.__portalSnapshotContext;
    const snapshot = {
        portalKey: context?.portalKey || window.location.pathname,
        lastViewKey: context?.getViewKey ? context.getViewKey() : "default",
        lastAction: reason || "crash-test",
        statePayload: context?.getState ? context.getState() : {}
    };
    await invokeDevCrashEndpoint(snapshot, reason || "manual");
}

function applyRandomCrashSimulation() {
    if (devRandomCrashTimer) {
        clearInterval(devRandomCrashTimer);
        devRandomCrashTimer = null;
    }

    const chance = getDevRandomCrashChance();
    if (chance <= 0 || !isDevCrashModeEnabled() || !getCurrentUser()?.sessionId) {
        return;
    }

    devRandomCrashTimer = setInterval(() => {
        if (Math.random() < chance) {
            triggerCrashSimulation("random-crash").catch((error) => showToast(error.message, true));
        }
    }, 15000);
}

function installDevCrashTools() {
    if (!isDevCrashModeEnabled() || !getCurrentUser()?.sessionId) {
        return;
    }

    if (document.getElementById("devCrashTools")) {
        applyRandomCrashSimulation();
        return;
    }

    const box = document.createElement("div");
    box.id = "devCrashTools";
    box.style.position = "fixed";
    box.style.right = "12px";
    box.style.bottom = "12px";
    box.style.zIndex = "90";
    box.style.background = "rgba(20, 28, 40, 0.95)";
    box.style.color = "#fff";
    box.style.padding = "10px";
    box.style.borderRadius = "10px";
    box.style.border = "1px solid rgba(102, 153, 204, 0.55)";
    box.style.display = "flex";
    box.style.flexDirection = "column";
    box.style.gap = "8px";
    box.style.minWidth = "220px";

    const title = document.createElement("div");
    title.textContent = "Dev Crash Tools";
    title.style.fontWeight = "700";

    const crashBtn = document.createElement("button");
    crashBtn.type = "button";
    crashBtn.textContent = "Crash Test";
    crashBtn.addEventListener("click", () => {
        triggerCrashSimulation("manual-crash").catch((error) => showToast(error.message, true));
    });

    const label = document.createElement("label");
    label.textContent = "Random crash chance";
    label.style.fontSize = "0.85rem";

    const chanceSelect = document.createElement("select");
    chanceSelect.innerHTML = ""
        + '<option value="0">Off</option>'
        + '<option value="0.05">5%</option>'
        + '<option value="0.1">10%</option>'
        + '<option value="0.2">20%</option>';
    chanceSelect.value = String(getDevRandomCrashChance());
    chanceSelect.addEventListener("change", () => {
        localStorage.setItem(DEV_RANDOM_CRASH_CHANCE_KEY, chanceSelect.value);
        applyRandomCrashSimulation();
    });

    const hint = document.createElement("small");
    hint.textContent = "Adds crash simulation during runtime for resilience testing.";

    box.appendChild(title);
    box.appendChild(crashBtn);
    box.appendChild(label);
    box.appendChild(chanceSelect);
    box.appendChild(hint);
    document.body.appendChild(box);
    applyRandomCrashSimulation();
}

function saveCurrentUser(user) {
    localStorage.setItem("currentUser", JSON.stringify(user));
}

function getCurrentUser() {
    const raw = localStorage.getItem("currentUser");
    if (!raw) {
        return null;
    }
    try {
        return JSON.parse(raw);
    } catch (e) {
        localStorage.removeItem("currentUser");
        return null;
    }
}

function requireRole(expectedRole) {
    const user = getCurrentUser();
    if (!user || !user.sessionId) {
        window.location.href = `login.html?role=${encodeURIComponent(expectedRole)}`;
        return null;
    }
    if (user.role !== expectedRole) {
        window.location.href = rolePage(user.role);
        return null;
    }
    return user;
}

function attachLogout(buttonId) {
    const button = document.getElementById(buttonId);
    if (!button) {
        return;
    }
    button.addEventListener("click", async () => {
        try {
            await clearSessionSnapshot(true);
            await api("/api/logout", { method: "POST" }, false);
        } catch (e) {
            // Ignore network/logout errors and continue clearing local session.
        }
        removeSessionRestoreBanner();
        localStorage.removeItem("currentUser");
        window.location.href = "index.html";
    });
}

function removeSessionRestoreBanner() {
    document.getElementById("sessionRestoreBanner")?.remove();
}

async function saveSessionSnapshot(snapshot, options = {}) {
    const current = getCurrentUser();
    if (!current?.sessionId) {
        return null;
    }

    const body = formBody({
        portalKey: snapshot?.portalKey || "",
        lastViewKey: snapshot?.lastViewKey || "",
        lastAction: snapshot?.lastAction || "",
        statePayload: stringifySnapshotPayload(snapshot?.statePayload)
    });

    try {
        if (options.keepalive) {
            await fetch("/api/session-snapshot/save", {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                    "X-Session-Id": current.sessionId
                },
                body,
                keepalive: true
            });
            return null;
        }

        return await api("/api/session-snapshot/save", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body
        });
    } catch (error) {
        if (!options.silent) {
            throw error;
        }
        return null;
    }
}

async function getSessionSnapshot(options = {}) {
    try {
        return await api("/api/session-snapshot");
    } catch (error) {
        if (!options.silent) {
            throw error;
        }
        return { exists: false };
    }
}

async function clearSessionSnapshot(silent = false) {
    try {
        removeSessionRestoreBanner();
        return await api("/api/session-snapshot/clear", { method: "POST" });
    } catch (error) {
        if (!silent) {
            throw error;
        }
        return null;
    }
}

function showSessionRestoreBanner(message, onRestore, onDismiss) {
    removeSessionRestoreBanner();

    const banner = document.createElement("div");
    banner.id = "sessionRestoreBanner";
    banner.style.position = "sticky";
    banner.style.top = "0";
    banner.style.zIndex = "50";
    banner.style.margin = "12px auto";
    banner.style.maxWidth = "1100px";
    banner.style.padding = "12px 16px";
    banner.style.borderRadius = "12px";
    banner.style.background = "rgba(32, 53, 79, 0.92)";
    banner.style.color = "#fff";
    banner.style.display = "flex";
    banner.style.alignItems = "center";
    banner.style.justifyContent = "space-between";
    banner.style.gap = "12px";

    const text = document.createElement("div");
    text.textContent = message;

    const actionBox = document.createElement("div");
    actionBox.style.display = "flex";
    actionBox.style.gap = "8px";

    const restoreButton = document.createElement("button");
    restoreButton.type = "button";
    restoreButton.textContent = "Restore";
    restoreButton.addEventListener("click", async () => {
        removeSessionRestoreBanner();
        await onRestore();
    });

    const dismissButton = document.createElement("button");
    dismissButton.type = "button";
    dismissButton.className = "secondary";
    dismissButton.textContent = "Dismiss";
    dismissButton.addEventListener("click", async () => {
        removeSessionRestoreBanner();
        await onDismiss();
    });

    actionBox.appendChild(restoreButton);
    actionBox.appendChild(dismissButton);
    banner.appendChild(text);
    banner.appendChild(actionBox);

    const anchor = document.querySelector("main") || document.body;
    anchor.parentNode.insertBefore(banner, anchor);
}

function initSessionSnapshotPortal(options) {
    const current = getCurrentUser();
    if (!current?.sessionId) {
        return {
            checkForRestore: async () => {},
            persistSnapshot: async () => {}
        };
    }

    const portalKey = options?.portalKey || window.location.pathname;
    const getViewKey = typeof options?.getViewKey === "function"
        ? options.getViewKey
        : () => options?.defaultViewKey || "default";
    const getState = typeof options?.getState === "function"
        ? options.getState
        : () => ({});
    const restoreState = typeof options?.restoreState === "function"
        ? options.restoreState
        : async () => {};
    const bannerMessage = options?.bannerMessage || "Previous portal state is available.";

    const persistSnapshot = async (lastAction = "view-update", stateOverride) => {
        await saveSessionSnapshot({
            portalKey,
            lastViewKey: getViewKey(),
            lastAction,
            statePayload: stateOverride === undefined ? getState() : stateOverride
        }, { silent: true });
    };

    window.__portalSnapshotContext = {
        portalKey,
        getViewKey,
        getState,
        persistSnapshot
    };

    window.addEventListener("beforeunload", () => {
        void saveSessionSnapshot({
            portalKey,
            lastViewKey: getViewKey(),
            lastAction: "beforeunload",
            statePayload: getState()
        }, { silent: true, keepalive: true });
    });

    const checkForRestore = async () => {
        const snapshot = await getSessionSnapshot({ silent: true });
        if (!snapshot?.exists) {
            return;
        }
        if (snapshot.role !== current.role || snapshot.portalKey !== portalKey) {
            return;
        }

        const parsedPayload = parseSnapshotPayload(snapshot.statePayload);
        showSessionRestoreBanner(
            bannerMessage,
            async () => {
                try {
                    await restoreState(parsedPayload, snapshot);
                    await persistSnapshot("restore-applied", getState());
                    showToast("Last session restored successfully.", false);
                } catch (error) {
                    await clearSessionSnapshot(true);
                    redirectToRoleHomeWithMessage("Session restore failed. Returning to home screen.");
                }
            },
            async () => {
                await clearSessionSnapshot(true);
            }
        );
    };

    return { checkForRestore, persistSnapshot };
}

function getQueryParam(name) {
    const url = new URL(window.location.href);
    return url.searchParams.get(name);
}

installDevCrashTools();
function getTextPolicyViolations(value, label = "Value") {
    const violations = [];

    if (!value || value.trim() === "") {
        violations.push(`${label} cannot be empty`);
        return violations;
    }

    const rudeWords = [
        'fuck', 'shit', 'ass', 'bitch', 'damn', 'hell', 
        'cunt', 'dick', 'pussy', 'cock', 'whore', 'slut',
        // Add more as needed
    ];

    const lowerValue = value.toLowerCase();

    for (const rudeWord of rudeWords) {
        if (lowerValue.includes(rudeWord)) {
            violations.push(`${label} contains inappropriate word: "${rudeWord}"`);
            break;
        }
    }

    return violations;
}

function getusernamePolicyViolations(username){
    return getTextPolicyViolations(username, "Username");
}

function getFullNamePolicyViolations(fullName) {
    return getTextPolicyViolations(fullName, "Full Name");
}

function getPasswordPolicyViolations(password) {
    const issues = [];

    if (!password || password.trim().length === 0) {
        issues.push("Password cannot be empty.");
        return issues;
    }

    if (password.length < 8 || password.length > 64) {
        issues.push("Password must be between 8 and 64 characters.");
    }
    if (password.includes(" ")) {
        issues.push("Password cannot contain spaces.");
    }
    if (!/[A-Z]/.test(password)) {
        issues.push("Password must contain at least one uppercase letter.");
    }
    if (!/[a-z]/.test(password)) {
        issues.push("Password must contain at least one lowercase letter.");
    }
    if (!/\d/.test(password)) {
        issues.push("Password must contain at least one digit.");
    }
    if (!/[^A-Za-z0-9]/.test(password)) {
        issues.push("Password must contain at least one special character.");
    }

    return issues;
}
