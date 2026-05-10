document.addEventListener("DOMContentLoaded", () => {
    const chips = Array.from(document.querySelectorAll(".article-category-chip"));
    const rows = Array.from(document.querySelectorAll(".article-row"));
    const pageNumbers = document.getElementById("pageNumbers");
    const prevButton = document.getElementById("pagePrev");
    const nextButton = document.getElementById("pageNext");
    const pageStatus = document.getElementById("articlePageStatus");

    const featureMain = document.getElementById("featureMain");
    const featureMainImage = document.getElementById("featureMainImage");
    const featureMainCategory = document.getElementById("featureMainCategory");
    const featureMainTitle = document.getElementById("featureMainTitle");
    const featureMainSummary = document.getElementById("featureMainSummary");

    const featureSub1 = document.getElementById("featureSub1");
    const featureSub1Image = document.getElementById("featureSub1Image");
    const featureSub1Category = document.getElementById("featureSub1Category");
    const featureSub1Title = document.getElementById("featureSub1Title");

    const featureSub2 = document.getElementById("featureSub2");
    const featureSub2Image = document.getElementById("featureSub2Image");
    const featureSub2Category = document.getElementById("featureSub2Category");
    const featureSub2Title = document.getElementById("featureSub2Title");

    const PAGE_SIZE = 5;
    const PAGE_BUTTONS_PER_GROUP = 5;
    const PLACEHOLDER_IMAGE = "/images/article-placeholder.svg";
    const initialCategory = new URLSearchParams(window.location.search).get("category") || "all";
    let currentCategory = initialCategory;
    let currentPage = 1;

    function filteredRows() {
        return rows.filter((row) => currentCategory === "all" || row.dataset.category === currentCategory);
    }

    function pageCount() {
        return Math.max(1, Math.ceil(filteredRows().length / PAGE_SIZE));
    }

    function currentGroupStart(totalPages) {
        if (totalPages <= 0) {
            return 1;
        }
        return Math.floor((currentPage - 1) / PAGE_BUTTONS_PER_GROUP) * PAGE_BUTTONS_PER_GROUP + 1;
    }

    function updateFeatureCard(anchor, imageEl, categoryEl, titleEl, article, fallbackTitle) {
        if (!anchor || !imageEl || !categoryEl || !titleEl) {
            return;
        }

        if (!article) {
            anchor.removeAttribute("href");
            anchor.setAttribute("aria-disabled", "true");
            imageEl.src = PLACEHOLDER_IMAGE;
            categoryEl.textContent = "기사 없음";
            titleEl.textContent = fallbackTitle;
            return;
        }

        anchor.href = article.href;
        anchor.removeAttribute("aria-disabled");
        imageEl.src = article.dataset.image || PLACEHOLDER_IMAGE;
        categoryEl.textContent = article.dataset.categoryLabel || "기사";
        titleEl.textContent = article.dataset.title || fallbackTitle;
    }

    function updateFeaturedArticles() {
        const matched = filteredRows();
        const first = matched[0];
        const second = matched[1];
        const third = matched[2];

        updateFeatureCard(featureMain, featureMainImage, featureMainCategory, featureMainTitle, first, "표시할 기사가 없습니다.");
        if (featureMainSummary) {
            featureMainSummary.textContent = first?.dataset.summary || "카테고리를 바꾸면 관련 기사가 이 영역에 표시됩니다.";
        }

        updateFeatureCard(featureSub1, featureSub1Image, featureSub1Category, featureSub1Title, second, "다른 기사를 기다리는 중입니다.");
        updateFeatureCard(featureSub2, featureSub2Image, featureSub2Category, featureSub2Title, third, "다른 기사를 기다리는 중입니다.");
    }

    function renderPagination() {
        const totalPages = pageCount();
        if (!pageNumbers) {
            return;
        }

        pageNumbers.innerHTML = "";

        const groupStart = currentGroupStart(totalPages);
        const groupEnd = Math.min(groupStart + PAGE_BUTTONS_PER_GROUP - 1, totalPages);

        for (let i = groupStart; i <= groupEnd; i += 1) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = `article-page-number${i === currentPage ? " is-active" : ""}`;
            button.textContent = String(i);
            button.addEventListener("click", () => {
                currentPage = i;
                renderRows();
            });
            pageNumbers.appendChild(button);
        }

        if (pageStatus) {
            pageStatus.textContent = `${currentPage} / ${totalPages}`;
        }

        if (prevButton) {
            prevButton.disabled = currentPage <= 1;
        }
        if (nextButton) {
            nextButton.disabled = currentPage >= totalPages;
        }
    }

    function renderRows() {
        const matched = filteredRows();
        const totalPages = pageCount();
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }

        const start = (currentPage - 1) * PAGE_SIZE;
        const end = start + PAGE_SIZE;

        rows.forEach((row) => row.classList.add("is-hidden"));
        matched.slice(start, end).forEach((row) => row.classList.remove("is-hidden"));

        renderPagination();
        updateFeaturedArticles();
    }

    chips.forEach((chip) => {
        if ((chip.dataset.category || "all") === currentCategory) {
            chips.forEach((item) => item.classList.remove("active"));
            chip.classList.add("active");
        }

        chip.addEventListener("click", () => {
            chips.forEach((item) => item.classList.remove("active"));
            chip.classList.add("active");
            currentCategory = chip.dataset.category || "all";
            currentPage = 1;
            renderRows();
        });
    });

    prevButton?.addEventListener("click", () => {
        if (currentPage > 1) {
            currentPage -= 1;
            renderRows();
        }
    });

    nextButton?.addEventListener("click", () => {
        if (currentPage < pageCount()) {
            currentPage += 1;
            renderRows();
        }
    });

    renderRows();
});
