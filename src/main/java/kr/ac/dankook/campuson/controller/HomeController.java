package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.repository.BoardRepository;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.ChatService;
import kr.ac.dankook.campuson.service.YoutubeContentService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class HomeController {

    private static final Duration LINK_THUMBNAIL_CACHE_TTL = Duration.ofHours(6);
    private static final int LINK_THUMBNAIL_TIMEOUT_MS = 1500;
    private static final Map<String, CachedThumbnail> LINK_THUMBNAIL_CACHE = new ConcurrentHashMap<>();

    private final MemberRepository memberRepository;
    private final BoardRepository boardRepository;
    private final ChatService chatService;
    private final YoutubeContentService youtubeContentService;

    public HomeController(MemberRepository memberRepository,
                          BoardRepository boardRepository,
                          ChatService chatService,
                          YoutubeContentService youtubeContentService) {
        this.memberRepository = memberRepository;
        this.boardRepository = boardRepository;
        this.chatService = chatService;
        this.youtubeContentService = youtubeContentService;
    }

    @GetMapping("/home")
    public String home(Model model, Principal principal) {
        Member member = null;
        if (principal != null) {
            member = memberRepository.findByStudentId(principal.getName());
            model.addAttribute("member", member);
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
        model.addAttribute("homeQuickLinks", homeQuickLinks());
        model.addAttribute("homeChatRooms", homeChatRooms);
        model.addAttribute("homeChatLastMessages", homeChatLastMessages);
        model.addAttribute("homePrivateChatSummaries", member == null
                ? List.of()
                : chatService.getUnreadPrivateChatSummaries(member.getStudentId()));
        model.addAttribute("homeLatestBoards", boardRepository.findTop5ByOrderByRegDateDesc());
        model.addAttribute("homeYoutubeVideos", youtubeContentService.fetchJeongwajaVideos());

        return "home/index";
    }

    @GetMapping("/home/link-thumbnail")
    public ResponseEntity<Void> linkThumbnail(@RequestParam(required = false) String target) {
        return findHomeQuickLink(target)
                .flatMap(this::resolveCachedThumbnail)
                .map(imageUrl -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(imageUrl))
                        .<Void>build())
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private List<HomeQuickLink> homeQuickLinks() {
        return List.of(
                new HomeQuickLink(
                        "portal",
                        "단국대 포털",
                        "학사·수업·성적·학교 행정 바로가기",
                        "https://portal.dankook.ac.kr/",
                        "https://www.dankook.ac.kr/web/kor",
                        "PORTAL",
                        "home-quick-portal"
                ),
                new HomeQuickLink(
                        "swcu",
                        "단국대 SW중심사업단",
                        "SW중심대학사업단 공지·교육·행사 바로가기",
                        "https://swcu.dankook.ac.kr/web/swcup",
                        "https://swcu.dankook.ac.kr/web/swcup",
                        "SWCU",
                        "home-quick-swcu"
                ),
                new HomeQuickLink(
                        "dev-event",
                        "Dev Event",
                        "개발자 행사·해커톤·컨퍼런스 모아보기",
                        "https://dev-event.vercel.app/events",
                        "https://dev-event.vercel.app/events",
                        "EVENT",
                        "home-quick-dev"
                ),
                new HomeQuickLink(
                        "yozm",
                        "요즘IT",
                        "개발·기획·디자인 실무 트렌드 아티클",
                        "https://yozm.wishket.com/magazine/",
                        "https://yozm.wishket.com/magazine/",
                        "YOZM",
                        "home-quick-yozm"
                ),
                new HomeQuickLink(
                        "brunch",
                        "브런치",
                        "IT·개발·커리어 인사이트를 읽는 글 플랫폼",
                        "https://brunch.co.kr/",
                        "https://brunch.co.kr/",
                        "BRUNCH",
                        "home-quick-brunch"
                ),
                new HomeQuickLink(
                        "bloter",
                        "블로터",
                        "국내 IT·테크 산업 뉴스 바로가기",
                        "https://www.bloter.net/",
                        "https://www.bloter.net/",
                        "BLOTER",
                        "home-quick-bloter"
                )
        );
    }

    private Optional<HomeQuickLink> findHomeQuickLink(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        String normalized = target.trim().toLowerCase(Locale.ROOT);
        return homeQuickLinks().stream()
                .filter(link -> link.key().equals(normalized))
                .findFirst();
    }

    private Optional<String> resolveCachedThumbnail(HomeQuickLink link) {
        CachedThumbnail cached = LINK_THUMBNAIL_CACHE.get(link.key());
        if (cached != null && !cached.isExpired()) {
            return Optional.ofNullable(cached.imageUrl());
        }

        Optional<String> imageUrl = fetchOpenGraphImage(link.thumbnailSourceUrl());
        LINK_THUMBNAIL_CACHE.put(link.key(), new CachedThumbnail(imageUrl.orElse(null), Instant.now().plus(LINK_THUMBNAIL_CACHE_TTL)));
        return imageUrl;
    }

    private Optional<String> fetchOpenGraphImage(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 CampusON/1.0")
                    .timeout(LINK_THUMBNAIL_TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            return firstPresent(
                    document.select("meta[property=og:image]").attr("content"),
                    document.select("meta[name=twitter:image]").attr("content"),
                    document.select("meta[property=twitter:image]").attr("content"),
                    firstMeaningfulImage(document)
            ).map(imageUrl -> document.baseUri().isBlank() ? imageUrl : URI.create(document.baseUri()).resolve(imageUrl).toString())
             .filter(this::isUsableThumbnailUrl);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String firstMeaningfulImage(Document document) {
        for (Element image : document.select("main img[src], .contents img[src], .visual img[src], .main img[src], img[src]")) {
            String src = image.absUrl("src");
            if (isUsableThumbnailUrl(src)) {
                return src;
            }
        }
        return null;
    }

    private boolean isUsableThumbnailUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        return (lower.startsWith("http://") || lower.startsWith("https://"))
                && !lower.endsWith(".svg")
                && !lower.contains("logo")
                && !lower.contains("favicon")
                && !lower.contains("icon");
    }

    private Optional<String> firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    public record HomeQuickLink(
            String key,
            String title,
            String description,
            String url,
            String thumbnailSourceUrl,
            String badge,
            String styleClass
    ) {
    }

    private record CachedThumbnail(String imageUrl, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
