document.addEventListener("DOMContentLoaded", () => {
    const tabButtons = document.querySelectorAll(".article-tab-button");
    const cards = Array.from(document.querySelectorAll(".article-card"));
    const loadMoreButton = document.getElementById("loadMoreButton");
    const articlePage = document.querySelector(".article-page");
    const dimLayer = document.getElementById("articleDim");

    let currentCategory = "all";
    let expanded = false;
    const defaultVisibleCount = 6;

    function applyFilter() {
        const matched = cards.filter((card) => {
            const category = card.dataset.category;
            return currentCategory === "all" || category === currentCategory;
        });

        cards.forEach((card) => {
            card.classList.remove("is-visible");
            card.classList.add("is-filtered-out");
        });

        matched.forEach((card, index) => {
            card.classList.remove("is-filtered-out");
            const shouldShow = expanded || index < defaultVisibleCount;
            if (shouldShow) {
                card.classList.add("is-visible");
            } else {
                card.classList.remove("is-visible");
            }
        });

        if (loadMoreButton) {
            loadMoreButton.style.display = matched.length > defaultVisibleCount && !expanded ? "inline-flex" : "none";
        }
    }

    tabButtons.forEach((button) => {
        button.addEventListener("click", () => {
            tabButtons.forEach((btn) => btn.classList.remove("active"));
            button.classList.add("active");
            currentCategory = button.dataset.category;
            expanded = false;
            applyFilter();
        });
    });

    if (loadMoreButton) {
        loadMoreButton.addEventListener("click", () => {
            expanded = true;
            applyFilter();
        });
    }

    cards.forEach((card) => {
        card.addEventListener("mouseenter", () => {
            if (window.innerWidth > 768) {
                articlePage?.classList.add("is-hovering-card");
                dimLayer?.classList.add("active");
                card.classList.add("is-hovered");
            }
        });

        card.addEventListener("mouseleave", () => {
            articlePage?.classList.remove("is-hovering-card");
            dimLayer?.classList.remove("active");
            card.classList.remove("is-hovered");
        });

        card.addEventListener("touchstart", () => {
            cards.forEach((item) => item.classList.remove("is-hovered"));
            card.classList.add("is-hovered");
        }, { passive: true });
    });

    document.addEventListener("click", (event) => {
        if (!event.target.closest(".article-card")) {
            cards.forEach((item) => item.classList.remove("is-hovered"));
        }
    });

    applyFilter();
});
