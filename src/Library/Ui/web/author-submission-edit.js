const currentUser = requireRole("AUTHOR");
let selectedSubmission = null;

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function selectedSubmissionId() {
    return (getQueryParam("submissionId") || "").trim();
}

function writeForm(item) {
    document.getElementById("submissionIdView").textContent = item.id || "";
    document.getElementById("submissionTitle").value = item.title || "";
    document.getElementById("submissionGenres").value = Array.isArray(item.genres) ? item.genres.join(", ") : "";
    document.getElementById("submissionDescription").value = item.description || "";
    document.getElementById("submissionFilePath").value = "";
}

async function loadPendingSubmission() {
    const status = document.getElementById("submissionEditStatus");
    const id = selectedSubmissionId();
    if (!id) {
        throw new Error("Missing submissionId in URL.");
    }

    const items = await api("/api/author/submissions");
    const found = Array.isArray(items)
        ? items.find((item) => item.id === id)
        : null;

    if (!found) {
        throw new Error("Submission not found.");
    }
    if (String(found.status || "").toUpperCase() !== "PENDING") {
        throw new Error("Only pending submissions can be edited.");
    }

    selectedSubmission = found;
    writeForm(found);
    if (status) {
        status.textContent = `Editing pending submission: ${found.title}`;
    }
}

async function saveSubmission() {
    if (!selectedSubmission) {
        throw new Error("Submission is not loaded yet.");
    }

    const title = document.getElementById("submissionTitle").value.trim();
    const genres = parseGenres(document.getElementById("submissionGenres").value || "");
    const description = document.getElementById("submissionDescription").value.trim();
    const filePath = document.getElementById("submissionFilePath").value.trim();

    if (!title) {
        throw new Error("Title cannot be empty.");
    }
    if (!genres) {
        throw new Error("At least one genre is required.");
    }
    if (!description) {
        throw new Error("Description cannot be empty.");
    }

    const text = await api("/api/author/submission/update", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({
            submissionId: selectedSubmission.id,
            title,
            genres,
            description,
            filePath
        })
    }, false);

    document.getElementById("submissionEditFeedback").textContent = text;
    showToast(text, false);
    await loadPendingSubmission();
}

document.getElementById("saveSubmissionBtn")?.addEventListener("click", () => {
    saveSubmission().catch((error) => {
        document.getElementById("submissionEditFeedback").textContent = error.message;
        showToast(error.message, true);
    });
});

if (currentUser) {
    loadPendingSubmission().catch((error) => {
        document.getElementById("submissionEditStatus").textContent = error.message;
        showToast(error.message, true);
    });
}
