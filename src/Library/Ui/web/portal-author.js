const currentUser = requireRole("AUTHOR");
let sessionSnapshotController = null;
if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let selectedFile = null;
let selectedCoverImageFile = null;
let previewObjectUrl = null;
let serverPreviewObjectUrl = null;

const filePathInput = document.getElementById("authorFilePath");
const fileInput = document.getElementById("authorFileInput");
const coverImagePathInput = document.getElementById("authorCoverImagePath");
const coverImageInput = document.getElementById("authorCoverImageInput");
const filePreview = document.getElementById("filePreview");

function clearFilePreviewUrl() {
    if (previewObjectUrl) {
        URL.revokeObjectURL(previewObjectUrl);
        previewObjectUrl = null;
    }
}

function clearServerPreviewUrl() {
    if (serverPreviewObjectUrl) {
        URL.revokeObjectURL(serverPreviewObjectUrl);
        serverPreviewObjectUrl = null;
    }
}


async function renderLocalFilePreview(file) {
    clearFilePreviewUrl();
    filePreview.innerHTML = "";

    const fileName = file.name.toLowerCase();
    const isPdf = fileName.endsWith(".pdf") || file.type === "application/pdf";
    const isImage = /\.(jpg|jpeg|png)$/i.test(fileName) || file.type.startsWith("image/");
    const isDocx = fileName.endsWith(".docx") || file.type.includes("wordprocessingml");

    if (isPdf) {
        previewObjectUrl = URL.createObjectURL(file);
        const iframe = document.createElement("iframe");
        iframe.src = previewObjectUrl;
        iframe.title = "PDF Preview";
        filePreview.appendChild(iframe);
        return;
    }

    if (isImage) {
        previewObjectUrl = URL.createObjectURL(file);
        const image = document.createElement("img");
        image.src = previewObjectUrl;
        image.alt = file.name;
        filePreview.appendChild(image);
        return;
    }

    if (isDocx) {
        if (typeof mammoth === "undefined") {
            filePreview.textContent = "DOCX preview dependency not loaded.";
            return;
        }

        const arrayBuffer = await file.arrayBuffer();
        const result = await mammoth.convertToHtml({ arrayBuffer });
        filePreview.innerHTML = result.value || "No visible preview content.";
        return;
    }

    filePreview.textContent = "Preview is supported for PDF, DOCX, JPG, JPEG, and PNG files.";
}

document.getElementById("pickFileBtn").addEventListener("click", () => {
    fileInput.click();
});

document.getElementById("pickCoverImageBtn")?.addEventListener("click", () => {
    coverImageInput?.click();
});

fileInput.addEventListener("change", async () => {
    const file = fileInput.files && fileInput.files[0] ? fileInput.files[0] : null;
    if (!file) {
        return;
    }

    selectedFile = file;
    filePathInput.value = file.name;

    try {
        await renderLocalFilePreview(file);
        showToast("File selected for preview.", false);
    } catch (error) {
        filePreview.textContent = "Failed to render local preview.";
        showToast(error.message || "Preview failed.", true);
    }
});

coverImageInput?.addEventListener("change", () => {
    const file = coverImageInput.files && coverImageInput.files[0] ? coverImageInput.files[0] : null;
    if (!file) {
        return;
    }

    selectedCoverImageFile = file;
    if (coverImagePathInput) {
        coverImagePathInput.value = file.name;
    }
    showToast("Cover image selected.", false);
});

async function refreshDrafts() {
    const list = document.getElementById("drafts");
    const items = await api("/api/author/drafts");
    list.innerHTML = "";

    items.forEach((draft) => {
        const li = document.createElement("li");
        const button = document.createElement("button");
        button.className = "secondary";
        button.textContent = `Load: ${draft.title}`;
        button.addEventListener("click", () => {
            document.getElementById("authorTitle").value = draft.title;
            document.getElementById("authorGenres").value = draft.genres.join(", ");
            document.getElementById("authorDescription").value = draft.description;
            document.getElementById("authorFilePath").value = draft.filePath;
            selectedFile = null;
            selectedCoverImageFile = null;
            fileInput.value = "";
            if (coverImageInput) {
                coverImageInput.value = "";
            }
            if (coverImagePathInput) {
                coverImagePathInput.value = "";
            }
            clearFilePreviewUrl();
            filePreview.textContent = "Choose a file to preview (PDF, DOCX, JPG/JPEG/PNG).";
            showToast("Draft loaded.", false);
        });
        li.appendChild(button);
        list.appendChild(li);
    });
}

