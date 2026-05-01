package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.dto.CrawledArticle;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.ArticleCrawlerService;
import kr.ac.dankook.campuson.service.ArticleCrawlerService.ArticleFetchResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Controller
public class HomeController {

    private final MemberRepository memberRepository;
    private final ArticleCrawlerService articleCrawlerService;

    public HomeController(MemberRepository memberRepository, ArticleCrawlerService articleCrawlerService) {
        this.memberRepository = memberRepository;
        this.articleCrawlerService = articleCrawlerService;
    }

    @GetMapping("/home")
    public String home(Model model, Principal principal) {
        if (principal != null) {
            Member member = memberRepository.findByStudentId(principal.getName());
            model.addAttribute("member", member);
        }

        ArticleFetchResult result = articleCrawlerService.fetchArticleFeed();
        List<CrawledArticle> articles = result.articles();

        model.addAttribute("activeMenu", "home");
        model.addAttribute("homeSlideArticles", selectTopicArticles(articles));
        model.addAttribute("homeScrapArticles", selectTopicArticles(articles));
        model.addAttribute("articleFetchFailed", result.failed());
        model.addAttribute("articleFetchMessage", result.message());

        return "home/index";
    }

    private List<CrawledArticle> selectTopicArticles(List<CrawledArticle> articles) {
        List<CrawledArticle> selected = new ArrayList<>();
        Set<String> usedUrls = new LinkedHashSet<>();

        addFirstByCategory(selected, usedUrls, articles, "news", "it", "school");
        addFirstByCategory(selected, usedUrls, articles, "hackathon");
        addFirstByCategory(selected, usedUrls, articles, "contest");

        for (CrawledArticle article : articles) {
            if (selected.size() >= 3) {
                break;
            }
            if (usedUrls.add(article.articleUrl())) {
                selected.add(article);
            }
        }

        return selected;
    }

    private void addFirstByCategory(List<CrawledArticle> selected,
                                    Set<String> usedUrls,
                                    List<CrawledArticle> articles,
                                    String... categoryKeys) {
        if (selected.size() >= 3) {
            return;
        }

        for (String categoryKey : categoryKeys) {
            for (CrawledArticle article : articles) {
                if (categoryKey.equals(article.categoryKey()) && usedUrls.add(article.articleUrl())) {
                    selected.add(article);
                    return;
                }
            }
        }
    }
}
