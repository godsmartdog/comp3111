const roleSelect = document.getElementById("roleSelect");
const employeeWrap = document.getElementById("employeeWrap");
const loginLink = document.getElementById("loginLink");

function syncRegisterUi() {
    const role = roleSelect.value;
    employeeWrap.classList.toggle("hidden-block", role !== "LIBRARIAN");
    loginLink.href = `login.html?role=${role}`;
}

const initialRole = (getQueryParam("role") || "STUDENT").toUpperCase();
if (["STUDENT", "STAFF", "LIBRARIAN"].includes(initialRole)) {
    roleSelect.value = initialRole;
}
syncRegisterUi();

roleSelect.addEventListener("change", syncRegisterUi);

document.getElementById("registerBtn").addEventListener("click", async () => {
    try {
        const passwordError = document.getElementById("passwordError");
        const role = roleSelect.value;
        const username = document.getElementById("username").value.trim();
        const fullName = document.getElementById("fullName").value.trim();
        const password = document.getElementById("password").value;
        const employeeId = document.getElementById("employeeId").value.trim();
        const vio = getusernamePolicyViolations(username);
        if (vio.length > 0) {
            showToast("Username does not meet policy.", true);
            return;
        }
        const issues = getPasswordPolicyViolations(password);
        if (issues.length > 0) {
            passwordError.textContent = issues.join("\n");
            showToast("Password does not meet policy.", true);
            return;
        }
        passwordError.textContent = "";

        const text = await api("/api/register", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ username, fullName, password, role, employeeId })
        }, false);

        showToast(text, false);
        setTimeout(() => {
            window.location.href = `login.html?role=${role}`;
        }, 500);
    } catch (error) {
        showToast(error.message, true);
    }
});
