package kr.ac.dankook.campuson.dto;

public record CrawledArticle(
        String categoryKey,
        String categoryLabel,
        String sourceName,
        String title,
        String articleUrl,
        String thumbnailUrl,
        String summary,
        String publishedAt
) {
}
