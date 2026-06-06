package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.ArticleCrawlerService;
import kr.ac.dankook.campuson.service.ArticleCrawlerService.ArticleFetchResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.time.Duration;

@Controller
public class ArticleController {

    private final ArticleCrawlerService articleCrawlerService;
    private final MemberRepository memberRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public ArticleController(ArticleCrawlerService articleCrawlerService, MemberRepository memberRepository) {
        this.articleCrawlerService = articleCrawlerService;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/articles")
    public String articles(
            Model model,
            Principal principal,
            @RequestParam(defaultValue = "false") boolean ready
    ) {
        if (!ready) {
            return "article/loading";
        }

        if (principal != null) {
            Member member = memberRepository.findByStudentId(principal.getName());
            model.addAttribute("member", member);
        }

        model.addAttribute("activeMenu", "articles");
        model.addAttribute("articles", java.util.Collections.emptyList());
        model.addAttribute("categoryCounts", java.util.Collections.singletonMap("all", 0L));
        model.addAttribute("fetchFailed", false);
        model.addAttribute("fetchMessage", null);
        model.addAttribute("currentPage", 1);
        model.addAttribute("hasMore", true);

        return "article/list";
    }

    @GetMapping("/articles/initial")
    public ResponseEntity<ArticleFetchResult> initialArticles(
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        ArticleFetchResult result = articleCrawlerService.fetchInitialCategoryFeed(refresh);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/articles/page")
    public ResponseEntity<ArticleFetchResult> articlePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        ArticleFetchResult result = articleCrawlerService.fetchArticlePage(page, category, refresh);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/articles/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam(required = false) String url) throws IOException, InterruptedException {
        if (url == null || url.isBlank()) {
            return placeholderImage();
        }

        if (url.startsWith("/images/")) {
            return localImage(url);
        }

        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return placeholderImage();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 CampusONBot/1.0")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null && response.body().length > 0) {
                String contentType = response.headers().firstValue("content-type").orElse("image/jpeg");
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(3)))
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .body(response.body());
            }
        } catch (Exception ignored) {
        }

        return placeholderImage();
    }

    private ResponseEntity<byte[]> localImage(String url) throws IOException {
        String safePath = url.replaceFirst("^/+", "");
        if (safePath.contains("..")) {
            return placeholderImage();
        }

        ClassPathResource resource = new ClassPathResource("static/" + safePath);
        if (!resource.exists()) {
            return placeholderImage();
        }

        byte[] body = StreamUtils.copyToByteArray(resource.getInputStream());
        String lowerPath = safePath.toLowerCase();
        MediaType mediaType = lowerPath.endsWith(".png")
                ? MediaType.IMAGE_PNG
                : lowerPath.endsWith(".svg")
                ? MediaType.parseMediaType("image/svg+xml")
                : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .body(body);
    }

    private ResponseEntity<byte[]> placeholderImage() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/images/article-placeholder.svg");
        byte[] body = StreamUtils.copyToByteArray(resource.getInputStream());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .body(body);
    }
}
