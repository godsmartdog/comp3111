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
        if (response.status === 401) {
            const role = current?.role || "";
            const redirect = role
                ? `login.html?role=${encodeURIComponent(role)}&sessionMessage=${encodeURIComponent(text || "Session expired. Please login again.")}`
                : `login.html?sessionMessage=${encodeURIComponent(text || "Session expired. Please login again.")}`;
            localStorage.removeItem("currentUser");
            window.location.href = redirect;
        }
        throw new Error(text || "Request failed.");
    }
    return expectJson ? response.json() : response.text();
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
    if (role === "STUDENT") return "student.html";
    if (role === "STAFF") return "staff.html";
    if (role === "AUTHOR") return "author.html";
    if (role === "LIBRARIAN") return "librarian.html";
    return "index.html";
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
                await restoreState(parsedPayload, snapshot);
                await persistSnapshot("restore-applied", getState());
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
