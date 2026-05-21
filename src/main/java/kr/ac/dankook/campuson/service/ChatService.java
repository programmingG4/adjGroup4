package kr.ac.dankook.campuson.service;

import jakarta.annotation.PostConstruct;
import kr.ac.dankook.campuson.dto.HomePrivateChatSummary;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.ChatReadStatus;
import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.repository.ChatMessageRepository;
import kr.ac.dankook.campuson.repository.ChatReadStatusRepository;
import kr.ac.dankook.campuson.repository.ChatRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;

    public ChatService(ChatRoomRepository chatRoomRepository,
                       ChatMessageRepository chatMessageRepository,
                       ChatReadStatusRepository chatReadStatusRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatReadStatusRepository = chatReadStatusRepository;
    }

    @PostConstruct
    public void initDefaultRooms() {
        createIfNotExists("전체 채팅방", "global", ChatRoom.RoomType.GLOBAL);
        createIfNotExists("1학년", "grade_1", ChatRoom.RoomType.GRADE);
        createIfNotExists("2학년", "grade_2", ChatRoom.RoomType.GRADE);
        createIfNotExists("3학년", "grade_3", ChatRoom.RoomType.GRADE);
        createIfNotExists("4학년", "grade_4", ChatRoom.RoomType.GRADE);
    }

    private void createIfNotExists(String name, String key, ChatRoom.RoomType type) {
        chatRoomRepository.findByRoomKey(key)
                .orElseGet(() -> chatRoomRepository.save(new ChatRoom(name, key, type)));
    }

    public List<ChatRoom> getPublicRoomsForMember(int grade) {
        List<ChatRoom> rooms = new ArrayList<>();
        chatRoomRepository.findByRoomKey("global").ifPresent(rooms::add);
        chatRoomRepository.findByRoomKey("grade_" + grade).ifPresent(rooms::add);
        return rooms;
    }

    public List<ChatRoom> getPrivateRoomsForUser(String studentId) {
        return chatRoomRepository.findPrivateRoomsForStudent(studentId);
    }

    public Optional<ChatRoom> findById(Long id) {
        return chatRoomRepository.findById(id);
    }

    @Transactional
    public ChatRoom getOrCreatePrivateRoom(String myName, String myId, String targetName, String targetId) {
        String key = myId.compareTo(targetId) < 0
                ? myId + "_" + targetId
                : targetId + "_" + myId;
        String name = myId.compareTo(targetId) < 0
                ? myName + "/" + targetName
                : targetName + "/" + myName;
        return chatRoomRepository.findByRoomKey(key)
                .orElseGet(() -> chatRoomRepository.save(new ChatRoom(name, key, ChatRoom.RoomType.PRIVATE)));
    }

    public List<ChatMessage> getMessages(Long roomId) {
        return chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId);
    }

    @Transactional
    public ChatMessage saveMessage(ChatMessage message) {
        return chatMessageRepository.save(message);
    }

    public Map<Long, ChatMessage> getLastMessages(List<Long> roomIds) {
        Map<Long, ChatMessage> map = new HashMap<>();
        for (Long roomId : roomIds) {
            chatMessageRepository.findTopByRoomIdOrderBySentAtDesc(roomId)
                    .ifPresent(m -> map.put(roomId, m));
        }
        return map;
    }

    public Map<Long, Long> getUnreadCounts(String studentId, List<Long> roomIds) {
        Map<Long, Long> map = new HashMap<>();
        for (Long roomId : roomIds) {
            Optional<ChatReadStatus> status = chatReadStatusRepository.findByStudentIdAndRoomId(studentId, roomId);
            long count;
            if (status.isEmpty()) {
                count = chatMessageRepository.countByRoomId(roomId);
            } else {
                count = chatMessageRepository.countByRoomIdAndIdGreaterThan(roomId, status.get().getLastReadMessageId());
            }
            if (count > 0) map.put(roomId, count);
        }
        return map;
    }

    public List<HomePrivateChatSummary> getUnreadPrivateChatSummaries(String studentId) {
        List<ChatRoom> privateRooms = getPrivateRoomsForUser(studentId);
        List<HomePrivateChatSummary> summaries = new ArrayList<>();

        for (ChatRoom room : privateRooms) {
            Optional<ChatReadStatus> status = chatReadStatusRepository.findByStudentIdAndRoomId(studentId, room.getId());
            long unreadCount = status
                    .map(s -> chatMessageRepository.countByRoomIdAndIdGreaterThanAndSenderNot(
                            room.getId(), s.getLastReadMessageId(), studentId))
                    .orElseGet(() -> chatMessageRepository.countByRoomIdAndSenderNot(room.getId(), studentId));

            if (unreadCount <= 0) {
                continue;
            }

            Optional<ChatMessage> lastUnreadMessage = chatMessageRepository
                    .findTopByRoomIdAndSenderNotOrderBySentAtDesc(room.getId(), studentId);
            if (lastUnreadMessage.isEmpty()) {
                continue;
            }

            ChatMessage message = lastUnreadMessage.get();
            summaries.add(new HomePrivateChatSummary(
                    room.getId(),
                    resolveOtherName(room, studentId, message),
                    resolveOtherStudentId(room, studentId, message),
                    unreadCount,
                    message.getSentAt(),
                    formatHomeChatTime(message.getSentAt())
            ));
        }

        summaries.sort(Comparator.comparing(HomePrivateChatSummary::lastSentAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return summaries;
    }

    private String resolveOtherName(ChatRoom room, String studentId, ChatMessage message) {
        if (message.getSenderName() != null && !message.getSenderName().isBlank()) {
            return message.getSenderName();
        }
        String otherStudentId = resolveOtherStudentId(room, studentId, message);
        return otherStudentId == null || otherStudentId.isBlank() ? "상대방" : otherStudentId;
    }

    private String resolveOtherStudentId(ChatRoom room, String studentId, ChatMessage message) {
        if (message.getSender() != null && !message.getSender().isBlank() && !message.getSender().equals(studentId)) {
            return message.getSender();
        }
        if (room.getRoomKey() == null || !room.getRoomKey().contains("_")) {
            return message.getSender();
        }
        String[] keys = room.getRoomKey().split("_");
        if (keys.length < 2) {
            return message.getSender();
        }
        return keys[0].equals(studentId) ? keys[1] : keys[0];
    }

    private String formatHomeChatTime(LocalDateTime sentAt) {
        if (sentAt == null) {
            return "시간 정보 없음";
        }

        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(sentAt, now);
        long minutes = duration.toMinutes();
        long hours = duration.toHours();

        if (minutes < 1) {
            return "방금 전";
        }
        if (minutes < 60) {
            return minutes + "분 전";
        }
        if (hours < 24) {
            return hours + "시간 전";
        }
        if (sentAt.getYear() == now.getYear()) {
            return sentAt.format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"));
        }
        return sentAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
    }

    public Long getLastReadMessageId(String studentId, Long roomId) {
        return chatReadStatusRepository.findByStudentIdAndRoomId(studentId, roomId)
                .map(ChatReadStatus::getLastReadMessageId)
                .orElse(null);
    }

    @Transactional
    public void markAsRead(String studentId, Long roomId) {
        chatMessageRepository.findTopByRoomIdOrderBySentAtDesc(roomId).ifPresent(lastMsg -> {
            chatReadStatusRepository.findByStudentIdAndRoomId(studentId, roomId)
                    .ifPresentOrElse(
                            s -> s.setLastReadMessageId(lastMsg.getId()),
                            () -> chatReadStatusRepository.save(new ChatReadStatus(studentId, roomId, lastMsg.getId()))
                    );
        });
    }
}
