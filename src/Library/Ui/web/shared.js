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
            localStorage.removeItem("currentUser");
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
            await api("/api/logout", { method: "POST" }, false);
        } catch (e) {
            // Ignore network/logout errors and continue clearing local session.
        }
        localStorage.removeItem("currentUser");
        window.location.href = "index.html";
    });
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
