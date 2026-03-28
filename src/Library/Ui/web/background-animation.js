(() => {
    const canvas = document.getElementById("bgCanvas");
    if (!canvas) {
        return;
    }

    const ctx = canvas.getContext("2d");
    const particles = [];
    const words = ["E-LIBRARY SYSTEM", "BOOK", "READ", "AUTHOR", "KNOWLEDGE"];
    const bookImage = new Image();
    bookImage.src = "book-icon.svg";

    const resize = () => {
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
    };

    const seed = () => {
        particles.length = 0;
        for (let i = 0; i < 30; i += 1) {
            particles.push({
                x: Math.random() * canvas.width,
                y: Math.random() * canvas.height,
                vx: (Math.random() - 0.5) * 0.55,
                vy: (Math.random() - 0.5) * 0.55,
                size: 18 + Math.random() * 28,
                text: words[Math.floor(Math.random() * words.length)],
                alpha: 0.08 + Math.random() * 0.17,
                type: Math.random() > 0.45 ? "text" : "book"
            });
        }
    };

    const step = () => {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        particles.forEach((p) => {
            p.x += p.vx;
            p.y += p.vy;
            if (p.x < -140) p.x = canvas.width + 40;
            if (p.x > canvas.width + 140) p.x = -40;
            if (p.y < -80) p.y = canvas.height + 40;
            if (p.y > canvas.height + 80) p.y = -40;

            ctx.save();
            ctx.translate(p.x, p.y);
            ctx.rotate((-24 * Math.PI) / 180);
            ctx.globalAlpha = p.alpha;
            if (p.type === "book" && bookImage.complete) {
                const iconSize = p.size + 16;
                ctx.drawImage(bookImage, 0, 0, iconSize, iconSize);
            } else {
                ctx.fillStyle = "#ffffff";
                ctx.font = `700 ${p.size}px Arial`;
                ctx.fillText(p.text, 0, 0);
            }
            ctx.restore();
        });
        requestAnimationFrame(step);
    };

    window.addEventListener("resize", () => {
        resize();
        seed();
    });

    resize();
    seed();
    step();
})();
