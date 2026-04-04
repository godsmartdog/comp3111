const currentUser = requireRole("AUTHOR");
let serverPreviewObjectUrl = null;

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
}

function formatAverageRating(item) {
    const rating = Number(item?.averageRating);
    const count = Number(item?.reviewCount || 0);
    if (!Number.isFinite(rating) || count <= 0) {
        return "-";
    }
    return `${rating.toFixed(2)} (${count})`;
}

function clearServerPreviewObjectUrl() {
    if (serverPreviewObjectUrl) {
        URL.revokeObjectURL(serverPreviewObjectUrl);
        serverPreviewObjectUrl = null;
    }
}

function resetPreviewSurface(metaText) {
    const meta = document.getElementById("authorPreviewMeta");
    const pdf = document.getElementById("authorPreviewPdf");
    const text = document.getElementById("authorPreviewText");
    const imageWrap = document.getElementById("authorPreviewImageWrap");
    const image = document.getElementById("authorPreviewImage");

    clearServerPreviewObjectUrl();
    if (meta) {
        meta.textContent = metaText;
    }
    if (pdf) {
        pdf.style.display = "none";
        pdf.src = "";
    }
    if (text) {
        text.style.display = "none";
        text.textContent = "";
    }
    if (imageWrap) {
        imageWrap.style.display = "none";
    }
    if (image) {
        image.removeAttribute("src");
    }
}

async function renderServerFilePreview(payload, heading) {
    const meta = document.getElementById("authorPreviewMeta");
    if (!meta) {
        return;
    }

    const filePath = payload?.filePath || "";
    const sizeBytes = payload?.sizeBytes ?? "";
    const previewType = payload?.previewType || "text";
    const fileUrl = payload?.fileUrl || "";
    const previewText = payload?.previewText || "";
    const metaText = `${heading} | File: ${filePath} | Size: ${sizeBytes} bytes`;

    resetPreviewSurface(metaText);

    const text = document.getElementById("authorPreviewText");
    const pdf = document.getElementById("authorPreviewPdf");
    const imageWrap = document.getElementById("authorPreviewImageWrap");
    const image = document.getElementById("authorPreviewImage");
    const headers = {};
    if (currentUser?.sessionId) {
        headers["X-Session-Id"] = currentUser.sessionId;
    }

    if (previewType === "text") {
        if (text) {
            text.style.display = "block";
            text.style.whiteSpace = "pre-wrap";
            text.textContent = previewText || "No text content available.";
        }
        return;
    }

    if (!fileUrl) {
        if (text) {
            text.style.display = "block";
            text.style.whiteSpace = "pre-wrap";
            text.textContent = "No preview URL available for this file.";
        }
        return;
    }

    const response = await fetch(fileUrl, { headers });
    if (!response.ok) {
        throw new Error("Failed to load file preview.");
    }

    if (previewType === "pdf") {
        clearServerPreviewObjectUrl();
        const blob = await response.blob();
        serverPreviewObjectUrl = URL.createObjectURL(blob);
        if (pdf) {
            pdf.style.display = "block";
            pdf.src = serverPreviewObjectUrl;
        }
        return;
    }

    if (previewType === "docx") {
        if (typeof mammoth === "undefined") {
            if (text) {
                text.style.display = "block";
                text.style.whiteSpace = "pre-wrap";
                text.textContent = "DOCX preview dependency is missing.";
            }
            return;
        }

        const arrayBuffer = await response.arrayBuffer();
        const result = await mammoth.convertToHtml({ arrayBuffer });
        if (text) {
            text.style.display = "block";
            text.style.whiteSpace = "normal";
            text.innerHTML = result.value || "No readable DOCX content available.";
        }
        return;
    }

    if (previewType === "image") {
        clearServerPreviewObjectUrl();
        const blob = await response.blob();
        serverPreviewObjectUrl = URL.createObjectURL(blob);
        if (image && imageWrap) {
            imageWrap.style.display = "block";
            image.src = serverPreviewObjectUrl;
        }
        return;
    }

    if (text) {
        text.style.display = "block";
        text.style.whiteSpace = "pre-wrap";
        text.textContent = "This file type cannot be rendered inline. Use browser download/open to view it.";
    }
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
            <td>${formatAverageRating(item)}</td>
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
                await renderServerFilePreview(payload, `Published: ${item.title || item.id}`);
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

window.addEventListener("beforeunload", () => {
    clearServerPreviewObjectUrl();
});
