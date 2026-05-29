package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.dto.ChatMessageDto;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.TalkBoard;
import kr.ac.dankook.campuson.entity.VoteItem;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Component
public class VoteEndScheduler {

    private final TalkBoardService talkBoardService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public VoteEndScheduler(TalkBoardService talkBoardService,
                            ChatService chatService,
                            SimpMessagingTemplate messagingTemplate) {
        this.talkBoardService = talkBoardService;
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void notifyEndedVotes() {
        List<TalkBoard> endedPosts = talkBoardService.findEndedVotesNotNotified();
        for (TalkBoard post : endedPosts) {
            sendVoteResultMessage(post);
            talkBoardService.markVoteResultNotified(post.getId());
        }
    }

    private void sendVoteResultMessage(TalkBoard post) {
        List<VoteItem> items = post.getVoteItems().stream()
                .sorted(Comparator.comparingInt(VoteItem::getVoteCount).reversed())
                .toList();

        int totalVotes = items.stream().mapToInt(VoteItem::getVoteCount).sum();

        StringBuilder sb = new StringBuilder();
        sb.append("📊 투표가 종료되었습니다.\n");
        sb.append("「 ").append(post.getTitle()).append(" 」\n\n");
        for (VoteItem item : items) {
            int count = item.getVoteCount();
            int pct = totalVotes > 0 ? (count * 100 / totalVotes) : 0;
            sb.append("• ").append(item.getItemText())
              .append("  ").append(count).append("표 (").append(pct).append("%)\n");
        }
        sb.append("\n총 ").append(totalVotes).append("명 참여");
        sb.append("\n[LINK]/talkboard/").append(post.getId());

        String content = sb.toString();

        chatService.findByRoomKey(post.getRoomKey()).ifPresent(room -> {
            // DB 저장
            ChatMessage msg = chatService.sendSystemMessage(room.getId(), content);
            if (msg == null) return;
            msg.setSenderName("투표 결과");
            ChatMessage saved = chatService.saveMessage(msg);

            // WebSocket 브로드캐스트
            ChatMessageDto dto = new ChatMessageDto();
            dto.setId(saved.getId());
            dto.setSender("SYSTEM");
            dto.setSenderName("투표 결과");
            dto.setContent(content);
            dto.setSentAt(saved.getSentAt().format(FORMATTER));
            messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), dto);
        });
    }
}
