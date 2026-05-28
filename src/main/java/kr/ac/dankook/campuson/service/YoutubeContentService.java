package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.dto.YoutubeRecommendation;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class YoutubeContentService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final String OOTB_CHANNEL_URL = "https://www.youtube.com/@ootbSTUDIO";
    private static final String YOUTUBE_SEARCH_URL = "https://www.youtube.com/results?search_query=%EC%A0%84%EA%B3%BC%EC%9E%90+%EB%8C%80%ED%95%99";
    private static final String FALLBACK_CHANNEL_ID = "";

    private volatile CacheEntry cacheEntry;

    public List<YoutubeRecommendation> fetchJeongwajaVideos() {
        CacheEntry localCache = cacheEntry;
        if (localCache != null && Duration.between(localCache.cachedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return localCache.videos();
        }

        List<YoutubeRecommendation> videos = crawlJeongwajaVideos();
        cacheEntry = new CacheEntry(Instant.now(), videos);
        return videos;
    }

    private List<YoutubeRecommendation> crawlJeongwajaVideos() {
        List<YoutubeRecommendation> videos = new ArrayList<>();

        try {
            String channelId = resolveChannelId();
            if (!channelId.isBlank()) {
                videos.addAll(fetchFromRss(channelId));
            }
        } catch (Exception ignored) {
            // fallback below
        }

        if (videos.isEmpty()) {
            videos.addAll(fetchFromSearchPage());
        }

        if (videos.isEmpty()) {
            videos.addAll(fallbackVideos());
        }

        return videos.stream().limit(3).toList();
    }

    private String resolveChannelId() {
        if (!FALLBACK_CHANNEL_ID.isBlank()) {
            return FALLBACK_CHANNEL_ID;
        }

        try {
            Document document = Jsoup.connect(OOTB_CHANNEL_URL)
                    .userAgent("Mozilla/5.0 CampusONBot/1.0")
                    .timeout(10000)
                    .get();
            String html = document.html();
            Matcher matcher = Pattern.compile("\\\"channelId\\\":\\\"(UC[^\\\"]+)\\\"").matcher(html);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
            return "";
        }

        return "";
    }

    private List<YoutubeRecommendation> fetchFromRss(String channelId) throws Exception {
        String rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
        Document document = Jsoup.connect(rssUrl)
                .userAgent("Mozilla/5.0 CampusONBot/1.0")
                .timeout(10000)
                .ignoreContentType(true)
                .get();

        List<YoutubeRecommendation> videos = new ArrayList<>();
        for (Element entry : document.select("entry")) {
            String title = text(entry, "title");
            if (!isJeongwajaTitle(title)) {
                continue;
            }

            String url = text(entry, "link[href]");
            if (url.isBlank()) {
                Element link = entry.selectFirst("link[href]");
                url = link != null ? link.attr("href") : "";
            }

            String videoId = text(entry, "yt|videoId");
            String thumbnail = videoId.isBlank() ? "" : "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
            String embedUrl = videoId.isBlank() ? toEmbedUrl(url) : "https://www.youtube.com/embed/" + videoId;
            if (!embedUrl.isBlank()) {
                videos.add(new YoutubeRecommendation(title, thumbnail, embedUrl, "전과자"));
            }

            if (videos.size() >= 3) {
                break;
            }
        }
        return videos;
    }

    private List<YoutubeRecommendation> fetchFromSearchPage() {
        List<YoutubeRecommendation> videos = new ArrayList<>();
        try {
            Document document = Jsoup.connect(YOUTUBE_SEARCH_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 CampusONBot/1.0")
                    .timeout(10000)
                    .get();
            String html = document.html();
            Matcher matcher = Pattern.compile("\\\"videoId\\\":\\\"([a-zA-Z0-9_-]{6,})\\\"").matcher(html);
            while (matcher.find() && videos.size() < 3) {
                String videoId = matcher.group(1);
                if (videos.stream().anyMatch(video -> video.url().contains(videoId))) {
                    continue;
                }
                videos.add(new YoutubeRecommendation(
                        "대학 컨텐츠 추천 영상",
                        "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg",
                        "https://www.youtube.com/embed/" + videoId,
                        "대학 컨텐츠"
                ));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return videos;
    }

    private String text(Element parent, String selector) {
        Element element = parent.selectFirst(selector);
        if (element == null) {
            return "";
        }
        if (selector.contains("[href]")) {
            return element.attr("href");
        }
        return element.text().trim();
    }

    private boolean isJeongwajaTitle(String title) {
        if (title == null) {
            return false;
        }
        String normalized = title.replace(" ", "").toLowerCase();
        return normalized.contains("전과자") || normalized.contains("jeongwaja");
    }

    private String toEmbedUrl(String watchUrl) {
        if (watchUrl == null || watchUrl.isBlank()) {
            return "";
        }

        Matcher watchMatcher = Pattern.compile("[?&]v=([a-zA-Z0-9_-]{6,})").matcher(watchUrl);
        if (watchMatcher.find()) {
            return "https://www.youtube.com/embed/" + watchMatcher.group(1);
        }

        Matcher shortMatcher = Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{6,})").matcher(watchUrl);
        if (shortMatcher.find()) {
            return "https://www.youtube.com/embed/" + shortMatcher.group(1);
        }

        return "";
    }

    private List<YoutubeRecommendation> fallbackVideos() {
        return List.of(
                new YoutubeRecommendation(
                        "대학 컨텐츠 추천",
                        "",
                        "https://www.youtube.com/embed?listType=search&list=%EC%A0%84%EA%B3%BC%EC%9E%90%20%EB%8C%80%ED%95%99",
                        "대학 컨텐츠"
                )
        );
    }

    private record CacheEntry(Instant cachedAt, List<YoutubeRecommendation> videos) {
    }
}
