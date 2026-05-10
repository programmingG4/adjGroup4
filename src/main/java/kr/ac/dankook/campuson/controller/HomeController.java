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

        List<CrawledArticle> articles;
        boolean fetchFailed = false;
        String fetchMessage = null;
        try {
            ArticleFetchResult result = articleCrawlerService.fetchArticleFeed();
            articles = result.articles();
            fetchFailed = result.failed();
            fetchMessage = result.message();
        } catch (Exception e) {
            articles = List.of();
            fetchFailed = true;
            fetchMessage = "기사를 불러올 수 없습니다.";
        }

        List<ChatRoom> homeChatRooms;
        if (member != null && member.getGrade() > 0) {
            homeChatRooms = chatService.getPublicRoomsForMember(member.getGrade());
        } else {
            homeChatRooms = chatService.getPublicRoomsForMember(0);
        }
        List<Long> chatRoomIds = homeChatRooms.stream().map(ChatRoom::getId).toList();
        Map<Long, ChatMessage> homeChatLastMessages = chatService.getLastMessages(chatRoomIds);

        model.addAttribute("activeMenu", "home");
        model.addAttribute("homeSlideArticles", selectTopicArticles(articles));
        model.addAttribute("homeScrapArticles", selectTopicArticles(articles));
        model.addAttribute("articleFetchFailed", fetchFailed);
        model.addAttribute("articleFetchMessage", fetchMessage);
        model.addAttribute("homeChatRooms", homeChatRooms);
        model.addAttribute("homeChatLastMessages", homeChatLastMessages);

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
