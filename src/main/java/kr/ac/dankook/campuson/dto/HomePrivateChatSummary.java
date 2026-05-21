package kr.ac.dankook.campuson.dto;

import java.time.LocalDateTime;

public record HomePrivateChatSummary(
        Long roomId,
        String otherName,
        String otherStudentId,
        long unreadCount,
        LocalDateTime lastSentAt,
        String lastSentAtText
) {
}
