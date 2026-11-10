document.addEventListener("DOMContentLoaded", () => {
    const PAGE_SIZE = 6;

    const searchForm = document.getElementById("articleSearchForm");
    const searchInput = document.getElementById("articleSearchInput");
    const searchType = document.getElementById("articleSearchType");
    const categorySelect = document.getElementById("articleCategorySelect");
    const rowsContainer = document.getElementById("articleListRows");
    const emptyState = document.getElementById("articleEmptyState");
    const resultSummary = document.getElementById("articleResultSummary");
    const pageStatus = document.getElementById("articlePageStatus");
    const prevButton = document.getElementById("pagePrev");
    const nextButton = document.getElementById("pageNext");
    const pageNumbers = document.getElementById("pageNumbers");
    const inlineLoading = document.getElementById("articleInlineLoading");
    const inlineLoadingBar = document.getElementById("articleInlineLoadingBar");
    const inlineLoadingPercent = document.getElementById("articleInlineLoadingPercent");
    const inlineProgress = document.getElementById("articleInlineProgress");

    if (!rowsContainer) {
        return;
    }

    let currentPage = Number(rowsContainer.dataset.initialPage || "1");
    let currentKeyword = "";
    let currentSearchType = "title-summary";
    let currentCategory = "all";
    let loading = false;
    let inlineProgressValue = 0;
    let inlineProgressTimer = null;

    const pageCache = new Map();
    const hasMoreCache = new Map();

    function cacheKey(page = currentPage, category = currentCategory) {
        return `${category || "all"}:${page}`;
    }

    function currentPageArticles() {
        return pageCache.get(cacheKey()) || [];
    }

    function hasMoreForCurrentPage() {
        return Boolean(hasMoreCache.get(cacheKey()));
    }

    function readStoredInitialPage() {
        try {
            const raw = sessionStorage.getItem("campusonArticleInitialPage");
            return raw ? JSON.parse(raw) : null;
        } catch (error) {
            sessionStorage.removeItem("campusonArticleInitialPage");
            return null;
        }
    }

    function textOf(value) {
        return (value || "").toString().trim();
    }

    function normalize(value) {
        return textOf(value).toLowerCase();
    }


    function cleanCrowllyTitle(article) {
        const rawTitle = textOf(article && article.title);
        const source = normalize(article && article.sourceName);
        const url = normalize(article && article.articleUrl);
        if (!rawTitle || (!source.includes("crowlly") && !url.includes("crowlly.xyz"))) {
            return rawTitle;
        }

        const separator = String.raw`[|｜:：\-–—−·•/\\]`;
        let title = rawTitle.replace(/[\u200B-\u200D\uFEFF]/g, " ").trim();
        for (let i = 0; i < 6; i += 1) {
            title = title
                .replace(new RegExp(String.raw`^\s*(?:공모전\s*${separator}+\s*)?(?:crowlly|크롤리|crowlly\.xyz)\s*${separator}+\s*`, "iu"), "")
                .replace(new RegExp(String.raw`^\s*(?:공모전\s+)?(?:crowlly|크롤리|crowlly\.xyz)\s+`, "iu"), "")
                .replace(new RegExp(String.raw`\s*${separator}+\s*(?:공모전\s*${separator}+\s*)?(?:crowlly|크롤리|crowlly\.xyz)\s*$`, "iu"), "")
                .replace(/\s+(?:crowlly|크롤리|crowlly\.xyz)\s*$/iu, "")
                .trim();
        }
        return title || rawTitle;
    }

    function normalizeTags(value) {
        if (Array.isArray(value)) {
            return value.map(textOf).filter(Boolean).join(",");
        }
        return textOf(value)
            .replace(/^\[/, "")
            .replace(/\]$/, "")
            .split(",")
            .map(tag => tag.trim())
            .filter(Boolean)
            .join(",");
    }

    function displayTagLabel(tag) {
        const normalized = textOf(tag);
        return normalized.startsWith("#") ? normalized : `#${normalized}`;
    }

    function rowToArticle(row) {
        return {
            categoryKey: row.dataset.category || "",
            categoryLabel: row.dataset.categoryLabel || "",
            sourceName: row.dataset.source || "",
            title: row.dataset.title || "",
            articleUrl: row.getAttribute("href") || "#",
            thumbnailUrl: row.dataset.rawImage || row.dataset.image || "",
            summary: row.dataset.summary || "",
            publishedAt: row.dataset.published || "",
            deadlineDday: row.dataset.deadlineDday || "",
            tags: normalizeTags(row.dataset.tags || "")
        };
    }

    const initialArticles = Array.from(rowsContainer.querySelectorAll(".article-row")).map(rowToArticle);
    const storedInitialPage = readStoredInitialPage();

    if (storedInitialPage && Number(storedInitialPage.page || 1) === 1) {
        currentPage = 1;
        pageCache.set(cacheKey(1, "all"), Array.isArray(storedInitialPage.articles) ? storedInitialPage.articles : []);
        hasMoreCache.set(cacheKey(1, "all"), Boolean(storedInitialPage.hasMore));
        sessionStorage.removeItem("campusonArticleInitialPage");
    } else {
        pageCache.set(cacheKey(currentPage, "all"), initialArticles);
        hasMoreCache.set(cacheKey(currentPage, "all"), rowsContainer.dataset.hasMore === "true");
    }

    function escapeHtml(value) {
        return textOf(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function proxiedImageUrl(rawUrl) {
        if (!rawUrl) {
            return "/articles/image";
        }
        if (rawUrl.startsWith("/articles/image")) {
            return rawUrl;
        }
        return `/articles/image?url=${encodeURIComponent(rawUrl)}`;
    }

    function createArticleRow(article) {
        const displayTitle = cleanCrowllyTitle(article);
        const link = document.createElement("a");
        link.className = "article-row";
        link.href = article.articleUrl || "#";
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        link.dataset.category = article.categoryKey || "";
        link.dataset.categoryLabel = article.categoryLabel || "";
        link.dataset.source = article.sourceName || "";
        link.dataset.title = displayTitle || "";
        link.dataset.summary = article.summary || "";
        link.dataset.tags = normalizeTags(article.tags || "");
        link.dataset.rawImage = article.thumbnailUrl || "";
        link.dataset.image = proxiedImageUrl(article.thumbnailUrl || "");
        link.dataset.published = article.publishedAt || "";
        link.dataset.deadlineDday = article.deadlineDday || "";

        const deadlineDday = textOf(article.deadlineDday || "");
        const deadlineHtml = deadlineDday
            ? `<span class="article-row-dday">${escapeHtml(deadlineDday)}</span>`
            : "";

        const tagHtml = normalizeTags(article.tags)
            .split(",")
            .map(tag => tag.trim())
            .filter(Boolean)
            .map(tag => `<span>${escapeHtml(displayTagLabel(tag))}</span>`)
            .join("");

        link.innerHTML = `
            <div class="article-row-thumb-wrap">
                <img class="article-row-thumb" src="${escapeHtml(proxiedImageUrl(article.thumbnailUrl || ""))}" alt="${escapeHtml(displayTitle || "기사 썸네일")}">
            </div>
            <div class="article-row-content">
                <div class="article-row-meta">
                    <span class="article-row-badge">${escapeHtml(article.categoryLabel || "카테고리")}</span>
                    <span class="article-row-source">${escapeHtml(article.sourceName || "출처")}</span>
                    <span class="article-row-date">${escapeHtml(article.publishedAt || "")}</span>
                    ${deadlineHtml}
                </div>
                <h3 class="article-row-title">${escapeHtml(displayTitle || "제목 없음")}</h3>
                <p class="article-row-summary">${escapeHtml(article.summary || "본문 요약은 원본 게시글에서 확인할 수 있습니다.")}</p>
                <div class="article-row-tags">${tagHtml}</div>
            </div>
            <span class="article-row-arrow">→</span>
        `;
        return link;
    }

    function matchesSearch(article) {
        const keyword = normalize(currentKeyword);
        if (!keyword) {
            return true;
        }

        const title = normalize(article.title);
        const summary = normalize(article.summary);
        const tags = normalize(normalizeTags(article.tags));
        const source = normalize(article.sourceName);

        if (currentSearchType === "title") {
            return title.includes(keyword);
        }
        if (currentSearchType === "summary") {
            return summary.includes(keyword);
        }
        return `${title} ${summary} ${tags} ${source}`.includes(keyword);
    }

    function matchesCategory(article) {
        return currentCategory === "all" || article.categoryKey === currentCategory;
    }

    function filteredArticles() {
        const articles = currentPageArticles();
        return articles
            .filter(article => matchesCategory(article) && matchesSearch(article))
            .slice(0, PAGE_SIZE);
    }

    function setInlineProgress(value) {
        inlineProgressValue = Math.max(0, Math.min(100, Math.round(value)));
        if (inlineLoadingBar) {
            inlineLoadingBar.style.width = `${inlineProgressValue}%`;
        }
        if (inlineLoadingPercent) {
            inlineLoadingPercent.textContent = `${inlineProgressValue}%`;
        }
        if (inlineProgress) {
            inlineProgress.setAttribute("aria-valuenow", String(inlineProgressValue));
        }
    }

    function startInlineLoading() {
        inlineLoading?.classList.remove("is-hidden");
        setInlineProgress(0);
        window.clearInterval(inlineProgressTimer);
        inlineProgressTimer = window.setInterval(() => {
            const next = inlineProgressValue < 55 ? inlineProgressValue + 5 : inlineProgressValue < 82 ? inlineProgressValue + 2 : inlineProgressValue + 1;
            setInlineProgress(Math.min(next, 92));
        }, 140);
    }

    function stopInlineLoading() {
        window.clearInterval(inlineProgressTimer);
        inlineProgressTimer = null;
        setInlineProgress(100);
        window.setTimeout(() => {
            inlineLoading?.classList.add("is-hidden");
            setInlineProgress(0);
        }, 220);
    }

    function setLoading(nextLoading) {
        loading = nextLoading;
        rowsContainer.classList.toggle("is-loading", loading);
        if (loading) {
            startInlineLoading();
        } else {
            stopInlineLoading();
        }
        if (nextButton) {
            nextButton.disabled = loading || !hasMoreForCurrentPage();
            nextButton.textContent = loading ? "불러오는 중..." : "다음";
        }
        if (prevButton) {
            prevButton.disabled = loading || currentPage <= 1;
        }
        if (resultSummary && loading) {
            resultSummary.textContent = `${currentPage + 1}페이지의 더 이전 게시글을 불러오는 중입니다.`;
        }
    }

    function updateSummary(count) {
        if (!resultSummary) {
            return;
        }
        const total = Math.min(currentPageArticles().length, PAGE_SIZE);
        const suffix = currentKeyword || currentCategory !== "all" ? ` / 조건 일치 ${count}개` : "";
        resultSummary.textContent = `${currentPage}페이지에서 최신순 게시글 ${total}개를 표시합니다${suffix}.`;
    }

    function renderPageMarker() {
        if (pageNumbers) {
            pageNumbers.innerHTML = `<span class="article-page-current">${currentPage}</span>`;
        }
        if (pageStatus) {
            pageStatus.textContent = `${currentPage}페이지`;
        }
        if (prevButton) {
            prevButton.disabled = loading || currentPage <= 1;
        }
        if (nextButton) {
            nextButton.disabled = loading || !hasMoreForCurrentPage();
        }
    }

    function renderRows() {
        const matched = filteredArticles();
        rowsContainer.innerHTML = "";
        matched.forEach(article => rowsContainer.appendChild(createArticleRow(article)));

        updateSummary(matched.length);
        if (emptyState) {
            emptyState.classList.toggle("is-visible", matched.length === 0);
        }
        renderPageMarker();
    }

    async function fetchInitialPage(force = false) {
        const key = cacheKey(1, "all");
        if (!force && pageCache.has(key) && (pageCache.get(key) || []).length > 0) {
            return;
        }

        setLoading(true);
        try {
            const response = await fetch(`/articles/initial`, {
                headers: {"Accept": "application/json"}
            });
            if (!response.ok) {
                throw new Error("initial fetch failed");
            }
            const result = await response.json();
            pageCache.set(key, Array.isArray(result.articles) ? result.articles : []);
            hasMoreCache.set(key, Boolean(result.hasMore));
        } catch (error) {
            pageCache.set(key, []);
            hasMoreCache.set(key, false);
            if (resultSummary) {
                resultSummary.textContent = "소식을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
            }
        } finally {
            setLoading(false);
        }
    }

    async function fetchPage(page, force = false, category = currentCategory) {
        const key = cacheKey(page, category);
        if (!force && pageCache.has(key)) {
            return;
        }

        setLoading(true);
        try {
            const query = new URLSearchParams({
                page: String(page),
                size: String(PAGE_SIZE),
                category: category || "all"
            });
            const response = await fetch(`/articles/page?${query.toString()}`, {
                headers: {"Accept": "application/json"}
            });
            if (!response.ok) {
                throw new Error("page fetch failed");
            }
            const result = await response.json();
            pageCache.set(key, Array.isArray(result.articles) ? result.articles : []);
            hasMoreCache.set(key, Boolean(result.hasMore));
        } catch (error) {
            pageCache.set(key, []);
            hasMoreCache.set(key, false);
            if (resultSummary) {
                resultSummary.textContent = "추가 소식을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
            }
        } finally {
            setLoading(false);
        }
    }

    async function applyFilters() {
        const nextCategory = categorySelect?.value || "all";
        currentKeyword = searchInput?.value || "";
        currentSearchType = searchType?.value || "title-summary";
        if (nextCategory !== currentCategory) {
            currentCategory = nextCategory;
            currentPage = 1;
            if (currentCategory !== "all") {
                await fetchPage(1, false, currentCategory);
            } else if (!pageCache.has(cacheKey(1, "all"))) {
                await fetchInitialPage(false);
            }
        }
        renderRows();
    }

    searchForm?.addEventListener("submit", (event) => {
        event.preventDefault();
        applyFilters();
    });

    categorySelect?.addEventListener("change", applyFilters);
    searchType?.addEventListener("change", applyFilters);

    searchInput?.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            searchInput.value = "";
            applyFilters();
        }
    });

    prevButton?.addEventListener("click", () => {
        if (currentPage > 1 && !loading) {
            currentPage -= 1;
            renderRows();
        }
    });

    nextButton?.addEventListener("click", async () => {
        if (loading || !hasMoreForCurrentPage()) {
            return;
        }
        const nextPage = currentPage + 1;
        await fetchPage(nextPage, false, currentCategory);
        currentPage = nextPage;
        renderRows();
    });

    async function initializeArticlePage() {
        const key = cacheKey(currentPage, currentCategory);
        if (currentCategory === "all" && currentPage === 1 && (!pageCache.has(key) || (pageCache.get(key) || []).length === 0)) {
            await fetchInitialPage(true);
        } else if (!pageCache.has(key) || (pageCache.get(key) || []).length === 0) {
            await fetchPage(currentPage, true, currentCategory);
        }
        renderRows();
    }

    initializeArticlePage();
});
