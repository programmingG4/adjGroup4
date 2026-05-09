package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.dto.CrawledArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArticleCrawlerService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(20);
    private static final List<String> CATEGORY_ORDER = List.of("school", "news", "it", "career", "support");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final ExecutorService FETCH_POOL = Executors.newFixedThreadPool(16);

    private volatile CacheEntry cacheEntry;

    public ArticleFetchResult fetchArticleFeed() {
        CacheEntry localCache = cacheEntry;
        if (localCache != null && Duration.between(localCache.cachedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return localCache.result();
        }

        synchronized (this) {
            localCache = cacheEntry;
            if (localCache != null && Duration.between(localCache.cachedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
                return localCache.result();
            }

            ArticleFetchResult result = crawlCombinedArticles();
            cacheEntry = new CacheEntry(Instant.now(), result);
            return result;
        }
    }

    private ArticleFetchResult crawlCombinedArticles() {
        List<SeedArticle> candidates = new ArrayList<>();
        candidates.addAll(discoverArticlesFromFeeds());
        candidates.addAll(seedArticles());

        List<CompletableFuture<FetchOutcome>> futures = candidates.stream()
                .map(seed -> CompletableFuture.supplyAsync(() -> fetchArticle(seed), FETCH_POOL))
                .toList();

        List<FetchOutcome> outcomes = futures.stream()
                .map(f -> f.exceptionally(ex -> new FetchOutcome(null, false)).join())
                .toList();

        Map<String, CrawledArticle> unique = new LinkedHashMap<>();
        int failedCount = 0;

        for (FetchOutcome outcome : outcomes) {
            if (outcome.article() != null) {
                unique.putIfAbsent(dedupeKey(outcome.article()), outcome.article());
            }
            if (!outcome.success()) {
                failedCount++;
            }
        }

        List<CrawledArticle> articles = balanceByCategory(new ArrayList<>(unique.values()));
        Map<String, Long> counts = categoryCounts(articles);

        boolean failed = false;
        String message = "";

        if (articles.isEmpty()) {
            failed = true;
            message = "네트워크 지연 또는 타임아웃으로 기사를 불러오는 데 실패했습니다. 잠시 후 다시 시도해주세요.";
        } else if (failedCount >= 12) {
            failed = true;
            message = "일부 기사 수집이 지연됐지만, 여러 뉴스·학교·지원 소스에서 확보한 기사부터 먼저 보여드립니다.";
        }

        return new ArticleFetchResult(articles, counts, failed, message);
    }

    private List<SeedArticle> discoverArticlesFromFeeds() {
        List<SeedArticle> discovered = new ArrayList<>();

        for (SourceFeed source : sourceFeeds()) {
            try {
                Document document = Jsoup.connect(source.feedUrl())
                        .userAgent("Mozilla/5.0 CampusONBot/1.0")
                        .timeout(5000)
                        .get();

                Map<String, FeedCandidate> ranked = new LinkedHashMap<>();
                for (Element anchor : document.select("a[href]")) {
                    String href = anchor.absUrl("href");
                    if (!isValidArticleLink(href, source.urlMarkers())) {
                        continue;
                    }

                    String title = safeText(firstNonBlank(anchor.text(), anchor.attr("title"), anchor.attr("aria-label")));
                    if (title.length() < 8) {
                        continue;
                    }

                    int score = scoreCandidate(title, href, source.keywordHints());
                    if (score <= 0) {
                        continue;
                    }

                    ranked.compute(href, (key, existing) -> {
                        if (existing == null || score > existing.score()) {
                            return new FeedCandidate(title, href, score);
                        }
                        return existing;
                    });
                }

                ranked.values().stream()
                        .sorted(Comparator.comparingInt(FeedCandidate::score).reversed())
                        .limit(source.limit())
                        .forEach(candidate -> discovered.add(new SeedArticle(
                                source.categoryKey(),
                                source.categoryLabel(),
                                source.sourceName(),
                                candidate.url(),
                                candidate.title(),
                                source.sourceName() + "에서 수집한 기사입니다.",
                                ""
                        )));
            } catch (Exception ignored) {
            }
        }

        return discovered;
    }

    private int scoreCandidate(String title, String url, List<String> keywordHints) {
        int score = 1;
        if (title.length() >= 12 && title.length() <= 90) {
            score += 2;
        }
        if (url.contains("article") || url.contains("detail") || url.contains("view")) {
            score += 2;
        }

        String lowered = title.toLowerCase(Locale.ROOT);
        for (String keyword : keywordHints) {
            if (lowered.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 3;
            }
        }
        return score;
    }

    private boolean isValidArticleLink(String href, List<String> urlMarkers) {
        if (href == null || href.isBlank()) {
            return false;
        }
        if (href.startsWith("javascript:") || href.startsWith("mailto:")) {
            return false;
        }
        for (String marker : urlMarkers) {
            if (href.contains(marker)) {
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

    private FetchOutcome fetchArticle(SeedArticle seed) {
        try {
            Document document = Jsoup.connect(seed.articleUrl())
                    .userAgent("Mozilla/5.0 CampusONBot/1.0")
                    .timeout(5000)
                    .get();

            String title = firstNonBlank(
                    meta(document, "property", "og:title"),
                    meta(document, "name", "twitter:title"),
                    meta(document, "name", "title"),
                    document.title(),
                    seed.fallbackTitle()
            );

            String summary = trimToLength(firstNonBlank(
                    meta(document, "property", "og:description"),
                    meta(document, "name", "description"),
                    meta(document, "name", "twitter:description"),
                    firstMeaningfulParagraph(document),
                    seed.fallbackSummary()
            ), 160);

            String image = firstNonBlank(
                    absMeta(document, "property", "og:image"),
                    absMeta(document, "name", "twitter:image"),
                    firstImage(document),
                    ""
            );

            String publishedAt = cleanPublishedAt(firstNonBlank(
                    meta(document, "property", "article:published_time"),
                    meta(document, "name", "pubdate"),
                    meta(document, "name", "publish-date"),
                    meta(document, "property", "og:published_time"),
                    timeValue(document),
                    seed.fallbackPublishedAt()
            ));

            CrawledArticle article = new CrawledArticle(
                    seed.categoryKey(),
                    seed.categoryLabel(),
                    seed.sourceName(),
                    safeText(title),
                    seed.articleUrl(),
                    image,
                    safeText(summary),
                    safeText(publishedAt)
            );

            return new FetchOutcome(article, true);
        } catch (Exception ignored) {
            CrawledArticle fallbackArticle = new CrawledArticle(
                    seed.categoryKey(),
                    seed.categoryLabel(),
                    seed.sourceName(),
                    seed.fallbackTitle(),
                    seed.articleUrl(),
                    "",
                    seed.fallbackSummary(),
                    cleanPublishedAt(seed.fallbackPublishedAt())
            );
            return new FetchOutcome(fallbackArticle, false);
        }
    }

    private List<CrawledArticle> balanceByCategory(List<CrawledArticle> articles) {
        Map<String, List<CrawledArticle>> grouped = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            grouped.put(category, new ArrayList<>());
        }
        for (CrawledArticle article : articles) {
            grouped.computeIfAbsent(article.categoryKey(), key -> new ArrayList<>()).add(article);
        }

        Comparator<CrawledArticle> comparator = Comparator
                .comparing(this::publishedDateOrMin)
                .reversed()
                .thenComparing(CrawledArticle::title);

        grouped.values().forEach(list -> list.sort(comparator));

        List<CrawledArticle> balanced = new ArrayList<>();
        boolean remaining = true;
        while (remaining) {
            remaining = false;
            for (String category : grouped.keySet()) {
                List<CrawledArticle> list = grouped.get(category);
                if (list != null && !list.isEmpty()) {
                    balanced.add(list.remove(0));
                    remaining = true;
                }
            }
        }
        return balanced;
    }

    private LocalDate publishedDateOrMin(CrawledArticle article) {
        String raw = article.publishedAt();
        if (raw == null || raw.isBlank()) {
            return LocalDate.MIN;
        }

        try {
            return OffsetDateTime.parse(raw).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }

        Matcher matcher = DATE_PATTERN.matcher(raw);
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            return LocalDate.of(year, month, day);
        }
        return LocalDate.MIN;
    }

    private String dedupeKey(CrawledArticle article) {
        return article.categoryKey() + "|" + normalizeTitle(article.title());
    }

    private String normalizeTitle(String title) {
        return safeText(title).toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "");
    }

    private List<SourceFeed> sourceFeeds() {
        return List.of(
                new SourceFeed("school", "학교 소식", "한국대학신문", "https://news.unn.net", List.of("articleView", "/news/article"), List.of("단국", "대학", "학생", "캠퍼스", "학과", "교육"), 12),
                new SourceFeed("news", "뉴스", "블로터", "https://www.bloter.net", List.of("articleView", "/news/article"), List.of("AI", "클라우드", "반도체", "보안", "플랫폼", "개발"), 12),
                new SourceFeed("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/", List.of("/magazine/detail/"), List.of("AI", "개발", "프론트엔드", "백엔드", "UX", "UI", "데이터", "클라우드", "보안", "검색"), 14),
                new SourceFeed("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/", List.of("/magazine/detail/"), List.of("취업", "이력서", "면접", "포트폴리오", "커리어", "채용", "신입", "창업"), 14),
                new SourceFeed("support", "기술개발 지원", "K-Startup", "https://www.k-startup.go.kr/web/main/mainSection0.do", List.of("/web/", "pbancSn", "do?"), List.of("지원", "사업", "모집", "공고", "바우처", "기술개발", "R&D", "스타트업"), 12),
                new SourceFeed("support", "기술개발 지원", "벤처스퀘어", "https://www.venturesquare.net/announcement", List.of("/announcement/", "/news/article"), List.of("지원", "모집", "창업", "스타트업", "기술개발", "사업"), 10)
        );
    }

    private List<SeedArticle> seedArticles() {
        List<SeedArticle> seeds = new ArrayList<>();

        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=590840", "단국대 교사 임용시험 합격자 기사", "단국대학교 교원 양성 관련 기사입니다.", "2026-03-17"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=590729", "단국대 고교학점제 협력 기사", "단국대학교의 지역 연계 교육 협력 기사입니다.", "2026-03-12"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=590468", "단국대 국제화역량 관련 기사", "단국대학교의 교육 국제화 관련 기사입니다.", "2026-03-04"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=589508", "단국대 창업교육 우수대학 기사", "단국대학교 창업교육 관련 기사입니다.", "2026-02-12"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=581221", "단국대학교 AI 거점 대학 관련 기사", "단국대학교 인공지능학과 및 AI 거점 대학 관련 기사입니다.", "2025-07-18"));

        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=659127", "방위산업 IR 개최 기사", "첨단기술 분야 진출 관련 기사입니다.", "2026-04-10"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=659117", "리벨리온-Arm-SKT 공동개발 기사", "AI 인프라와 반도체 관련 기사입니다.", "2026-04-10"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=658906", "시스코 AI 인프라 기사", "AI 인프라 비용과 네트워크 기사입니다.", "2026-04-08"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=658803", "AWS 관련 클라우드 기사", "클라우드와 AI 인프라 관련 기사입니다.", "2026-04-07"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=658453", "AI 에이전트 관련 기사", "생성형 AI 서비스 흐름 기사입니다.", "2026-04-03"));

        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3701/", "리드 개발자 마인드셋 기사", "개발자 성장 관점 기사입니다.", "2026-04-10"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3677/", "AI 검색 서비스 관련 기사", "검색과 AI 흐름 관련 기사입니다.", "2026-03-26"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3655/", "개발자 생산성 관련 기사", "실무 생산성 관련 기사입니다.", "2026-03-17"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3639/", "사랑받는 AI의 비밀", "AI UX 설계 관련 기사입니다.", "2026-03-05"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3519/", "2026년 프론트엔드 트렌드 총정리", "프론트엔드 트렌드 기사입니다.", "2025-12-24"));

        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/3694/", "AI로 프로젝트 10개 만든 개발자가 서류에서 떨어지는 이유", "최근 취업 시장 관련 기사입니다.", "2026-04-07"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/3391/", "개발자 포트폴리오와 경력기술서 작성법", "포트폴리오 작성 전략 글입니다.", "2025-10-14"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/2686/", "뽑히는 개발자 포트폴리오는 어떻게 만드나요?", "개발자 포트폴리오 조언 글입니다.", "2024-07-24"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/2648/", "뽑히는 개발자 이력서는 어떻게 만드나요?", "신입 개발자 이력서 조언 글입니다.", "2024-06-27"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/2230/", "개발자 커리어 로드맵 작성 시 고려할 점", "개발자 커리어 설계 글입니다.", "2023-09-15"));

        seeds.add(new SeedArticle("support", "기술개발 지원", "K-Startup", "https://www.k-startup.go.kr/web/contents/webNOTI300.do?schM=view&schStr=&pbancSn=171590", "창업지원사업 공고 예시", "창업 및 기술개발 지원 공고 예시입니다.", "2026-04-01"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "국토교통 기업지원허브", "https://hub.kaia.re.kr/organSupportHub.do/view?orgKind=ORG_KIND_2&rltdId=78822", "AICT 표준기술 개발 지원사업 참여기업 모집공고", "기술개발 지원 공고 페이지입니다.", "2026-04-08"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "오늘지원", "https://todayjiwon.co.kr/programs/993710d7-283e-4f49-8413-3f319944a89d", "2026년 XR 기술개발 지원사업 참여기업 모집", "지원사업 요약 페이지입니다.", "2026-04-07"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "벤처스퀘어", "https://www.venturesquare.net/announcement/1067580", "2026년 XR 기술개발 지원사업 참여기업 모집", "기술개발 지원사업 소개 기사입니다.", "2026-03-25"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "CISTEP", "https://www.cistep.re.kr/zboard/read.do?lmCode=notice&pd_pkid=12982", "AICT 표준기술 개발 지원사업 모집공고", "표준기술 개발 지원사업 공고입니다.", "2026-03-16"));

        return seeds;
    }

    private String meta(Document document, String attrKey, String attrValue) {
        Element element = document.selectFirst("meta[" + attrKey + "=" + attrValue + "]");
        return element != null ? element.attr("content") : "";
    }

    private String absMeta(Document document, String attrKey, String attrValue) {
        Element element = document.selectFirst("meta[" + attrKey + "=" + attrValue + "]");
        if (element == null) {
            return "";
        }

        String content = element.attr("content");
        if (content == null || content.isBlank()) {
            return "";
        }

        if (content.startsWith("http://") || content.startsWith("https://")) {
            return content;
        }

        try {
            return URI.create(document.baseUri()).resolve(content).toString();
        } catch (Exception ignored) {
            return content;
        }
    }

    private String firstImage(Document document) {
        Element image = document.selectFirst("article img[src], .article img[src], .article-view img[src], .view-content img[src], img[src]");
        if (image == null) {
            return "";
        }
        return image.absUrl("src");
    }

    private String timeValue(Document document) {
        Element time = document.selectFirst("time[datetime]");
        if (time != null) {
            return time.attr("datetime");
        }
        return "";
    }

    private String firstMeaningfulParagraph(Document document) {
        for (Element paragraph : document.select("article p, .article p, .article-view p, .view-content p, p")) {
            String text = paragraph.text();
            if (text != null && text.trim().length() >= 40) {
                return text.trim();
            }
        }
        return "";
    }

    private String safeText(String value) {
        return value == null ? "" : value.replaceAll("\s+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String cleaned = safeText(value);
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength - 1) + "…";
    }

    private String cleanPublishedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        try {
            return OffsetDateTime.parse(raw).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
        }

        Matcher matcher = DATE_PATTERN.matcher(raw);
        if (matcher.find()) {
            return String.format("%s-%02d-%02d", matcher.group(1), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        }

        return safeText(raw);
    }

    private record SourceFeed(
            String categoryKey,
            String categoryLabel,
            String sourceName,
            String feedUrl,
            List<String> urlMarkers,
            List<String> keywordHints,
            int limit
    ) {
    }

    private record SeedArticle(
            String categoryKey,
            String categoryLabel,
            String sourceName,
            String articleUrl,
            String fallbackTitle,
            String fallbackSummary,
            String fallbackPublishedAt
    ) {
    }

    private record FeedCandidate(String title, String url, int score) {
    }

    private record FetchOutcome(CrawledArticle article, boolean success) {
    }

    private record CacheEntry(Instant cachedAt, ArticleFetchResult result) {
    }

    public record ArticleFetchResult(
            List<CrawledArticle> articles,
            Map<String, Long> categoryCounts,
            boolean failed,
            String message
    ) {
    }
}
