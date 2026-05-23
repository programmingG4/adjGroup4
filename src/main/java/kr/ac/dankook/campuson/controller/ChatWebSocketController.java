package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.dto.ChatMessageDto;
import kr.ac.dankook.campuson.dto.ChatNotificationDto;
import kr.ac.dankook.campuson.dto.ReadNotificationDto;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.format.DateTimeFormatter;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final MemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public ChatWebSocketController(ChatService chatService, MemberRepository memberRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.memberRepository = memberRepository;
        this.messagingTemplate = messagingTemplate;
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
            message.setMediaUrl(dto.getMediaUrl());
            message.setMediaType(dto.getMediaType());
            message.setFileName(dto.getFileName());
            ChatMessage saved = chatService.saveMessage(message);
            dto.setId(saved.getId());
            dto.setSentAt(saved.getSentAt().format(FORMATTER));
            chatService.markAsRead(sender.getStudentId(), roomId);
            String notifContent = dto.getMediaType() != null ? "[" + (dto.getMediaType().equals("image") ? "사진" : "파일") + "]" : dto.getContent();
            sendNotification(room, sender, notifContent);
        });

        dto.setSender(sender.getStudentId());
        dto.setSenderName(sender.getName());
        return dto;
    }

    private void sendNotification(ChatRoom room, Member sender, String content) {
        String preview = content.length() > 35 ? content.substring(0, 35) + "…" : content;

        switch (room.getType()) {
            case GLOBAL -> {
                ChatNotificationDto notif = new ChatNotificationDto(room.getId(), room.getName(), sender.getName(), preview, false);
                messagingTemplate.convertAndSend("/topic/notifications/global", notif);
            }
            case GRADE -> {
                ChatNotificationDto notif = new ChatNotificationDto(room.getId(), room.getName(), sender.getName(), preview, false);
                String grade = room.getRoomKey().replace("grade_", "");
                messagingTemplate.convertAndSend("/topic/notifications/grade/" + grade, notif);
            }
            case PRIVATE -> {
                ChatNotificationDto notif = new ChatNotificationDto(room.getId(), sender.getName(), sender.getName(), preview, true);
                String[] keys = room.getRoomKey().split("_");
                String otherStudentId = keys[0].equals(sender.getStudentId()) ? keys[1] : keys[0];
                messagingTemplate.convertAndSendToUser(otherStudentId, "/queue/notifications", notif);
            }
            case GROUP -> {
                ChatNotificationDto notif = new ChatNotificationDto(room.getId(), room.getName(), sender.getName(), preview, false);
                chatService.getGroupRoomMemberIds(room.getId()).forEach(memberId -> {
                    if (!memberId.equals(sender.getStudentId())) {
                        messagingTemplate.convertAndSendToUser(memberId, "/queue/notifications", notif);
                    }
                });
            }
        }
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
