package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.dto.CrawledArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ArticleCrawlerService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final String DKU_NEWS_IMAGE = "/images/dku-news.svg";

    private volatile CacheEntry cacheEntry;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    @PostConstruct
    public void warmUpCache() {
        startBackgroundRefresh();
    }

    public ArticleFetchResult fetchArticleFeed() {
        CacheEntry localCache = cacheEntry;
        if (localCache != null && Duration.between(localCache.cachedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return localCache.result();
        }

        // 캐시가 없거나 만료됐으면 백그라운드에서 갱신 시작
        startBackgroundRefresh();

        // 갱신 완료까지 기다리지 않고 현재 캐시(또는 빈 결과) 즉시 반환
        if (localCache != null) {
            return localCache.result();
        }
        return new ArticleFetchResult(List.of(), Map.of(), false, null);
    }

    private void startBackgroundRefresh() {
        if (refreshing.compareAndSet(false, true)) {
            Thread.ofVirtual().start(() -> {
                try {
                    ArticleFetchResult result = crawlSeedArticles();
                    cacheEntry = new CacheEntry(Instant.now(), result);
                } finally {
                    refreshing.set(false);
                }
            });
        }
    }

    private ArticleFetchResult crawlSeedArticles() {
        List<SeedArticle> seeds = seedArticles();
        Map<String, CrawledArticle> unique = new LinkedHashMap<>();
        int failedCount = 0;

        for (SourcePage sourcePage : sourcePages()) {
            SourceCrawlOutcome outcome = crawlSourcePage(sourcePage);
            for (CrawledArticle article : outcome.articles()) {
                unique.putIfAbsent(article.articleUrl(), article);
            }
            if (!outcome.success()) {
                failedCount++;
            }
        }

        for (SeedArticle seed : seeds) {
            FetchOutcome outcome = fetchArticle(seed);
            if (outcome.article() != null && isArticleAllowedForFeed(outcome.article())) {
                unique.putIfAbsent(outcome.article().articleUrl(), outcome.article());
            }
            if (!outcome.success()) {
                failedCount++;
            }
        }

        List<CrawledArticle> articles = unique.values().stream()
                .filter(ArticleCrawlerService::isArticleAllowedForFeed)
                .sorted(Comparator.comparing(ArticleCrawlerService::publicationSortKey).reversed())
                .toList();

        Map<String, Long> counts = categoryCounts(articles);

        boolean failed = false;
        String message = "";

        if (articles.isEmpty()) {
            failed = true;
            message = "네트워크 지연 또는 타임아웃으로 기사를 불러오는 데 실패했습니다. 잠시 후 다시 시도해주세요.";
        } else if (failedCount >= 8) {
            failed = true;
            message = "일부 기사 수집이 지연되거나 실패했습니다. 현재 불러온 기사만 먼저 표시합니다.";
        }

        return new ArticleFetchResult(articles, counts, failed, message);
    }

    private Map<String, Long> categoryCounts(List<CrawledArticle> articles) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", (long) articles.size());
        for (CrawledArticle article : articles) {
            counts.merge(article.categoryKey(), 1L, Long::sum);
        }
        return counts;
    }

    private SourceCrawlOutcome crawlSourcePage(SourcePage sourcePage) {
        List<CrawledArticle> articles = new ArrayList<>();
        Set<String> visitedUrls = new LinkedHashSet<>();

        try {
            Document document = Jsoup.connect(sourcePage.sourceUrl())
                    .userAgent("Mozilla/5.0 CampusONBot/1.0")
                    .timeout(10000)
                    .get();

            for (Element link : document.select("a[href]")) {
                if (articles.size() >= sourcePage.maxItems()) {
                    break;
                }

                String articleUrl = normalizeUrl(link.absUrl("href"));
                String linkText = safeText(link.text());

                if (!isUsefulArticleLink(sourcePage, articleUrl, linkText) || !visitedUrls.add(articleUrl)) {
                    continue;
                }

                SeedArticle seed = new SeedArticle(
                        sourcePage.categoryKey(),
                        sourcePage.categoryLabel(),
                        sourcePage.sourceName(),
                        articleUrl,
                        firstNonBlank(linkText, sourcePage.fallbackTitle()),
                        sourcePage.fallbackSummary(),
                        "상시 업데이트",
                        sourcePage.fallbackThumbnail()
                );

                FetchOutcome outcome = fetchArticle(seed);
                if (outcome.article() != null) {
                    articles.add(outcome.article());
                }
            }

            return new SourceCrawlOutcome(articles, !articles.isEmpty());
        } catch (Exception ignored) {
            return new SourceCrawlOutcome(articles, false);
        }
    }

    private boolean isUsefulArticleLink(SourcePage sourcePage, String articleUrl, String linkText) {
        if (articleUrl == null || articleUrl.isBlank()) {
            return false;
        }
        if (!(articleUrl.startsWith("http://") || articleUrl.startsWith("https://"))) {
            return false;
        }
        if (linkText == null || linkText.length() < 4) {
            return false;
        }
        if (articleUrl.contains("javascript:") || articleUrl.contains("mailto:")) {
            return false;
        }

        try {
            URI source = URI.create(sourcePage.sourceUrl());
            URI target = URI.create(articleUrl);
            if (source.getHost() != null && target.getHost() != null && !source.getHost().equalsIgnoreCase(target.getHost())) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }

        String lowerText = linkText.toLowerCase();
        String lowerUrl = articleUrl.toLowerCase();
        if (lowerText.contains("로그인") || lowerText.contains("회원가입") || lowerText.contains("검색") || lowerText.contains("이전") || lowerText.contains("다음")
                || lowerText.contains("사업단소개") || lowerText.contains("참여학과") || lowerText.contains("사이트맵") || lowerText.contains("개인정보처리방침")) {
            return false;
        }

        if (lowerUrl.contains("swcu.dankook.ac.kr")) {
            return linkText.contains("공지")
                    || linkText.contains("모집")
                    || linkText.contains("안내")
                    || linkText.contains("대회")
                    || linkText.contains("행사")
                    || linkText.contains("공모")
                    || linkText.contains("채용")
                    || linkText.contains("창업")
                    || linkText.contains("AI")
                    || linkText.contains("SW")
                    || linkText.contains("소프트웨어");
        }

        return lowerUrl.contains("view")
                || lowerUrl.contains("contest")
                || lowerUrl.contains("find")
                || lowerUrl.contains("career")
                || lowerUrl.contains("job")
                || lowerUrl.contains("recruit")
                || lowerUrl.contains("activity")
                || lowerUrl.contains("web/ace")
                || lowerText.contains("공모")
                || lowerText.contains("대회")
                || lowerText.contains("해커톤")
                || lowerText.contains("채용")
                || lowerText.contains("취업")
                || lowerText.contains("인턴")
                || lowerText.contains("모집");
    }

    private static boolean isArticleAllowedForFeed(CrawledArticle article) {
        if (article == null) {
            return false;
        }

        if (isListingPageArticle(article)) {
            return false;
        }

        if (("career".equals(article.categoryKey()) || "취창업".equals(article.categoryLabel()))
                && !hasUsableRemoteThumbnail(article.thumbnailUrl())) {
            return false;
        }

        return true;
    }

    private static boolean isListingPageArticle(CrawledArticle article) {
        String url = article.articleUrl() == null ? "" : article.articleUrl().toLowerCase();
        String title = article.title() == null ? "" : article.title().trim();

        if (url.isBlank()) {
            return true;
        }

        if (url.contains("k-startup.go.kr/web/main/mainsection")
                || url.matches(".*k-startup\\.go\\.kr/web/?$")
                || url.contains("/list/")
                || url.contains("/contest/catefield")
                || url.contains("/events")
                || url.contains("/search?")
                || url.contains("/event/main")) {
            return true;
        }

        return title.contains("목록")
                || title.contains("리스트")
                || title.contains("검색")
                || title.contains("분야별")
                || title.contains("통합 정보")
                || title.contains("모아볼 수")
                || title.contains("확인할 수 있는 페이지");
    }

    private static boolean hasUsableRemoteThumbnail(String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return false;
        }

        String lower = thumbnailUrl.toLowerCase();
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return false;
        }

        return !lower.endsWith(".svg")
                && !lower.contains("article-placeholder")
                && !lower.contains("dku-news")
                && !lower.contains("blank")
                && !lower.contains("default")
                && !lower.contains("noimage")
                && !lower.contains("no-image");
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        int hashIndex = url.indexOf('#');
        return hashIndex >= 0 ? url.substring(0, hashIndex) : url;
    }

    private FetchOutcome fetchArticle(SeedArticle seed) {
        try {
            Document document = Jsoup.connect(seed.articleUrl())
                    .userAgent("Mozilla/5.0 CampusONBot/1.0")
                    .timeout(10000)
                    .get();

            String title = firstNonBlank(
                    meta(document, "property", "og:title"),
                    meta(document, "name", "twitter:title"),
                    document.title(),
                    seed.fallbackTitle()
            );

            String summary = trimToLength(firstNonBlank(
                    meta(document, "property", "og:description"),
                    meta(document, "name", "description"),
                    firstMeaningfulParagraph(document),
                    seed.fallbackSummary()
            ), 160);

            String image = firstNonBlank(
                    absMeta(document, "property", "og:image"),
                    absMeta(document, "name", "twitter:image"),
                    firstImage(document),
                    seed.fallbackThumbnail(),
                    ""
            );

            String publishedAt = firstNonBlank(
                    meta(document, "property", "article:published_time"),
                    meta(document, "name", "pubdate"),
                    timeValue(document),
                    seed.fallbackPublishedAt()
            );

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
                    seed.fallbackThumbnail(),
                    seed.fallbackSummary(),
                    seed.fallbackPublishedAt()
            );
            return new FetchOutcome(fallbackArticle, false);
        }
    }

    private static String publicationSortKey(CrawledArticle article) {
        if (article == null || article.publishedAt() == null || article.publishedAt().isBlank()) {
            return "0000-00-00";
        }
        String value = article.publishedAt().trim();
        java.util.regex.Matcher dateMatcher = java.util.regex.Pattern
                .compile("(\\d{4})[.\\-/년 ]+(\\d{1,2})[.\\-/월 ]+(\\d{1,2})")
                .matcher(value);
        if (dateMatcher.find()) {
            return String.format("%04d-%02d-%02d",
                    Integer.parseInt(dateMatcher.group(1)),
                    Integer.parseInt(dateMatcher.group(2)),
                    Integer.parseInt(dateMatcher.group(3)));
        }
        java.util.regex.Matcher compactMatcher = java.util.regex.Pattern
                .compile("(\\d{4})(\\d{2})(\\d{2})")
                .matcher(value);
        if (compactMatcher.find()) {
            return compactMatcher.group(1) + "-" + compactMatcher.group(2) + "-" + compactMatcher.group(3);
        }
        if (value.contains("상시")) {
            return "0001-00-00";
        }
        return value;
    }

    private List<SourcePage> sourcePages() {
        List<SourcePage> sources = new ArrayList<>();

        sources.add(new SourcePage(
                "contest",
                "공모전",
                "단국대학교 ACE사업단",
                "https://cms.dankook.ac.kr/web/ace/-39",
                "단국대학교 공모전·프로그램 공지",
                "단국대학교 홈페이지에서 제공하는 공모전, 비교과 프로그램, 학생 참여형 공지입니다.",
                DKU_NEWS_IMAGE,
                8
        ));

        sources.add(new SourcePage(
                "school",
                "학교 소식",
                "단국대학교 SW중심대학사업단 공지사항",
                "https://swcu.dankook.ac.kr/-5",
                "SW중심대학사업단 공지사항",
                "단국대학교 SW중심대학사업단의 공지사항과 학생 참여 안내를 수집합니다.",
                DKU_NEWS_IMAGE,
                8
        ));

        sources.add(new SourcePage(
                "contest",
                "공모전",
                "단국대학교 SW중심대학사업단 행사정보",
                "https://swcu.dankook.ac.kr/-2024-",
                "SW대회/행사 정보",
                "단국대학교 SW중심대학사업단의 SW대회, 행사, 경진대회 정보를 수집합니다.",
                DKU_NEWS_IMAGE,
                8
        ));

        sources.add(new SourcePage(
                "support",
                "기술개발 지원",
                "단국대학교 SW중심대학사업단 외부소식",
                "https://swcu.dankook.ac.kr/-24",
                "SW중심대학사업단 외부소식",
                "외부 교육, 기술 트렌드, 창업·지원사업 관련 소식을 수집합니다.",
                DKU_NEWS_IMAGE,
                8
        ));

        sources.add(new SourcePage(
                "contest",
                "공모전",
                "위비티",
                "https://www.wevity.com/?c=find&s=1&gub=1&cidx=22",
                "위비티 공모전 분야별 목록",
                "기획, 아이디어, 디자인, 개발 등 대학생이 참여하기 좋은 공모전 정보를 수집합니다.",
                "",
                8
        ));

        sources.add(new SourcePage(
                "contest",
                "공모전",
                "캠퍼스픽",
                "https://www.campuspick.com/contest",
                "캠퍼스픽 공모전 정보",
                "대학생 대상 공모전, 대외활동, 학생 참여형 프로그램 정보를 수집합니다.",
                "",
                8
        ));

        sources.add(new SourcePage(
                "career",
                "취창업",
                "캠퍼스픽",
                "https://www.campuspick.com/job",
                "캠퍼스픽 취업 정보",
                "대학생과 취업준비생을 위한 채용, 인턴, 취업 관련 정보를 수집합니다.",
                "",
                8
        ));

        sources.add(new SourcePage(
                "career",
                "취창업",
                "캠퍼스픽",
                "https://www.campuspick.com/",
                "캠퍼스픽 취업·공모전 통합 정보",
                "캠퍼스픽 메인에서 취업, 공모전, 대외활동 관련 최신 글을 추가로 탐색합니다.",
                "",
                6
        ));

        return sources;
    }

    private List<SeedArticle> seedArticles() {
        List<SeedArticle> seeds = new ArrayList<>();

        // 학교 소식
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=587218", "단국대학교 관련 대학 현장 이슈", "단국대학교 관련 대학 현장 기사입니다.", "2025-12-12"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=590468", "단국대 국제화역량 관련 기사", "단국대학교의 교육 국제화 관련 기사입니다.", "2026-03-04"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=590729", "단국대 고교학점제 협력 기사", "단국대학교의 지역 연계 교육 협력 기사입니다.", "2026-03-12"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=588278", "단국대 정시모집 경쟁률 기사", "단국대학교의 입시 관련 기사입니다.", "2025-12-31"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=589508", "단국대 창업교육 우수대학 기사", "단국대학교 창업교육 관련 기사입니다.", "2026-02-12"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=590840", "단국대 교사 임용시험 합격자 기사", "단국대학교 교원 양성 관련 기사입니다.", "2026-03-17"));
        seeds.add(new SeedArticle("school", "학교 소식", "한국대학신문", "https://news.unn.net/news/articleView.html?idxno=581221", "단국대학교 AI 거점 대학 관련 기사", "단국대학교 인공지능학과 및 AI 거점 대학 관련 기사입니다.", "2025-07-18"));

        // 뉴스
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=659117", "리벨리온-Arm-SKT 공동개발 기사", "AI 인프라와 반도체 관련 기사입니다.", "2026-04-10"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=658803", "AWS 관련 클라우드 기사", "클라우드와 AI 인프라 관련 기사입니다.", "2026-04-07"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=658906", "시스코 AI 인프라 기사", "AI 인프라 비용과 네트워크 기사입니다.", "2026-04-08"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=659127", "방위산업 IR 개최 기사", "첨단기술 분야 진출 관련 기사입니다.", "2026-04-10"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=658453", "AI 에이전트 관련 기사", "생성형 AI 서비스 흐름 기사입니다.", "2026-04-03"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=658569", "GPU 인프라 기사", "AI 연산 인프라 기사입니다.", "2026-04-04"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=650737", "AI 칩 업계 성장세 전망", "AI 칩 업계 성장세 관련 기사입니다.", "2026-01-01"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=656683", "엔비디아 GTC 2026 전략 기사", "엔비디아 AI 전략 관련 기사입니다.", "2026-03-14"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=657226", "엠게임 2026 비전 기사", "AI·웹3·헬스케어 관련 기사입니다.", "2026-03-20"));
        seeds.add(new SeedArticle("news", "뉴스", "블로터", "https://www.bloter.net/news/articleView.html?idxno=653745", "SKT 2026 통신·AI 기사", "통신과 AI 사업 관련 기사입니다.", "2026-02-09"));

        // 최신 IT 정보
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3494/", "2025년 회고와 2026년 개발 트렌드 전망", "개발 트렌드 전망 기사입니다.", "2025-12-10"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3519/", "2026년 프론트엔드 트렌드 총정리", "프론트엔드 트렌드 기사입니다.", "2025-12-24"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3639/", "사랑받는 AI의 비밀", "AI UX 설계 관련 기사입니다.", "2026-03-05"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3701/", "리드 개발자 마인드셋 기사", "개발자 성장 관점 기사입니다.", "2026-04-10"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3677/", "AI 검색 서비스 관련 기사", "검색과 AI 흐름 관련 기사입니다.", "2026-03-26"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3655/", "개발자 생산성 관련 기사", "실무 생산성 관련 기사입니다.", "2026-03-17"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3675/", "AI보다 나은 취향에 대한 기사", "AI 시대의 판단과 취향에 대한 기사입니다.", "2026-03-25"));
        seeds.add(new SeedArticle("it", "최신 IT정보", "요즘IT", "https://yozm.wishket.com/magazine/detail/3441/", "2026년 UI·UX 트렌드 기사", "UI·UX 트렌드 관련 기사입니다.", "2025-11-11"));

        // 취창업
        seeds.add(new SeedArticle("career", "취창업", "브런치", "https://brunch.co.kr/%40youngstone89/1", "비전공자 개발자 취업기", "개발자 취업 경험을 다룬 글입니다.", "2021-11-30"));
        seeds.add(new SeedArticle("career", "취창업", "브런치", "https://brunch.co.kr/%40likelion/130", "2025년 프론트엔드 개발자 취업 로드맵", "프론트엔드 취업 로드맵 글입니다.", "2025-03-20"));
        seeds.add(new SeedArticle("career", "취창업", "브런치", "https://brunch.co.kr/%40storyofaddie/7", "플랫폼 스타트업 첫 개발자 경험담", "현업 개발자 취업 경험담입니다.", "2026-03-29"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/3391/", "개발자 포트폴리오와 경력기술서 작성법", "포트폴리오 작성 전략 글입니다.", "2025-10-14"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/2686/", "뽑히는 개발자 포트폴리오는 어떻게 만드나요?", "개발자 포트폴리오 조언 글입니다.", "2024-07-24"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/2648/", "뽑히는 개발자 이력서는 어떻게 만드나요?", "신입 개발자 이력서 조언 글입니다.", "2024-06-27"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/2230/", "개발자 커리어 로드맵 작성 시 고려할 점", "개발자 커리어 설계 글입니다.", "2023-09-15"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/3694/", "AI로 프로젝트 10개 만든 개발자가 서류에서 떨어지는 이유", "최근 취업 시장 관련 기사입니다.", "2026-04-07"));
        seeds.add(new SeedArticle("career", "취창업", "브런치", "https://brunch.co.kr/%40career/1051", "개발자 취업 시장 변화 정리", "개발자 채용 트렌드 글입니다.", "2025-07-08"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/3373/", "프리랜서 개발자, 어떤 기술을 배워야 돈이 될까?", "프리랜서 개발자 커리어 전략 글입니다.", "2025-09-30"));
        seeds.add(new SeedArticle("career", "취창업", "요즘IT", "https://yozm.wishket.com/magazine/detail/3471/", "챗GPT 보고 사표 쓴 비전공자, IT 커뮤니케이터가 되다", "커리어 전환 인터뷰 기사입니다.", "2025-11-27"));

        // 기술개발 지원
        seeds.add(new SeedArticle("support", "기술개발 지원", "한국전자정보통신산업진흥회", "https://www.gokea.org/core/?cid=11&role=view&uid=52487", "2026년 XR 기술개발 지원사업 신청 안내", "XR 기술개발 지원사업 안내입니다.", "2026-03-27"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "CISTEP", "https://www.cistep.re.kr/zboard/read.do?lmCode=notice&pd_pkid=12982", "AICT 표준기술 개발 지원사업 모집공고", "표준기술 개발 지원사업 공고입니다.", "2026-03-16"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "국토교통 기업지원허브", "https://hub.kaia.re.kr/organSupportHub.do/view?orgKind=ORG_KIND_2&rltdId=78822", "AICT 표준기술 개발 지원사업 참여기업 모집공고", "기술개발 지원 공고 페이지입니다.", "2026-04-08"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "국토교통 기업지원허브", "https://hub.kaia.re.kr/organSupportHub.do/view?orgKind=ORG_KIND_2&rltdId=77207", "XR 기술개발 지원사업 참여기업 모집", "XR 기술개발 지원 공고 페이지입니다.", "2026-04-02"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "벤처스퀘어", "https://www.venturesquare.net/announcement/1067580", "2026년 XR 기술개발 지원사업 참여기업 모집", "기술개발 지원사업 소개 기사입니다.", "2026-03-25"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "오늘지원", "https://todayjiwon.co.kr/programs/993710d7-283e-4f49-8413-3f319944a89d", "2026년 XR 기술개발 지원사업 참여기업 모집", "지원사업 요약 페이지입니다.", "2026-04-07"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "K-Startup", "https://www.k-startup.go.kr/web/contents/webNOTI300.do?schM=view&schStr=&pbancSn=171590", "창업지원사업 공고 예시", "창업 및 기술개발 지원 공고 예시입니다.", "2026-04-01"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "K-Startup", "https://www.k-startup.go.kr/web/main/mainSection0.do", "K-Startup 신규 사업 공고 목록", "최신 창업 및 기술개발 지원 공고를 모아볼 수 있는 페이지입니다.", "2026-04-10"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "K-Startup", "https://www.k-startup.go.kr/web/main/mainSectionChNaviList.do", "K-Startup 모집 중 사업공고 목록", "모집중인 기술개발 및 창업지원 사업 목록입니다.", "2026-04-10"));
        seeds.add(new SeedArticle("support", "기술개발 지원", "K-Startup", "https://www.k-startup.go.kr/web", "2026년 창업지원사업 통합공고", "창업지원사업 및 R&D 지원 정보를 확인할 수 있습니다.", "2026-04-10"));


        // 공모전
        seeds.add(new SeedArticle("contest", "공모전", "단국대학교 ACE사업단", "https://cms.dankook.ac.kr/web/ace/-39", "단국대학교 공모전·프로그램 공지", "단국대학교 홈페이지에서 제공하는 공모전, 비교과 프로그램, 학생 참여형 공지입니다.", "상시 업데이트", DKU_NEWS_IMAGE));
        seeds.add(new SeedArticle("contest", "공모전", "위비티", "https://www.wevity.com/?c=find&s=1&gub=1&cidx=22", "위비티 공모전 분야별 목록", "기획, 아이디어, 디자인, 개발 등 대학생이 참여하기 좋은 공모전 정보를 확인할 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("contest", "공모전", "캠퍼스픽", "https://www.campuspick.com/contest", "캠퍼스픽 공모전 정보", "대학생 대상 공모전, 대외활동, 학생 참여형 프로그램 정보를 확인할 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("career", "취창업", "캠퍼스픽", "https://www.campuspick.com/job", "캠퍼스픽 취업 정보", "대학생과 취업준비생을 위한 채용, 인턴, 취업 관련 정보를 확인할 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("contest", "공모전", "링커리어", "https://linkareer.com/list/contest", "링커리어 공모전 목록", "대학생과 취업준비생이 확인하기 좋은 공모전 목록입니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("contest", "공모전", "위비티", "https://www.wevity.com/?c=find&s=1&gbn=viewok", "위비티 공모전 정보", "기획, 디자인, 개발, 마케팅 등 다양한 공모전 정보를 확인할 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("contest", "공모전", "올콘", "https://www.all-con.co.kr/list/contest", "올콘 공모전 정보", "대학생 대상 공모전과 대외활동 정보를 모아볼 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("contest", "공모전", "캠퍼스픽", "https://www.campuspick.com/contest", "캠퍼스픽 공모전", "학생 참여형 공모전과 대외활동 정보를 확인할 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("contest", "공모전", "씽굿", "https://www.thinkcontest.com/Contest/CateField.html", "씽굿 공모전 분야별 목록", "분야별 공모전과 대외활동 정보를 확인할 수 있는 목록입니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("contest", "공모전", "대티즌", "https://www.detizen.com/contest", "대티즌 공모전 목록", "대학생과 청년 대상 공모전 정보를 확인할 수 있습니다.", "상시 업데이트"));

        // 해커톤
        seeds.add(new SeedArticle("hackathon", "해커톤", "데브이벤트", "https://dev-event.vercel.app/events", "개발자 행사와 해커톤 목록", "개발자 컨퍼런스, 세미나, 해커톤 정보를 확인할 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("hackathon", "해커톤", "온오프믹스", "https://www.onoffmix.com/event/main?s=%ED%95%B4%EC%BB%A4%ED%86%A4", "온오프믹스 해커톤 검색", "해커톤, 개발 행사, IT 세미나 정보를 확인할 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("hackathon", "해커톤", "이벤터스", "https://event-us.kr/search?keyword=%ED%95%B4%EC%BB%A4%ED%86%A4", "이벤터스 해커톤 검색", "오프라인·온라인 해커톤과 기술 행사를 확인할 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("hackathon", "해커톤", "구름", "https://goorm.io/", "구름 개발자 행사", "개발자 성장과 해커톤 관련 행사를 확인할 수 있는 플랫폼입니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("hackathon", "해커톤", "인프런", "https://www.inflearn.com/events", "인프런 개발자 이벤트", "개발자 이벤트, 세미나, 실무형 기술 행사 정보를 확인할 수 있습니다.", "상시 업데이트"));
        seeds.add(new SeedArticle("hackathon", "해커톤", "링커리어", "https://linkareer.com/list/activity", "링커리어 대외활동·해커톤", "청년과 대학생 대상 대외활동, 공모전, 해커톤 정보를 확인할 수 있습니다.", "상시 업데이트"));

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


    private boolean isDkuCampusSource(SeedArticle seed) {
        String url = seed.articleUrl() == null ? "" : seed.articleUrl().toLowerCase();
        String source = seed.sourceName() == null ? "" : seed.sourceName();
        return url.contains("cms.dankook.ac.kr") || source.contains("단국대학교");
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
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
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

    private record SourcePage(
            String categoryKey,
            String categoryLabel,
            String sourceName,
            String sourceUrl,
            String fallbackTitle,
            String fallbackSummary,
            String fallbackThumbnail,
            int maxItems
    ) {
    }

    private record SeedArticle(
            String categoryKey,
            String categoryLabel,
            String sourceName,
            String articleUrl,
            String fallbackTitle,
            String fallbackSummary,
            String fallbackPublishedAt,
            String fallbackThumbnail
    ) {
        private SeedArticle(
                String categoryKey,
                String categoryLabel,
                String sourceName,
                String articleUrl,
                String fallbackTitle,
                String fallbackSummary,
                String fallbackPublishedAt
        ) {
            this(categoryKey, categoryLabel, sourceName, articleUrl, fallbackTitle, fallbackSummary, fallbackPublishedAt, "");
        }
    }

    private record SourceCrawlOutcome(List<CrawledArticle> articles, boolean success) {
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
