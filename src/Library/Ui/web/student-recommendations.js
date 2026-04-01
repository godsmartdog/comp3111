const expectedRole = document.querySelector("main")?.dataset.role || "STUDENT";
const currentUser = requireRole(expectedRole);

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

async function refreshRecommendations() {
    const list = document.getElementById("recommendations");
    if (!list) {
        return;
    }

    const items = await api("/api/recommendations?limit=10");
    list.innerHTML = "";

    if (!Array.isArray(items) || items.length === 0) {
        const li = document.createElement("li");
        li.className = "muted";
        li.textContent = "No recommendations available right now.";
        list.appendChild(li);
        return;
    }

    items.forEach((item, index) => {
        const li = document.createElement("li");
        li.textContent = `${index + 1}. ${item.title} (${item.author})`;
        list.appendChild(li);
    });
}

document.getElementById("refreshRecommendationsBtn")?.addEventListener("click", () => {
    refreshRecommendations().catch((e) => showToast(e.message, true));
});

if (currentUser) {
    refreshRecommendations().catch((e) => showToast(e.message, true));
}
