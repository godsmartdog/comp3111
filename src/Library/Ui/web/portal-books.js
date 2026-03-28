const expectedRole = document.querySelector("main").dataset.role;
const currentUser = requireRole(expectedRole);
if (currentUser) {
    const welcomeLine = document.getElementById("welcomeLine");
    welcomeLine.textContent = `Welcome, ${currentUser.fullName} (${currentUser.role})`;
    attachLogout("logoutBtn");
}

let selectedBookId = null;
let allBooks = [];
let currentPage = 1;
const pageSize = 5;

function getTotalPages() {
    return Math.max(1, Math.ceil(allBooks.length / pageSize));
}

function updatePagerUi() {
    const totalPages = getTotalPages();
    const pageInfo = document.getElementById("pageInfo");
    const prevBtn = document.getElementById("prevPageBtn");
    const nextBtn = document.getElementById("nextPageBtn");

    pageInfo.textContent = `${currentPage}/${totalPages}`;
    prevBtn.disabled = currentPage <= 1;
    nextBtn.disabled = currentPage >= totalPages;
}

function renderCurrentPageBooks() {
    const start = (currentPage - 1) * pageSize;
    const end = start + pageSize;
    renderBooks(allBooks.slice(start, end));
    updatePagerUi();
}

function renderBooks(books) {
    const tbody = document.getElementById("booksBody");
    tbody.innerHTML = "";

    books.forEach((book) => {
        const row = document.createElement("tr");
        const statusClass = book.available ? "status-available" : "status-unavailable";

        row.innerHTML = `
            <td>${book.title}</td>
            <td>${book.author}</td>
            <td class="${statusClass}">${book.available ? "Available" : "Unavailable"}</td>
            <td><button class="secondary">Select</button></td>
        `;

        row.querySelector("button").addEventListener("click", () => {
            selectedBookId = book.id;
            document.getElementById("selectedBook").textContent = `Selected book: ${book.title}`;
        });

        tbody.appendChild(row);
    });
}

async function refreshBooks(keyword = "") {
    const url = keyword ? `/api/books?keyword=${encodeURIComponent(keyword)}` : "/api/books";
    const books = await api(url);
    allBooks = [...books].sort((a, b) => a.title.localeCompare(b.title, undefined, { sensitivity: "base" }));
    currentPage = 1;
    renderCurrentPageBooks();
}

async function refreshRecommendations() {
    const list = document.getElementById("recommendations");
    const items = await api("/api/recommendations?limit=5");
    list.innerHTML = "";
    items.forEach((item) => {
        const li = document.createElement("li");
        li.textContent = `${item.title} (${item.author})`;
        list.appendChild(li);
    });
}

async function refreshBorrows() {
    const list = document.getElementById("borrows");
    const items = await api("/api/borrows");
    list.innerHTML = "";
    items.forEach((item) => {
        const li = document.createElement("li");
        li.textContent = `${item.bookTitle} (due ${item.dueDate})`;
        list.appendChild(li);
    });
}

document.getElementById("searchBtn").addEventListener("click", () => {
    refreshBooks(document.getElementById("searchKeyword").value.trim()).catch((e) => showToast(e.message, true));
});

document.getElementById("showAllBtn").addEventListener("click", () => {
    refreshBooks().catch((e) => showToast(e.message, true));
});

document.getElementById("prevPageBtn").addEventListener("click", () => {
    if (currentPage > 1) {
        currentPage -= 1;
        renderCurrentPageBooks();
    }
});

document.getElementById("nextPageBtn").addEventListener("click", () => {
    if (currentPage < getTotalPages()) {
        currentPage += 1;
        renderCurrentPageBooks();
    }
});

document.getElementById("borrowBtn").addEventListener("click", async () => {
    try {
        if (!selectedBookId) {
            showToast("Please select a book first.", true);
            return;
        }

        const days = Number(document.getElementById("borrowDays").value);
        const text = await api("/api/borrow", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formBody({ bookId: selectedBookId, days })
        }, false);

        showToast(text, false);
        await refreshBooks();
        await refreshRecommendations();
        await refreshBorrows();
    } catch (error) {
        showToast(error.message, true);
    }
});

if (currentUser) {
    refreshBooks().catch((e) => showToast(e.message, true));
    refreshRecommendations().catch((e) => showToast(e.message, true));
    refreshBorrows().catch((e) => showToast(e.message, true));
}
