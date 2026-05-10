package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.dto.ChatMessageDto;
import kr.ac.dankook.campuson.dto.ReadNotificationDto;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.format.DateTimeFormatter;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final MemberRepository memberRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public ChatWebSocketController(ChatService chatService, MemberRepository memberRepository) {
        this.chatService = chatService;
        this.memberRepository = memberRepository;
    }

    @MessageMapping("/chat/{roomId}")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessageDto handleMessage(@DestinationVariable Long roomId,
                                        ChatMessageDto dto,
                                        Principal principal) {
        Member sender = memberRepository.findByStudentId(principal.getName());

        chatService.findById(roomId).ifPresent(room -> {
            ChatMessage message = new ChatMessage();
            message.setRoom(room);
            message.setSender(sender.getStudentId());
            message.setSenderName(sender.getName());
            message.setContent(dto.getContent());
            ChatMessage saved = chatService.saveMessage(message);
            dto.setId(saved.getId());
            dto.setSentAt(saved.getSentAt().format(FORMATTER));
            chatService.markAsRead(sender.getStudentId(), roomId);
        });

        dto.setSender(sender.getStudentId());
        dto.setSenderName(sender.getName());
        return dto;
    }

    @MessageMapping("/chat/{roomId}/read")
    @SendTo("/topic/chat/{roomId}/read")
    public ReadNotificationDto handleRead(@DestinationVariable Long roomId, Principal principal) {
        Member member = memberRepository.findByStudentId(principal.getName());
        chatService.markAsRead(member.getStudentId(), roomId);

        ReadNotificationDto dto = new ReadNotificationDto();
        dto.setStudentId(member.getStudentId());
        dto.setLastReadMessageId(chatService.getLastReadMessageId(member.getStudentId(), roomId));
        return dto;
    }
}
