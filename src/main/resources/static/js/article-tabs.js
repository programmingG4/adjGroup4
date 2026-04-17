document.addEventListener("DOMContentLoaded", () => {
    const tabButtons = document.querySelectorAll(".article-tab-button");
    const cards = Array.from(document.querySelectorAll(".article-card"));
    const loadMoreButton = document.getElementById("loadMoreButton");
    const articlePage = document.querySelector(".article-page");
    const dimLayer = document.getElementById("articleDim");

    let currentCategory = "all";
    const allTabVisibleCount = 6;
    const categoryInitialVisibleCount = 9;
    const categoryLoadStep = 9;
    let currentVisibleCount = allTabVisibleCount;

    function matchedCards() {
        return cards.filter((card) => {
            const category = card.dataset.category;
            return currentCategory === "all" || category === currentCategory;
        });
    }

    function resetVisibleCount() {
        currentVisibleCount = currentCategory === "all"
            ? allTabVisibleCount
            : categoryInitialVisibleCount;
    }

    function applyFilter() {
        const filtered = matchedCards();

        cards.forEach((card) => {
            card.classList.remove("is-visible", "is-filtered-out");
            card.classList.add("is-filtered-out");
        });

        filtered.forEach((card, index) => {
            card.classList.remove("is-filtered-out");
            if (index < currentVisibleCount) {
                card.classList.add("is-visible");
            }
        });

        if (loadMoreButton) {
            const shouldShowMore = currentCategory !== "all" && filtered.length > currentVisibleCount;
            loadMoreButton.style.display = shouldShowMore ? "inline-flex" : "none";
            loadMoreButton.disabled = !shouldShowMore;
        }
    }

    tabButtons.forEach((button) => {
        button.addEventListener("click", () => {
            tabButtons.forEach((btn) => btn.classList.remove("active"));
            button.classList.add("active");
            currentCategory = button.dataset.category;
            resetVisibleCount();
            applyFilter();
        });
    });

    if (loadMoreButton) {
        loadMoreButton.addEventListener("click", () => {
            if (currentCategory === "all") {
                return;
            }
            const filtered = matchedCards();
            currentVisibleCount = Math.min(currentVisibleCount + categoryLoadStep, filtered.length);
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

    resetVisibleCount();
    applyFilter();
});