async function refreshSubmittedBooks() {
    const status = document.getElementById("submittedStatus");
    const body = document.getElementById("submittedBooksBody");
    if (!status || !body) {
        return;
    }

    const items = await api("/api/author/submissions");
    const pendingItems = Array.isArray(items)
        ? items.filter((item) => String(item.status || "").toUpperCase() === "PENDING")
        : [];
    body.innerHTML = "";

    if (pendingItems.length === 0) {
        status.textContent = "No pending submitted books.";
        return;
    }

    status.textContent = `Found ${pendingItems.length} pending submitted book(s).`;

    pendingItems.forEach((item) => {
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
                await renderServerFilePreview(payload, `Submission: ${item.title || item.id}`);
                showToast("Submission preview loaded.", false);
            } catch (error) {
                showToast(error.message, true);
            }
        });

        const editBtn = document.createElement("button");
        editBtn.className = "secondary";
        editBtn.type = "button";
        editBtn.textContent = "Edit";
        editBtn.addEventListener("click", () => {
            window.location.href = `author-submission-edit.html?submissionId=${encodeURIComponent(item.id)}`;
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

        actionCell.appendChild(document.createTextNode(" "));
        actionCell.appendChild(readBtn);

        row.appendChild(actionCell);
        body.appendChild(row);
    });
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

function resetServerPreviewSurface(metaText) {
    const meta = document.getElementById("authorPreviewMeta");
    const pdf = document.getElementById("authorPreviewPdf");
    const text = document.getElementById("authorPreviewText");
    const imageWrap = document.getElementById("authorPreviewImageWrap");
    const image = document.getElementById("authorPreviewImage");

    clearServerPreviewUrl();
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

    resetServerPreviewSurface(metaText);

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
        clearServerPreviewUrl();
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
        clearServerPreviewUrl();
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

async function refreshAuthorNotifications() {
    const status = document.getElementById("authorNotificationStatus");
    const unreadLine = document.getElementById("authorNotificationUnread");
    const list = document.getElementById("authorNotificationsList");
    if (!status || !list || !unreadLine) {
        return;
    }

    try {
        unreadLine.textContent = "Unread: --";
        const items = await api("/api/author/notifications");
        let unreadCount = items.filter((item) => !item.read).length;

        try {
            const summary = await api("/api/author/notifications/summary");
            if (summary && typeof summary.unreadCount === "number") {
                unreadCount = summary.unreadCount;
            }
        } catch (_) {
            // Fallback to local count from list payload for backward compatibility.
        }

        list.innerHTML = "";
        unreadLine.textContent = `Unread: ${unreadCount}`;

        if (!Array.isArray(items) || items.length === 0) {
            status.textContent = "No notifications.";
            return;
        }

        status.textContent = `Total: ${items.length}, Unread: ${unreadCount}`;

        items.forEach((item) => {
            const li = document.createElement("li");
            const readLabel = item.read ? "Read" : "Unread";
            li.innerHTML = `
                <div>
                    <strong>[${readLabel}] ${item.title}</strong>
                    <div>${item.message || ""}</div>
                    <small>${item.createdAt || ""}</small>
                </div>
                <button class="secondary" type="button" ${item.read ? "disabled" : ""}>Mark As Read</button>
            `;

            const readBtn = li.querySelector("button");
            readBtn.addEventListener("click", async () => {
                try {
                    const payload = await api("/api/author/notifications/read", {
                        method: "POST",
                        headers: { "Content-Type": "application/x-www-form-urlencoded" },
                        body: formBody({ notificationId: item.id })
                    });
                    showToast(payload.message || "Notification marked as read.", false);
                    li.remove();
                    const remaining = list.querySelectorAll("li").length;
                    unreadLine.textContent = `Unread: ${remaining}`;
                    status.textContent = remaining === 0 ? "No notifications." : `Unread: ${remaining}`;
                } catch (error) {
                    showToast(error.message, true);
                }
            });

            list.appendChild(li);
        });
    } catch (error) {
        status.textContent = "Failed to load notifications.";
        unreadLine.textContent = "Unread: --";
        throw error;
    }
}

document.getElementById("autoSaveBtn").addEventListener("click", async () => {
    try {
        const text = await api("/api/author/draft", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                title: document.getElementById("authorTitle").value.trim(),
                genres: parseGenres(document.getElementById("authorGenres").value),
                description: document.getElementById("authorDescription").value.trim(),
                filePath: document.getElementById("authorFilePath").value.trim()
            })
        }, false);
        showToast(text, false);
        await refreshDrafts();
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("loadDraftsBtn").addEventListener("click", () => {
    refreshDrafts().catch((e) => showToast(e.message, true));
});

document.getElementById("loadSubmittedBtn")?.addEventListener("click", () => {
    refreshSubmittedBooks().catch((e) => {
        const status = document.getElementById("submittedStatus");
        if (status) {
            status.textContent = "Failed to load submitted books.";
        }
        showToast(e.message, true);
    });
});

document.getElementById("loadPublishedBtn")?.addEventListener("click", () => {
    refreshPublishedBooks().catch((e) => {
        const status = document.getElementById("publishedStatus");
        if (status) {
            status.textContent = "Failed to load published books.";
        }
        showToast(e.message, true);
    });
});

document.getElementById("loadAuthorNotificationsBtn")?.addEventListener("click", () => {
    refreshAuthorNotifications().catch((e) => showToast(e.message, true));
});

document.getElementById("previewBtn").addEventListener("click", async () => {
    try {
        const preview = await api("/api/author/preview", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({
                title: document.getElementById("authorTitle").value.trim(),
                genres: parseGenres(document.getElementById("authorGenres").value),
                description: document.getElementById("authorDescription").value.trim()
            })
        }, false);

        resetServerPreviewSurface("Draft text preview");
        const previewText = document.getElementById("authorPreviewText");
        if (previewText) {
            previewText.style.display = "block";
            previewText.style.whiteSpace = "pre-wrap";
            previewText.textContent = preview;
        }
    } catch (error) {
        showToast(error.message, true);
    }
});

