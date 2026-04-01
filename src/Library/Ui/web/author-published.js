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

async function refreshPublishedBooks() {
    const status = document.getElementById("publishedStatus");
    const body = document.getElementById("publishedBooksBody");
    if (!status || !body) {
        return;
    }

    const items = await api("/api/author/published-books");
    body.innerHTML = "";

    if (!Array.isArray(items) || items.length === 0) {
        status.textContent = "No published books yet.";
        return;
    }

    status.textContent = `Found ${items.length} published book(s).`;

    items.forEach((item) => {
        const row = document.createElement("tr");
        const actionCell = document.createElement("td");
        row.innerHTML = `
            <td>${item.id}</td>
            <td>${item.title}</td>
            <td>${Array.isArray(item.genres) ? item.genres.join(", ") : ""}</td>
            <td>${item.description || item.summary || ""}</td>
            <td>${item.publishDate || ""}</td>
            <td>${item.status}</td>
        `;

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

                const newGenres = prompt(
                    "Update genres (comma separated):",
                    Array.isArray(item.genres) ? item.genres.join(", ") : ""
                );
                if (newGenres === null) {
                    return;
                }

                const newDescription = prompt("Update description:", item.description || item.summary || "");
                if (newDescription === null) {
                    return;
                }

                const text = await api("/api/author/published-book/update", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({
                        bookId: item.id,
                        title: newTitle.trim(),
                        genres: parseGenres(newGenres),
                        description: newDescription.trim()
                    })
                }, false);

                showToast(text, false);
                await refreshPublishedBooks();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        const deleteBtn = document.createElement("button");
        deleteBtn.className = "secondary";
        deleteBtn.type = "button";
        deleteBtn.textContent = "Delete";
        deleteBtn.addEventListener("click", async () => {
            const confirmed = confirm(`Delete published book \"${item.title}\"? This cannot be undone.`);
            if (!confirmed) {
                return;
            }

            try {
                const text = await api("/api/author/published-book/delete", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: formBody({ bookId: item.id })
                }, false);

                showToast(text, false);
                await refreshPublishedBooks();
            } catch (error) {
                showToast(error.message, true);
            }
        });

        const readBtn = document.createElement("button");
        readBtn.className = "secondary";
        readBtn.type = "button";
        readBtn.textContent = "Read";
        readBtn.addEventListener("click", async () => {
            try {
                const payload = await api(`/api/author/published-book/read?bookId=${encodeURIComponent(item.id)}`);
                renderServerFilePreview(payload, `Published: ${item.title || item.id}`);
                showToast("Published book preview loaded.", false);
            } catch (error) {
                showToast(error.message, true);
            }
        });

        actionCell.appendChild(editBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(deleteBtn);
        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(readBtn);
        row.appendChild(actionCell);
        body.appendChild(row);
    });
}

document.getElementById("loadPublishedBtn")?.addEventListener("click", () => {
    refreshPublishedBooks().catch((e) => {
        const status = document.getElementById("publishedStatus");
        if (status) {
            status.textContent = "Failed to load published books.";
        }
        showToast(e.message, true);
    });
});

if (currentUser) {
    refreshPublishedBooks().catch((e) => {
        const status = document.getElementById("publishedStatus");
        if (status) {
            status.textContent = "Failed to load published books.";
        }
        showToast(e.message, true);
    });
}
