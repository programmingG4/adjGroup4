package kr.ac.dankook.campuson.dto;

import java.util.List;

public record CrawledArticle(
        String categoryKey,
        String categoryLabel,
        String sourceName,
        String title,
        String articleUrl,
        String thumbnailUrl,
        String summary,
        String publishedAt,
        List<String> tags,
        String deadlineDday
) {
    public CrawledArticle(
            String categoryKey,
            String categoryLabel,
            String sourceName,
            String title,
            String articleUrl,
            String thumbnailUrl,
            String summary,
            String publishedAt
    ) {
        this(categoryKey, categoryLabel, sourceName, title, articleUrl, thumbnailUrl, summary, publishedAt, List.of(), "");
    }

    public CrawledArticle(
            String categoryKey,
            String categoryLabel,
            String sourceName,
            String title,
            String articleUrl,
            String thumbnailUrl,
            String summary,
            String publishedAt,
            List<String> tags
    ) {
        this(categoryKey, categoryLabel, sourceName, title, articleUrl, thumbnailUrl, summary, publishedAt, tags, "");
    }
}
