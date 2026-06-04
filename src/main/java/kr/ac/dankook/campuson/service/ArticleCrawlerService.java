package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.dto.CrawledArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class ArticleCrawlerService {

    private static final int REQUEST_TIMEOUT_MILLIS = 4_500;
    private static final int PAGE_TOTAL_TIMEOUT_MILLIS = 12_000;
    private static final int INITIAL_TOTAL_TIMEOUT_MILLIS = 30_000;
    private static final int ARTICLES_PER_PAGE = 6;
    private static final int ARTICLES_PER_CATEGORY = 6;
    private static final int PAGE_CANDIDATES_PER_TARGET = 10;
    private static final int INITIAL_CANDIDATES_PER_TARGET = 18;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CampusONBot/1.0";

    private static final Pattern BAD_TITLE_PATTERN = Pattern.compile(
            "(?i)(로그인|회원가입|구독|검색|메뉴|전체보기|바로가기|더보기|목록|뉴스홈|고객센터|이용약관|개인정보|제보|언론사|사이트맵)"
    );

    private final Map<String, ArticleCacheEntry> pageCache = new ConcurrentHashMap<>();
    private volatile ArticleCacheEntry initialCache;

    public ArticleFetchResult fetchArticleFeed() {
        return fetchInitialCategoryFeed(false);
    }

    public ArticleFetchResult fetchInitialCategoryFeed() {
        return fetchInitialCategoryFeed(false);
    }

    public ArticleFetchResult fetchInitialCategoryFeed(boolean refresh) {
        if (!refresh) {
            ArticleCacheEntry cached = initialCache;
            if (cached != null && !cached.isExpired()) {
                return cached.result();
            }
        } else {
            initialCache = null;
        }

        ArticleFetchResult result = fetchArticlePage(1, "all", refresh);
        initialCache = new ArticleCacheEntry(result, System.currentTimeMillis());
        return result;
    }

    public ArticleFetchResult fetchArticlePage(int page) {
        return fetchArticlePage(page, "all", false);
    }

    public ArticleFetchResult fetchArticlePage(int page, boolean refresh) {
        return fetchArticlePage(page, "all", refresh);
    }

    public ArticleFetchResult fetchArticlePage(int page, String categoryKey, boolean refresh) {
        int normalizedPage = Math.max(1, page);
        String normalizedCategory = normalizeCategoryKey(categoryKey);
        String cacheKey = normalizedCategory + ":" + normalizedPage;
        long requestStart = System.nanoTime();

        if (!refresh) {
            ArticleCacheEntry cached = pageCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.result();
            }
        } else {
            pageCache.remove(cacheKey);
            if (normalizedPage == 1 && "all".equals(normalizedCategory)) {
                initialCache = null;
            }
        }

        List<CrawlTarget> targets = targetsForCategory(normalizedCategory);
        List<CrawledArticle> collectedArticles = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        long deadlineNanos = System.nanoTime() + Duration.ofMillis(PAGE_TOTAL_TIMEOUT_MILLIS).toNanos();
        boolean totalTimeout = false;

        int requiredArticleCount = normalizedPage * ARTICLES_PER_PAGE + 1;
        int perTargetCandidateLimit = Math.max(PAGE_CANDIDATES_PER_TARGET, requiredArticleCount + 8);

        for (CrawlTarget target : targets) {
            if (System.nanoTime() >= deadlineNanos) {
                totalTimeout = true;
                break;
            }

            SiteCrawlOutcome outcome = crawlTarget(
                    target,
                    seenUrls,
                    deadlineNanos,
                    requiredArticleCount,
                    perTargetCandidateLimit
            );
            collectedArticles.addAll(outcome.articles());
        }

        if (System.nanoTime() >= deadlineNanos) {
            totalTimeout = true;
        }

        sortArticlesNewestFirst(collectedArticles);

        int startIndex = Math.max(0, (normalizedPage - 1) * ARTICLES_PER_PAGE);
        int endIndex = Math.min(collectedArticles.size(), startIndex + ARTICLES_PER_PAGE);
        List<CrawledArticle> pageArticles = startIndex < collectedArticles.size()
                ? new ArrayList<>(collectedArticles.subList(startIndex, endIndex))
                : new ArrayList<>();

        boolean failed = pageArticles.isEmpty();
        String message = failed
                ? "기사 수집에 실패했거나 수집 조건에 맞는 게시글이 없습니다."
                : totalTimeout
                ? "일부 사이트가 느려 전체 제한 시간 내에서 수집 가능한 기사만 표시합니다."
                : null;

        boolean hasMore = collectedArticles.size() > endIndex || (totalTimeout && !pageArticles.isEmpty());
        ArticleFetchResult result = new ArticleFetchResult(
                Collections.unmodifiableList(pageArticles),
                categoryCounts(pageArticles),
                failed,
                message,
                normalizedPage,
                elapsedMillis(requestStart),
                totalTimeout,
                hasMore
        );

        pageCache.put(cacheKey, new ArticleCacheEntry(result, System.currentTimeMillis()));
        if (normalizedPage == 1 && "all".equals(normalizedCategory)) {
            initialCache = new ArticleCacheEntry(result, System.currentTimeMillis());
        }
        return result;
    }

    private SiteCrawlOutcome crawlTarget(
            CrawlTarget target,
            Set<String> seenUrls,
            long deadlineNanos,
            int articleLimit,
            int candidateLimit
    ) {
        List<CrawledArticle> collected = new ArrayList<>();
        if (articleLimit <= 0) {
            return new SiteCrawlOutcome(List.of());
        }

        try {
            Document listDocument = fetchDocument(target.url());
            List<ArticleCandidate> candidates = extractCandidates(target, listDocument, candidateLimit);
            for (ArticleCandidate candidate : candidates) {
                if (collected.size() >= articleLimit) {
                    break;
                }
                if (System.nanoTime() >= deadlineNanos) {
                    break;
                }
                if (!seenUrls.add(candidate.url())) {
                    continue;
                }

                try {
                    CrawledArticle article = buildArticleFromCandidate(target, candidate);
                    if (article != null) {
                        collected.add(article);
                    }
                } catch (Exception ignored) {
                }
            }
            return new SiteCrawlOutcome(collected);
        } catch (Exception ignored) {
            return new SiteCrawlOutcome(List.of());
        }
    }

    private Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(REQUEST_TIMEOUT_MILLIS)
                .followRedirects(true)
                .ignoreContentType(true)
                .get();
    }

    private List<ArticleCandidate> extractCandidates(CrawlTarget target, Document document, int candidateLimit) {
        List<ArticleCandidate> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String selector : target.selectors()) {
            for (Element link : document.select(selector)) {
                if (candidates.size() >= candidateLimit) {
                    return candidates;
                }

                String href = link.absUrl("href");
                String title = normalize(link.text());

                if (!isUsefulCandidateTitle(title)) {
                    title = normalize(link.attr("title"));
                }
                if (!isUsefulCandidateTitle(title) || !isUsefulArticleUrl(href)) {
                    continue;
                }
                if (target.requiredHostKeyword() != null && !href.toLowerCase(Locale.ROOT).contains(target.requiredHostKeyword())) {
                    continue;
                }
                if (seen.add(href)) {
                    candidates.add(new ArticleCandidate(title, href, null, null, null));
                }
            }
        }

        if (target.listPageOnly() && candidates.size() < candidateLimit) {
            candidates.addAll(extractBoardTextCandidates(target, document, candidateLimit - candidates.size(), seen));
        }

        return candidates;
    }

    private List<ArticleCandidate> extractBoardTextCandidates(
            CrawlTarget target,
            Document document,
            int remainingLimit,
            Set<String> seen
    ) {
        List<ArticleCandidate> candidates = new ArrayList<>();
        if (remainingLimit <= 0) {
            return candidates;
        }

        for (Element titleElement : document.select(".title, .subject, .bbs-title, .board-title, td a[href], h3, h4, h5")) {
            if (candidates.size() >= remainingLimit) {
                break;
            }

            String title = cleanBoardTitle(titleElement.text());
            if (!isUsefulCandidateTitle(title)) {
                continue;
            }

            String summary = extractNearbySummary(titleElement);
            String publishedAt = firstDate(titleElement.parent() != null ? titleElement.parent().text() : "");
            if (publishedAt == null) {
                publishedAt = firstDate(summary);
            }

            String href = titleElement.hasAttr("href") ? titleElement.absUrl("href") : "";
            if (href == null || href.isBlank() || !isUsefulArticleUrl(href)) {
                href = target.url();
            }
            href = href + "#" + Math.abs((title + publishedAt).hashCode());

            if (seen.add(href)) {
                candidates.add(new ArticleCandidate(title, href, summary, publishedAt, null));
            }
        }

        if (candidates.isEmpty()) {
            candidates.addAll(extractBoardTextCandidatesFromLines(target, document, remainingLimit, seen));
        }
        return candidates;
    }

    private List<ArticleCandidate> extractBoardTextCandidatesFromLines(
            CrawlTarget target,
            Document document,
            int remainingLimit,
            Set<String> seen
    ) {
        List<ArticleCandidate> candidates = new ArrayList<>();
        String[] lines = document.text().split("(?=\\d{4}\\.\\d{2}\\.\\d{2})|(?=\\d{4}\\.\\d{1,2}\\.\\d{1,2})");
        for (String line : lines) {
            if (candidates.size() >= remainingLimit) {
                break;
            }
            String normalized = normalize(line);
            if (normalized.length() < 20) {
                continue;
            }

            String publishedAt = firstDate(normalized);
            String withoutDate = publishedAt == null ? normalized : normalized.replace(publishedAt, "").trim();
            String title = cleanBoardTitle(withoutDate.length() > 80 ? withoutDate.substring(0, 80) : withoutDate);
            if (!isUsefulCandidateTitle(title)) {
                continue;
            }

            String href = target.url() + "#" + Math.abs((title + publishedAt).hashCode());
            if (seen.add(href)) {
                candidates.add(new ArticleCandidate(title, href, limitText(withoutDate, 180), publishedAt, null));
            }
        }
        return candidates;
    }

    private CrawledArticle buildArticleFromCandidate(CrawlTarget target, ArticleCandidate candidate) throws IOException {
        if (target.listPageOnly()) {
            return buildArticleFromListCandidate(target, candidate);
        }

        Document articleDocument = fetchDocument(candidate.url());

        String title = firstNonBlank(
                meta(articleDocument, "meta[property=og:title]", "content"),
                meta(articleDocument, "meta[name=twitter:title]", "content"),
                articleDocument.title(),
                candidate.title()
        );
        title = cleanTitle(title);

        String summary = firstNonBlank(
                meta(articleDocument, "meta[property=og:description]", "content"),
                meta(articleDocument, "meta[name=description]", "content"),
                meta(articleDocument, "meta[name=twitter:description]", "content"),
                candidate.summary(),
                "본문 요약을 제공하지 않는 게시글입니다. 제목과 원문 링크를 확인하세요."
        );
        summary = limitText(cleanTitle(summary), 180);

        if (!matchesTargetContent(target, title, summary)) {
            throw new IllegalStateException("target-keyword-mismatch");
        }

        String image = firstNonBlank(
                meta(articleDocument, "meta[property=og:image]", "content"),
                meta(articleDocument, "meta[name=twitter:image]", "content"),
                firstImage(articleDocument),
                candidate.thumbnailUrl()
        );

        if (!isUsableImage(image)) {
            throw new IllegalStateException("thumbnail-not-found");
        }

        String publishedAt = firstNonBlank(
                meta(articleDocument, "meta[property=article:published_time]", "content"),
                meta(articleDocument, "meta[name=date]", "content"),
                meta(articleDocument, "meta[name=pubdate]", "content"),
                meta(articleDocument, "time[datetime]", "datetime"),
                candidate.publishedAt(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );
        publishedAt = normalizeDate(publishedAt);

        return new CrawledArticle(
                target.categoryKey(),
                target.categoryLabel(),
                target.sourceName(),
                title,
                candidate.url(),
                image,
                summary,
                publishedAt,
                target.tags()
        );
    }

    private CrawledArticle buildArticleFromListCandidate(CrawlTarget target, ArticleCandidate candidate) {
        String title = cleanTitle(firstNonBlank(candidate.title(), target.name()));
        String summary = limitText(firstNonBlank(
                candidate.summary(),
                "원문 게시판에서 상세 내용을 확인할 수 있는 SW중심대학사업단 소식입니다."
        ), 180);

        if (!matchesTargetContent(target, title, summary)) {
            throw new IllegalStateException("target-keyword-mismatch");
        }

        String image = isUsableImage(candidate.thumbnailUrl()) ? candidate.thumbnailUrl() : "";
        String publishedAt = normalizeDate(firstNonBlank(
                candidate.publishedAt(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        ));

        ArticleCategory articleCategory = inferArticleCategory(target, title, summary);

        return new CrawledArticle(
                articleCategory.key(),
                articleCategory.label(),
                target.sourceName(),
                title,
                candidate.url(),
                image,
                summary,
                publishedAt,
                mergeTags(target.tags(), articleCategory.tag())
        );
    }

    private ArticleCategory inferArticleCategory(CrawlTarget target, String title, String summary) {
        if (!target.listPageOnly() || target.url().contains("swcu.dankook.ac.kr")) {
            return new ArticleCategory(target.categoryKey(), target.categoryLabel(), null);
        }

        String text = normalize(title + " " + summary).toLowerCase(Locale.ROOT);
        if (containsAny(text, "채용", "취업", "인턴", "커리어", "면접", "자기소개서", "창업")) {
            return new ArticleCategory("career", "취창업", "#취창업");
        }
        if (containsAny(text, "부트캠프", "아카데미", "교육", "강의", "훈련", "국비", "topcit", "특강", "마이크로디그리")) {
            return new ArticleCategory("bootcamp", "부트캠프/AI교육", "#AI교육");
        }
        if (containsAny(text, "공모전", "경진대회", "대회", "챌린지", "contest", "페스티벌", "행사")) {
            return new ArticleCategory("contest", "공모전", "#공모전");
        }
        if (containsAny(text, "단국", "캠퍼스", "재학생", "학과", "사업단", "sw중심대학")) {
            return new ArticleCategory("school", "학교소식", "#학교소식");
        }
        return new ArticleCategory("it", "최신 IT정보", "#IT");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> mergeTags(List<String> baseTags, String extraTag) {
        if (extraTag == null || extraTag.isBlank()) {
            return baseTags;
        }
        List<String> merged = new ArrayList<>(baseTags);
        if (!merged.contains(extraTag)) {
            merged.add(extraTag);
        }
        return merged;
    }

    private Map<String, List<CrawlTarget>> targetsByCategory() {
        Map<String, List<CrawlTarget>> grouped = new LinkedHashMap<>();
        for (CrawlTarget target : allTargets()) {
            grouped.computeIfAbsent(target.categoryKey(), ignored -> new ArrayList<>()).add(target);
        }
        return grouped;
    }

    private List<CrawlTarget> targetsForPage(int page, String categoryKey) {
        List<CrawlTarget> targets = targetsForCategory(categoryKey);
        int start = Math.max(0, (page - 1) * 3);
        if (start >= targets.size()) {
            return List.of();
        }
        int end = Math.min(targets.size(), start + 4);
        return targets.subList(start, end);
    }

    private List<CrawlTarget> targetsForCategory(String categoryKey) {
        String normalizedCategory = normalizeCategoryKey(categoryKey);
        if ("all".equals(normalizedCategory)) {
            return allTargets();
        }
        List<CrawlTarget> filtered = new ArrayList<>();
        for (CrawlTarget target : allTargets()) {
            if (normalizedCategory.equals(target.categoryKey())) {
                filtered.add(target);
            }
        }
        return filtered;
    }

    private String normalizeCategoryKey(String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank()) {
            return "all";
        }
        String normalized = categoryKey.trim().toLowerCase(Locale.ROOT);
        return Set.of("all", "it", "school", "career", "contest", "bootcamp").contains(normalized) ? normalized : "all";
    }

    private void sortArticlesNewestFirst(List<CrawledArticle> articles) {
        articles.sort((left, right) -> normalize(right.publishedAt()).compareTo(normalize(left.publishedAt())));
    }

    private List<CrawlTarget> allTargets() {
        List<CrawlTarget> targets = new ArrayList<>();

        targets.add(newsSearchTarget(
                "네이버 IT 뉴스",
                "https://search.naver.com/search.naver?where=news&sort=1&query=" + enc("IT 뉴스 AI 반도체 소프트웨어 개발자"),
                "it",
                "최신 IT정보",
                "네이버 뉴스",
                List.of("#IT", "#AI", "#국내뉴스"),
                null,
                List.of("IT", "AI", "인공지능", "반도체", "소프트웨어", "개발자", "기술", "디지털"),
                List.of("연예", "스포츠", "정치", "맛집")
        ));
        targets.add(newsSearchTarget(
                "다음 IT 뉴스",
                "https://search.daum.net/search?w=news&sort=recency&q=" + enc("IT 뉴스 AI 개발자 소프트웨어"),
                "it",
                "최신 IT정보",
                "다음 뉴스",
                List.of("#IT", "#AI", "#국내뉴스"),
                null,
                List.of("IT", "AI", "인공지능", "개발자", "소프트웨어", "기술", "디지털"),
                List.of("연예", "스포츠", "정치", "맛집")
        ));
        targets.add(new CrawlTarget(
                "카카오 기술 블로그",
                "https://tech.kakao.com/blog",
                "it",
                "최신 IT정보",
                "Kakao Tech",
                List.of("#IT", "#기술블로그", "#카카오"),
                List.of("a[href*='/blog/']", "a[href*='tech.kakao.com']", "a[href]"),
                "tech.kakao.com",
                List.of("AI", "개발", "기술", "채용", "커리어", "컨퍼런스", "학생", "서버", "프론트엔드", "백엔드", "데이터"),
                List.of("사이트맵", "개인정보", "Back to Blog")
        ));
        targets.add(newsSearchTarget(
                "AI 부트캠프 교육 정보",
                "https://search.naver.com/search.naver?where=news&sort=1&query=" + enc("AI 부트캠프 개발자 교육 국비지원 부트캠프"),
                "bootcamp",
                "부트캠프/AI교육",
                "AI 교육 정보",
                List.of("#AI", "#부트캠프", "#교육", "#개발자"),
                null,
                List.of("AI", "인공지능", "부트캠프", "개발자", "교육", "훈련", "양성", "수료", "국비", "과정", "SW", "소프트웨어"),
                List.of("Who Is", "총장", "인사", "부고", "정치", "여성뉴스", "칼럼", "사설", "맛집", "연예")
        ));
        targets.add(newsSearchTarget(
                "컴퓨터공학 취창업 정보",
                "https://search.naver.com/search.naver?where=news&sort=1&query=" + enc("개발자 취업 창업 컴퓨터공학과 소프트웨어 채용"),
                "career",
                "취창업",
                "취창업 정보",
                List.of("#취업정보", "#창업", "#개발자"),
                null,
                List.of("개발자", "취업", "채용", "창업", "인턴", "커리어", "컴퓨터공학", "소프트웨어", "SW", "인재", "모집"),
                List.of("Who Is", "총장", "인사", "부고", "정치", "여성뉴스", "칼럼", "사설", "연예", "스포츠")
        ));
        targets.add(newsSearchTarget(
                "공모전 정보",
                "https://search.naver.com/search.naver?where=news&sort=1&query=" + enc("대학생 IT 공모전 AI 소프트웨어 경진대회 모집"),
                "contest",
                "공모전",
                "공모전 정보",
                List.of("#공모전", "#대학생", "#IT"),
                null,
                List.of("공모전", "경진대회", "대학생", "모집", "참가", "AI", "소프트웨어", "IT"),
                List.of("연예", "스포츠", "정치", "부고")
        ));

        targets.add(new CrawlTarget(
                "단국대 SW중심대학 SW대회/행사",
                "https://swcu.dankook.ac.kr/ko/-2024-",
                "school",
                "학교소식",
                "DKU SW중심대학사업단",
                List.of("#단국대", "#SW중심대학", "#학교소식", "#공모전", "#행사"),
                List.of(".bbs a[href]", ".board a[href]", ".title a[href]", "a[href*='_dku_bbs_web_BbsPortlet']", "td a[href]", "h3", "h4", "h5"),
                "swcu.dankook.ac.kr",
                List.of("공모전", "경진대회", "대회", "행사", "특강", "세미나", "SW", "AI", "소프트웨어", "프로그램", "모집", "참여", "TOPCIT", "창업"),
                List.of("로그인", "개인정보", "사이트맵", "작성일", "수정일", "검색"),
                true
        ));
        targets.add(new CrawlTarget(
                "단국대 SW중심대학 외부소식",
                "https://swcu.dankook.ac.kr/ko/-24",
                "school",
                "학교소식",
                "DKU SW중심대학사업단",
                List.of("#단국대", "#SW중심대학", "#학교소식", "#외부소식"),
                List.of(".bbs a[href]", ".board a[href]", ".title a[href]", "a[href*='_dku_bbs_web_BbsPortlet']", "td a[href]", "h3", "h4", "h5"),
                "swcu.dankook.ac.kr",
                List.of("AI", "인공지능", "소프트웨어", "SW", "IT", "데이터", "개발", "기술", "취업", "채용", "인턴", "창업", "공모전", "대회", "교육", "부트캠프", "특강", "행사"),
                List.of("로그인", "개인정보", "사이트맵", "작성일", "수정일", "검색"),
                true
        ));
        targets.add(new CrawlTarget(
                "단국대학교 뉴스",
                "http://dknews.dankook.ac.kr/",
                "school",
                "학교소식",
                "단대신문",
                List.of("#단국대", "#학교소식", "#단대신문"),
                List.of("a[href*='news/articleView.html']", "a[href*='articleView']", "a[href]"),
                "dknews.dankook.ac.kr",
                List.of("단국", "대학", "학생", "캠퍼스", "교수", "동아리", "학과", "취업", "공모전", "소식"),
                List.of("로그인", "개인정보", "사이트맵", "구독", "광고")
        ));

        return targets;
    }

    private CrawlTarget newsSearchTarget(
            String name,
            String url,
            String categoryKey,
            String categoryLabel,
            String sourceName,
            List<String> tags,
            String requiredHostKeyword,
            List<String> includeKeywords,
            List<String> excludeKeywords
    ) {
        return new CrawlTarget(
                name,
                url,
                categoryKey,
                categoryLabel,
                sourceName,
                tags,
                List.of(
                        "a.news_tit[href]",
                        "a.tit_main[href]",
                        "a.tit_g[href]",
                        "a.link_txt[href]",
                        "a[href*='news.naver.com']",
                        "a[href*='v.daum.net']",
                        "a[href]"
                ),
                requiredHostKeyword,
                includeKeywords,
                excludeKeywords
        );
    }

    private boolean isUsefulArticleUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return false;
        }
        if (lower.contains("google.com") || lower.contains("news.google.com")) {
            return false;
        }
        if (lower.contains("search.naver.com") || lower.contains("search.daum.net")) {
            return false;
        }
        return !lower.contains("javascript:");
    }

    private boolean isUsefulCandidateTitle(String title) {
        if (title == null) {
            return false;
        }
        String normalized = normalize(title);
        return normalized.length() >= 10 && !BAD_TITLE_PATTERN.matcher(normalized).find();
    }

    private boolean isUsableImage(String image) {
        if (image == null || image.isBlank()) {
            return false;
        }
        String lower = image.toLowerCase(Locale.ROOT);
        return (lower.startsWith("http://") || lower.startsWith("https://"))
                && !lower.contains("placeholder")
                && !lower.endsWith(".svg")
                && !lower.contains("logo");
    }

    private boolean matchesTargetContent(CrawlTarget target, String title, String summary) {
        String combined = normalize((title == null ? "" : title) + " " + (summary == null ? "" : summary));
        String lower = combined.toLowerCase(Locale.ROOT);

        for (String keyword : target.excludeKeywords()) {
            if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        if (target.includeKeywords() == null || target.includeKeywords().isEmpty()) {
            return true;
        }

        for (String keyword : target.includeKeywords()) {
            if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Long> categoryCounts(List<CrawledArticle> articles) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", (long) articles.size());
        for (CrawledArticle article : articles) {
            counts.merge(article.categoryKey(), 1L, Long::sum);
        }
        return counts;
    }

    private String meta(Document document, String cssQuery, String attr) {
        Element element = document.selectFirst(cssQuery);
        if (element == null) {
            return null;
        }
        String value = element.attr(attr);
        if (value == null || value.isBlank()) {
            value = element.text();
        }
        return value;
    }

    private String firstImage(Document document) {
        for (Element image : document.select("article img[src], .article img[src], .news_view img[src], .view img[src], .article-view img[src], img[src]")) {
            String src = image.absUrl("src");
            if (isUsableImage(src)) {
                return src;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String cleanBoardTitle(String value) {
        String title = cleanTitle(value)
                .replaceAll("^\\[[^\\]]+\\]\\s*", "")
                .replaceAll("\\s+H$", "")
                .trim();
        return limitText(title, 90);
    }

    private String extractNearbySummary(Element titleElement) {
        Element parent = titleElement.parent();
        StringBuilder builder = new StringBuilder();

        if (parent != null) {
            String parentText = normalize(parent.text().replace(titleElement.text(), " "));
            if (!parentText.isBlank()) {
                builder.append(parentText);
            }

            Element sibling = parent.nextElementSibling();
            int count = 0;
            while (sibling != null && count < 3) {
                String siblingText = normalize(sibling.text());
                if (!siblingText.isBlank() && !BAD_TITLE_PATTERN.matcher(siblingText).find()) {
                    if (builder.length() > 0) {
                        builder.append(' ');
                    }
                    builder.append(siblingText);
                }
                sibling = sibling.nextElementSibling();
                count++;
            }
        }

        String summary = normalize(builder.toString());
        return summary.isBlank() ? null : limitText(summary, 180);
    }

    private String firstDate(String value) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher matcher = Pattern.compile("(20\\d{2})[.-](\\d{1,2})[.-](\\d{1,2})").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1) + "-"
                + String.format("%02d", Integer.parseInt(matcher.group(2))) + "-"
                + String.format("%02d", Integer.parseInt(matcher.group(3)));
    }

    private String cleanTitle(String value) {
        String normalized = normalize(value);
        return normalized
                .replace("| Daum 뉴스", "")
                .replace("- 네이버뉴스", "")
                .replace(" - Kakao Tech", "")
                .replace(" - 단대신문", "")
                .trim();
    }

    private String limitText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = normalize(value);
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private String normalizeDate(String value) {
        String normalized = normalize(value);
        if (normalized.length() >= 10) {
            return normalized.substring(0, 10);
        }
        return normalized;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    public record ArticleFetchResult(
            List<CrawledArticle> articles,
            Map<String, Long> categoryCounts,
            boolean failed,
            String message,
            int page,
            long elapsedMillis,
            boolean totalTimeout,
            boolean hasMore
    ) {
        public ArticleFetchResult(
                List<CrawledArticle> articles,
                Map<String, Long> categoryCounts,
                boolean failed,
                String message
        ) {
            this(articles, categoryCounts, failed, message, 1, 0, false, false);
        }
    }

    private record ArticleCacheEntry(ArticleFetchResult result, long createdAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() - createdAtMillis > CACHE_TTL.toMillis();
        }
    }

    private record CrawlTarget(
            String name,
            String url,
            String categoryKey,
            String categoryLabel,
            String sourceName,
            List<String> tags,
            List<String> selectors,
            String requiredHostKeyword,
            List<String> includeKeywords,
            List<String> excludeKeywords,
            boolean listPageOnly
    ) {
        private CrawlTarget(
                String name,
                String url,
                String categoryKey,
                String categoryLabel,
                String sourceName,
                List<String> tags,
                List<String> selectors,
                String requiredHostKeyword,
                List<String> includeKeywords,
                List<String> excludeKeywords
        ) {
            this(name, url, categoryKey, categoryLabel, sourceName, tags, selectors, requiredHostKeyword, includeKeywords, excludeKeywords, false);
        }
    }

    private record ArticleCandidate(
            String title,
            String url,
            String summary,
            String publishedAt,
            String thumbnailUrl
    ) {
    }

    private record ArticleCategory(String key, String label, String tag) {
    }

    private record SiteCrawlOutcome(List<CrawledArticle> articles) {
    }
}
