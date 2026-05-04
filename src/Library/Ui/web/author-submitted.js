const currentUser = requireRole("AUTHOR");
let serverPreviewObjectUrl = null;
const selectedSubmissionIds = new Set();

if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    if (welcomeLine) {
        welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }
    attachLogout("logoutBtn");
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

    const visibleIds = new Set(pendingItems.map((item) => item.id));
    Array.from(selectedSubmissionIds).forEach((id) => {
        if (!visibleIds.has(id)) selectedSubmissionIds.delete(id);
    });

    pendingItems.forEach((item) => {
        const row = document.createElement("tr");
        const actionCell = document.createElement("td");
        row.innerHTML = `
            <td>${item.id}</td>
            <td>${item.title}</td>
            <td>${item.status}</td>
            <td>${formatDateOnly(item.submittedDate || "")}</td>
            <td>${item.fileName || ""}</td>
        `;

        const selectCell = document.createElement("td");
        const selectInput = document.createElement("input");
        selectInput.type = "checkbox";
        selectInput.dataset.submissionId = item.id;
        selectInput.checked = selectedSubmissionIds.has(item.id);
        selectInput.addEventListener("change", () => {
            if (selectInput.checked) selectedSubmissionIds.add(item.id);
            else selectedSubmissionIds.delete(item.id);
            updateBulkDeleteSubmittedButton();
        });
        selectCell.appendChild(selectInput);
        row.insertBefore(selectCell, row.firstChild);

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
    updateBulkDeleteSubmittedButton();
    const selectAll = document.getElementById("submittedSelectAll");
    if (selectAll) selectAll.checked = false;
}

function updateBulkDeleteSubmittedButton() {
    const btn = document.getElementById("bulkDeleteSubmittedBtn");
    if (!btn) return;
    btn.textContent = `Delete Selected (${selectedSubmissionIds.size})`;
    btn.disabled = selectedSubmissionIds.size === 0;
}

document.getElementById("submittedSelectAll")?.addEventListener("change", (e) => {
    const checked = e.target.checked;
    document
        .querySelectorAll("#submittedBooksBody input[type=checkbox][data-submission-id]")
        .forEach((cb) => {
            cb.checked = checked;
            const id = cb.dataset.submissionId;
            if (checked) selectedSubmissionIds.add(id);
            else selectedSubmissionIds.delete(id);
        });
    updateBulkDeleteSubmittedButton();
});

document.getElementById("bulkDeleteSubmittedBtn")?.addEventListener("click", async () => {
    if (selectedSubmissionIds.size === 0) return;
    const ids = Array.from(selectedSubmissionIds);
    const titles = ids.map((id) => {
        const cb = document.querySelector(`#submittedBooksBody input[data-submission-id="${id}"]`);
        // Row order: [select][ID][Title][...]; title is index 2 of row.children.
        const titleCell = cb?.parentElement?.parentElement?.children?.[2];
        return titleCell ? titleCell.textContent : id;
    });
    const summary = titles.length > 6
        ? titles.slice(0, 6).join(", ") + `, … (+${titles.length - 6} more)`
        : titles.join(", ");
    if (!confirm(`Delete ${ids.length} pending submission(s)?\n\n${summary}\n\nThis cannot be undone. Approved submissions and foreign rows will be skipped with a reason.`)) {
        return;
    }
    try {
        const result = await api("/api/author/submission/bulk-delete", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ submissionIds: ids.join(",") })
        });
        const deleted = Number(result?.deletedCount || 0);
        const skipped = Array.isArray(result?.skipped) ? result.skipped : [];
        let msg = `Deleted ${deleted} submission(s).`;
        if (skipped.length > 0) {
            msg += ` Skipped ${skipped.length}: ` + skipped.slice(0, 3).map((s) => `${s.id} (${s.reason})`).join("; ");
            if (skipped.length > 3) msg += `; …`;
        }
        showToast(msg, skipped.length > 0);
        selectedSubmissionIds.clear();
        await refreshSubmittedBooks();
    } catch (error) {
        showToast(error.message, true);
    }
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

if (currentUser) {
    refreshSubmittedBooks().catch((e) => {
        const status = document.getElementById("submittedStatus");
        if (status) {
            status.textContent = "Failed to load submitted books.";
        }
        showToast(e.message, true);
    });
}

window.addEventListener("beforeunload", () => {
    clearServerPreviewObjectUrl();
});
