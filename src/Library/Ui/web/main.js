document.querySelectorAll(".menu-button").forEach((container) => {
    const button = container.querySelector("button");
    button.addEventListener("click", () => {
        container.classList.toggle("open");
    });

    container.addEventListener("mouseleave", () => {
        container.classList.remove("open");
    });
});
