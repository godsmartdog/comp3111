const roleSelect = document.getElementById("roleSelect");
const roleHint = document.getElementById("roleHint");
const registerLink = document.getElementById("registerLink");

function syncRoleUi() {
    const role = roleSelect.value;
    roleHint.textContent = `Sign in as ${role}.`;
    registerLink.href = role === "AUTHOR"
        ? "register-author.html?role=AUTHOR"
        : `register.html?role=${role}`;
}

const initialRole = (getQueryParam("role") || "STUDENT").toUpperCase();
if (["STUDENT", "STAFF", "AUTHOR", "LIBRARIAN"].includes(initialRole)) {
    roleSelect.value = initialRole;
}
syncRoleUi();

roleSelect.addEventListener("change", syncRoleUi);

document.getElementById("loginBtn").addEventListener("click", async () => {
    try {
        const role = roleSelect.value;
        const username = document.getElementById("username").value.trim();
        const password = document.getElementById("password").value;

        const user = await api("/api/login", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ username, password, role })
        });

        saveCurrentUser(user);
        showToast("Login successful.", false);
        setTimeout(() => {
            window.location.href = rolePage(user.role);
        }, 300);
    } catch (error) {
        showToast(error.message, true);
    }
});
