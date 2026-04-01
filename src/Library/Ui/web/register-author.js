document.getElementById("registerBtn").addEventListener("click", async () => {
    try {
        const passwordError = document.getElementById("passwordError");
        const username = document.getElementById("username").value.trim();
        const fullName = document.getElementById("fullName").value.trim();
        const password = document.getElementById("password").value;
        const bio = document.getElementById("bio").value.trim();

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
            body: formBody({ username, fullName, password, role: "AUTHOR", bio })
        }, false);

        showToast(text, false);
        setTimeout(() => {
            window.location.href = "login.html?role=AUTHOR";
        }, 500);
    } catch (error) {
        showToast(error.message, true);
    }
});
