const currentUser = requireRole("AUTHOR");
if (currentUser) {
    document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let selectedFile = null;
let previewObjectUrl = null;

const filePathInput = document.getElementById("authorFilePath");
const fileInput = document.getElementById("authorFileInput");
const filePreview = document.getElementById("filePreview");
const authorPasswordInput = document.getElementById("authorProfilePassword");
const authorPasswordStrengthLabel = document.getElementById("authorPasswordStrengthLabel");
const authorPasswordStrengthHint = document.getElementById("authorPasswordStrengthHint");
const authorPasswordChecklist = document.getElementById("authorPasswordChecklist");

const authorPasswordRuleLength = document.getElementById("authorPasswordRuleLength");
const authorPasswordRuleUpper = document.getElementById("authorPasswordRuleUpper");
const authorPasswordRuleLower = document.getElementById("authorPasswordRuleLower");
const authorPasswordRuleDigit = document.getElementById("authorPasswordRuleDigit");
const authorPasswordRuleSpecial = document.getElementById("authorPasswordRuleSpecial");

function updatePasswordRuleLine(element, passed, label) {
    if (!element) {
        return;
    }
    element.textContent = `${passed ? "[OK]" : "[ ]"} ${label}`;
}

function refreshAuthorPasswordStrength(passwordRaw) {
    if (!authorPasswordStrengthLabel || !authorPasswordStrengthHint || !authorPasswordChecklist) {
        return;
    }

    const password = passwordRaw || "";
    if (!password) {
        authorPasswordStrengthLabel.textContent = "Strength: --";
        authorPasswordStrengthHint.textContent = "Enter a new password to see strength guidance.";
        authorPasswordChecklist.classList.add("hidden");
        return;
    }

    const checks = {
        length: password.length >= 8 && password.length <= 64,
        upper: /[A-Z]/.test(password),
        lower: /[a-z]/.test(password),
        digit: /\d/.test(password),
        special: /[^A-Za-z0-9]/.test(password)
    };

    const passedCount = Object.values(checks).filter(Boolean).length;
    let level = "Weak";
    if (passedCount >= 5) {
        level = "Strong";
    } else if (passedCount >= 3) {
        level = "Medium";
    }

    authorPasswordStrengthLabel.textContent = `Strength: ${level}`;
    authorPasswordStrengthHint.textContent = "Password rules are enforced by server-side validation on save.";
    authorPasswordChecklist.classList.remove("hidden");

    updatePasswordRuleLine(authorPasswordRuleLength, checks.length, "8-64 characters");
    updatePasswordRuleLine(authorPasswordRuleUpper, checks.upper, "At least one uppercase letter");
    updatePasswordRuleLine(authorPasswordRuleLower, checks.lower, "At least one lowercase letter");
    updatePasswordRuleLine(authorPasswordRuleDigit, checks.digit, "At least one digit");
    updatePasswordRuleLine(authorPasswordRuleSpecial, checks.special, "At least one special character");
}

function clearFilePreviewUrl() {
    if (previewObjectUrl) {
        URL.revokeObjectURL(previewObjectUrl);
        previewObjectUrl = null;
    }
}

async function loadAuthorProfile() {
    const payload = await api("/api/author/profile");
    document.getElementById("authorProfileFullName").value = payload.fullName || "";
    document.getElementById("authorProfileBio").value = payload.bio || "";
}

async function saveAuthorProfile() {
    const feedback = document.getElementById("authorProfileFeedback");
    const fullName = document.getElementById("authorProfileFullName").value.trim();
    const bio = document.getElementById("authorProfileBio").value.trim();
    const password = document.getElementById("authorProfilePassword").value;

    if (!fullName) {
        feedback.textContent = "Full Name cannot be empty.";
        showToast(feedback.textContent, true);
        return;
    }
    if (!bio) {
        feedback.textContent = "Bio cannot be empty.";
        showToast(feedback.textContent, true);
        return;
    }

    if (password.trim()) {
        const issues = getPasswordPolicyViolations(password);
        if (issues.length > 0) {
            feedback.textContent = issues[0];
            showToast(feedback.textContent, true);
            return;
        }
    }

    const text = await api("/api/author/profile", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody({ fullName, bio, password })
    }, false);

    feedback.textContent = text;
    document.getElementById("authorProfilePassword").value = "";

    if (currentUser) {
        currentUser.fullName = fullName;
        saveCurrentUser(currentUser);
        document.getElementById("welcomeLine").textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    }

    showToast(text, false);
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

authorPasswordInput?.addEventListener("input", () => {
    refreshAuthorPasswordStrength(authorPasswordInput.value);
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
            fileInput.value = "";
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
        `--- Text Preview ---\n` +
        `${text}\n` +
        `--- End Preview ---`;
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
                    await refreshAuthorNotifications();
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

document.getElementById("saveAuthorProfileBtn")?.addEventListener("click", () => {
    saveAuthorProfile().catch((e) => {
        const feedback = document.getElementById("authorProfileFeedback");
        if (feedback) {
            feedback.textContent = e.message;
        }
        showToast(e.message, true);
    });
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

        document.getElementById("authorPreview").textContent = preview;
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

        let text;
        if (selectedFile) {
            const payload = new FormData();
            payload.append("title", title);
            payload.append("genres", genres);
            payload.append("description", description);
            payload.append("file", selectedFile, selectedFile.name);

            text = await api("/api/author/submit", {
                method: "POST",
                body: payload
            }, false);
        } else {
            text = await api("/api/author/submit", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: formBody({ title, genres, description, filePath: manualFilePath })
            }, false);
        }

        showToast(text, false);
        await refreshSubmittedBooks();
    } catch (error) {
        showToast(error.message, true);
    }
});

if (currentUser) {
    refreshAuthorPasswordStrength(authorPasswordInput?.value || "");
    loadAuthorProfile().catch((e) => {
        const feedback = document.getElementById("authorProfileFeedback");
        if (feedback) {
            feedback.textContent = "Failed to load author profile.";
        }
        showToast(e.message, true);
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
}