document.getElementById("submitBtn").addEventListener("click", async () => {
    try {
        const title = document.getElementById("authorTitle").value.trim();
        const genres = parseGenres(document.getElementById("authorGenres").value);
        const description = document.getElementById("authorDescription").value.trim();
        const manualFilePath = document.getElementById("authorFilePath").value.trim();
        const manualCoverImagePath = document.getElementById("authorCoverImagePath")?.value.trim() || "";

        let text;
        if (selectedFile) {
            const payload = new FormData();
            payload.append("title", title);
            payload.append("genres", genres);
            payload.append("description", description);
            payload.append("file", selectedFile, selectedFile.name);
            if (selectedCoverImageFile) {
                payload.append("coverImage", selectedCoverImageFile, selectedCoverImageFile.name);
            }

            text = await api("/api/author/submit", {
                method: "POST",
                body: payload
            }, false);
        } else {
            text = await api("/api/author/submit", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: formBody({ title, genres, description, filePath: manualFilePath, coverImagePath: manualCoverImagePath })
            }, false);
        }

        showToast(text, false);
        document.getElementById("authorTitle").value = "";
        document.getElementById("authorGenres").value = "";
        document.getElementById("authorDescription").value = "";
        document.getElementById("authorFilePath").value = "";
        if (document.getElementById("authorCoverImagePath")) {
            document.getElementById("authorCoverImagePath").value = "";
        }
        resetServerPreviewSurface("");
        selectedFile = null;
        selectedCoverImageFile = null;
        fileInput.value = "";
        if (coverImageInput) {
            coverImageInput.value = "";
        }
        clearFilePreviewUrl();
        filePreview.textContent = "Choose a file to preview (PDF, DOCX, JPG/JPEG/PNG).";
        await refreshDrafts();
        await refreshSubmittedBooks();
        await refreshAuthorNotifications();
    } catch (error) {
        showToast(error.message, true);
    }
});

window.addEventListener("beforeunload", () => {
    clearFilePreviewUrl();
    clearServerPreviewUrl();
});

if (currentUser) {
    sessionSnapshotController = initSessionSnapshotPortal({
        portalKey: "author-portal",
        defaultViewKey: "author-dashboard",
        getViewKey: () => "author-dashboard",
        getState: () => ({ page: "author-dashboard" }),
        restoreState: async () => {
            showToast("Previous author portal state restored.", false);
        },
        bannerMessage: "A previous author portal state is available for this session."
    });

    refreshDrafts().catch((e) => showToast(e.message, true));
    refreshSubmittedBooks().catch((e) => {
        const status = document.getElementById("submittedStatus");
        if (status) {
            status.textContent = "Failed to load submitted books.";
        }
        showToast(e.message, true);
    });
    refreshPublishedBooks().catch((e) => {
        const status = document.getElementById("publishedStatus");
        if (status) {
            status.textContent = "Failed to load published books.";
        }
        showToast(e.message, true);
    });
    refreshAuthorNotifications().catch((e) => showToast(e.message, true));
    sessionSnapshotController.checkForRestore().catch((e) => showToast(e.message, true));
}
