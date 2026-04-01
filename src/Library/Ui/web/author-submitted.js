const currentUser = requireRole("AUTHOR");

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function renderServerFilePreview(payload, heading) {
    const previewBox = document.getElementById("authorPreview");
    if (!previewBox) {
        return;
    }

    const filePath = payload?.filePath || "";
    const sizeBytes = payload?.sizeBytes ?? "";
    const text = payload?.previewText || "";
    previewBox.textContent =
        `=== ${heading} ===\n` +
        `File: ${filePath}\n` +
        `Size: ${sizeBytes} bytes\n` +
        "--- Text Preview ---\n" +
        `${text}\n` +
        "--- End Preview ---";
}

async function refreshSubmittedBooks() {
    const status = document.getElementById("submittedStatus");
    const body = document.getElementById("submittedBooksBody");
    if (!status || !body) {
        return;
    }

    const items = await api("/api/author/submissions");
    body.innerHTML = "";

    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No submitted books yet.";
        return;
    }

    status.textContent = `Found ${items.length} submitted book(s).`;

    items.forEach((item) => {
        const row = document.createElement("tr");
        const actionCell = document.createElement("td");
        row.innerHTML = `
            <td>${item.id}</td>
            <td>${item.title}</td>
            <td>${item.status}</td>
            <td>${item.submittedDate || ""}</td>
            <td>${item.fileName || ""}</td>
        `;

        const readBtn = document.createElement("button");
        readBtn.className = "secondary";
        readBtn.type = "button";
        readBtn.textContent = "Read";
        readBtn.addEventListener("click", async () => {
            try {
                const payload = await api(`/api/author/submission/read?submissionId=${encodeURIComponent(item.id)}`);
                renderServerFilePreview(payload, `Submission: ${item.title || item.id}`);
                showToast("Submission preview loaded.", false);
            } catch (error) {
                showToast(error.message, true);
            }
        });

        if (item.status === "PENDING") {
            const editBtn = document.createElement("button");
            editBtn.className = "secondary";
            editBtn.type = "button";
            editBtn.textContent = "Edit";
            editBtn.addEventListener("click", async () => {
                try {
                    const newTitle = prompt("Update title:", item.title || "");
                    if (newTitle === null) {
                        return;
                    }

                    const newGenres = prompt("Update genres (comma separated):", Array.isArray(item.genres) ? item.genres.join(", ") : "");
                    if (newGenres === null) {
                        return;
                    }

                    const newDescription = prompt("Update description:", item.description || "");
                    if (newDescription === null) {
                        return;
                    }

                    const newFilePath = prompt("Update file path (leave empty to keep current):", "");
                    if (newFilePath === null) {
                        return;
                    }

                    const text = await api("/api/author/submission/update", {
                        method: "POST",
                        headers: { "Content-Type": "application/x-www-form-urlencoded" },
                        body: formBody({
                            submissionId: item.id,
                            title: newTitle.trim(),
                            genres: parseGenres(newGenres),
                            description: newDescription.trim(),
                            filePath: newFilePath.trim()
                        })
                    }, false);

                    showToast(text, false);
                    await refreshSubmittedBooks();
                } catch (error) {
                    showToast(error.message, true);
                }
            });

            const deleteBtn = document.createElement("button");
            deleteBtn.className = "secondary";
            deleteBtn.type = "button";
            deleteBtn.textContent = "Delete";
            deleteBtn.addEventListener("click", async () => {
                const confirmed = confirm(`Delete pending submission \"${item.title}\"?`);
                if (!confirmed) {
                    return;
                }

                try {
                    const text = await api("/api/author/submission/delete", {
                        method: "POST",
                        headers: { "Content-Type": "application/x-www-form-urlencoded" },
                        body: formBody({ submissionId: item.id })
                    }, false);

                    showToast(text, false);
                    await refreshSubmittedBooks();
                } catch (error) {
                    showToast(error.message, true);
                }
            });

            actionCell.appendChild(editBtn);
            actionCell.appendChild(document.createTextNode(" "));
            actionCell.appendChild(deleteBtn);
        } else {
            actionCell.textContent = "Locked ";
        }

        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(readBtn);

        row.appendChild(actionCell);
        body.appendChild(row);
    });
}

document.getElementById("loadSubmittedBtn")?.addEventListener("click", () => {
    refreshSubmittedBooks().catch((e) => {
        const status = document.getElementById("submittedStatus");
        if (status) {
            status.textContent = "Failed to load submitted books.";
        }
        showToast(e.message, true);
    });
});

if (currentUser) {
    refreshSubmittedBooks().catch((e) => {
        const status = document.getElementById("submittedStatus");
        if (status) {
            status.textContent = "Failed to load submitted books.";
        }
        showToast(e.message, true);
    });
}
