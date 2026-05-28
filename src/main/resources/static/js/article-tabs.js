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
            tags: normalizeTags(row.dataset.tags || "")
        };
    }

    const initialArticles = Array.from(rowsContainer.querySelectorAll(".article-row")).map(rowToArticle);
    const storedInitialPage = readStoredInitialPage();

    if (storedInitialPage && Number(storedInitialPage.page || 1) === 1) {
        currentPage = 1;
        pageCache.set(1, Array.isArray(storedInitialPage.articles) ? storedInitialPage.articles : []);
        hasMoreCache.set(1, Boolean(storedInitialPage.hasMore));
        sessionStorage.removeItem("campusonArticleInitialPage");
    } else {
        pageCache.set(currentPage, initialArticles);
        hasMoreCache.set(currentPage, rowsContainer.dataset.hasMore === "true");
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
        const link = document.createElement("a");
        link.className = "article-row";
        link.href = article.articleUrl || "#";
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        link.dataset.category = article.categoryKey || "";
        link.dataset.categoryLabel = article.categoryLabel || "";
        link.dataset.source = article.sourceName || "";
        link.dataset.title = article.title || "";
        link.dataset.summary = article.summary || "";
        link.dataset.tags = normalizeTags(article.tags || "");
        link.dataset.rawImage = article.thumbnailUrl || "";
        link.dataset.image = proxiedImageUrl(article.thumbnailUrl || "");
        link.dataset.published = article.publishedAt || "";

        const tagHtml = normalizeTags(article.tags)
            .split(",")
            .map(tag => tag.trim())
            .filter(Boolean)
            .map(tag => `<span>${escapeHtml(displayTagLabel(tag))}</span>`)
            .join("");

        link.innerHTML = `
            <div class="article-row-thumb-wrap">
                <img class="article-row-thumb" src="${escapeHtml(proxiedImageUrl(article.thumbnailUrl || ""))}" alt="${escapeHtml(article.title || "기사 썸네일")}">
            </div>
            <div class="article-row-content">
                <div class="article-row-meta">
                    <span class="article-row-badge">${escapeHtml(article.categoryLabel || "카테고리")}</span>
                    <span class="article-row-source">${escapeHtml(article.sourceName || "출처")}</span>
                    <span class="article-row-date">${escapeHtml(article.publishedAt || "")}</span>
                </div>
                <h3 class="article-row-title">${escapeHtml(article.title || "제목 없음")}</h3>
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
        const articles = pageCache.get(currentPage) || [];
        return articles.filter(article => matchesCategory(article) && matchesSearch(article));
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
            nextButton.disabled = loading || !hasMoreCache.get(currentPage);
            nextButton.textContent = loading ? "불러오는 중..." : "다음";
        }
        if (prevButton) {
            prevButton.disabled = loading || currentPage <= 1;
        }
        if (resultSummary && loading) {
            resultSummary.textContent = `${currentPage + 1}페이지 소식을 추가로 불러오는 중입니다.`;
        }
    }

    function updateSummary(count) {
        if (!resultSummary) {
            return;
        }
        const total = (pageCache.get(currentPage) || []).length;
        const suffix = currentKeyword || currentCategory !== "all" ? ` / 조건 일치 ${count}개` : "";
        resultSummary.textContent = `${currentPage}페이지에서 원본 게시글 ${total}개를 불러왔습니다${suffix}.`;
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
            nextButton.disabled = loading || !hasMoreCache.get(currentPage);
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
        if (!force && pageCache.has(1) && (pageCache.get(1) || []).length > 0) {
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
            pageCache.set(1, Array.isArray(result.articles) ? result.articles : []);
            hasMoreCache.set(1, Boolean(result.hasMore));
        } catch (error) {
            pageCache.set(1, []);
            hasMoreCache.set(1, false);
            if (resultSummary) {
                resultSummary.textContent = "소식을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
            }
        } finally {
            setLoading(false);
        }
    }

    async function fetchPage(page, force = false) {
        if (!force && pageCache.has(page)) {
            return;
        }

        setLoading(true);
        try {
            const response = await fetch(`/articles/page?page=${page}&size=${PAGE_SIZE}`, {
                headers: {"Accept": "application/json"}
            });
            if (!response.ok) {
                throw new Error("page fetch failed");
            }
            const result = await response.json();
            pageCache.set(page, Array.isArray(result.articles) ? result.articles : []);
            hasMoreCache.set(page, Boolean(result.hasMore));
        } catch (error) {
            pageCache.set(page, []);
            hasMoreCache.set(page, false);
            if (resultSummary) {
                resultSummary.textContent = "추가 소식을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
            }
        } finally {
            setLoading(false);
        }
    }

    function applyFilters() {
        currentKeyword = searchInput?.value || "";
        currentSearchType = searchType?.value || "title-summary";
        currentCategory = categorySelect?.value || "all";
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
        if (loading || !hasMoreCache.get(currentPage)) {
            return;
        }
        const nextPage = currentPage + 1;
        await fetchPage(nextPage);
        currentPage = nextPage;
        renderRows();
    });

    async function initializeArticlePage() {
        if (currentPage === 1 && (!pageCache.has(1) || (pageCache.get(1) || []).length === 0)) {
            await fetchInitialPage(true);
        } else if (!pageCache.has(currentPage) || (pageCache.get(currentPage) || []).length === 0) {
            await fetchPage(currentPage, true);
        }
        renderRows();
    }

    initializeArticlePage();
});
