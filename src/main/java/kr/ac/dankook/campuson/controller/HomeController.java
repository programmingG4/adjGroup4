package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.dto.CrawledArticle;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.ArticleCrawlerService;
import kr.ac.dankook.campuson.service.ArticleCrawlerService.ArticleFetchResult;
import kr.ac.dankook.campuson.service.ChatService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class HomeController {

    private final MemberRepository memberRepository;
    private final ArticleCrawlerService articleCrawlerService;
    private final ChatService chatService;

    public HomeController(MemberRepository memberRepository, ArticleCrawlerService articleCrawlerService, ChatService chatService) {
        this.memberRepository = memberRepository;
        this.articleCrawlerService = articleCrawlerService;
        this.chatService = chatService;
    }

    @GetMapping("/home")
    public String home(Model model, Principal principal) {
        Member member = null;
        if (principal != null) {
            member = memberRepository.findByStudentId(principal.getName());
            model.addAttribute("member", member);
        }

        ArticleFetchResult articleResult = fetchArticleFeedWithWarmupWait();
        List<CrawledArticle> articles = articleResult.articles();
        boolean fetchFailed = articleResult.failed();
        String fetchMessage = articleResult.message();

        List<ChatRoom> homeChatRooms;
        if (member != null && member.getGrade() > 0) {
            homeChatRooms = chatService.getPublicRoomsForMember(member.getGrade());
        } else {
            homeChatRooms = chatService.getPublicRoomsForMember(0);
        }
        List<Long> chatRoomIds = homeChatRooms.stream().map(ChatRoom::getId).toList();
        Map<Long, ChatMessage> homeChatLastMessages = chatService.getLastMessages(chatRoomIds);

        model.addAttribute("activeMenu", "home");
        model.addAttribute("homeItArticles", selectRandomItArticlesFromFirstThreePages(articles));
        model.addAttribute("articleFetchFailed", fetchFailed);
        model.addAttribute("articleFetchMessage", fetchMessage);
        model.addAttribute("homeChatRooms", homeChatRooms);
        model.addAttribute("homeChatLastMessages", homeChatLastMessages);

        return "home/index";
    }

    private ArticleFetchResult fetchArticleFeedWithWarmupWait() {
        ArticleFetchResult result = new ArticleFetchResult(List.of(), Map.of(), false, null);

        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                result = articleCrawlerService.fetchArticleFeed();
                if (result.articles() != null && !result.articles().isEmpty()) {
                    return result;
                }
                Thread.sleep(350);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ArticleFetchResult(List.of(), Map.of(), true, "기사를 불러오는 중 요청이 중단되었습니다.");
            } catch (Exception e) {
                return new ArticleFetchResult(List.of(), Map.of(), true, "기사를 불러올 수 없습니다.");
            }
        }

        return result;
    }

    private List<CrawledArticle> selectRandomItArticlesFromFirstThreePages(List<CrawledArticle> articles) {
        final int articlePageSize = 5;
        final int targetPageCount = 3;
        final int pickCount = 3;

        List<CrawledArticle> candidates = new ArrayList<>(articles.stream()
                .filter(this::isItArticle)
                .limit(articlePageSize * targetPageCount)
                .toList());

        if (candidates.isEmpty()) {
            candidates.addAll(fallbackItArticles());
        }

        Collections.shuffle(candidates);

        return candidates.stream()
                .limit(pickCount)
                .toList();
    }

    private boolean isItArticle(CrawledArticle article) {
        return article != null
                && ("it".equals(article.categoryKey()) || "최신 IT정보".equals(article.categoryLabel()));
    }

    private List<CrawledArticle> fallbackItArticles() {
        return List.of(
                new CrawledArticle("it", "최신 IT정보", "요즘IT", "2025년 회고와 2026년 개발 트렌드 전망", "", "", "", "2025-12-10"),
                new CrawledArticle("it", "최신 IT정보", "요즘IT", "2026년 프론트엔드 트렌드 총정리", "", "", "", "2025-12-24"),
                new CrawledArticle("it", "최신 IT정보", "요즘IT", "사랑받는 AI의 비밀", "", "", "", "2026-03-05"),
                new CrawledArticle("it", "최신 IT정보", "요즘IT", "리드 개발자 마인드셋 기사", "", "", "", "2026-04-10"),
                new CrawledArticle("it", "최신 IT정보", "요즘IT", "AI 검색 서비스 관련 기사", "", "", "", "2026-03-26"),
                new CrawledArticle("it", "최신 IT정보", "요즘IT", "개발자 생산성 관련 기사", "", "", "", "2026-03-17"),
                new CrawledArticle("it", "최신 IT정보", "요즘IT", "AI보다 나은 취향에 대한 기사", "", "", "", "2026-03-25"),
                new CrawledArticle("it", "최신 IT정보", "요즘IT", "2026년 UI·UX 트렌드 기사", "", "", "", "2025-11-11")
        );
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
