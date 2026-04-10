package kr.ac.dankook.campuson.controller;

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
import java.time.Duration;

@Controller
public class ArticleController {

    private final ArticleCrawlerService articleCrawlerService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public ArticleController(ArticleCrawlerService articleCrawlerService) {
        this.articleCrawlerService = articleCrawlerService;
    }

    @GetMapping("/articles")
    public String articles(Model model) {
        ArticleFetchResult result = articleCrawlerService.fetchArticleFeed();

        model.addAttribute("activeMenu", "articles");
        model.addAttribute("articles", result.articles());
        model.addAttribute("categoryCounts", result.categoryCounts());
        model.addAttribute("fetchFailed", result.failed());
        model.addAttribute("fetchMessage", result.message());

        return "article/list";
    }

    @GetMapping("/articles/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam(required = false) String url) throws IOException, InterruptedException {
        if (url == null || url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
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

    private ResponseEntity<byte[]> placeholderImage() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/images/article-placeholder.svg");
        byte[] body = StreamUtils.copyToByteArray(resource.getInputStream());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .body(body);
    }
}
