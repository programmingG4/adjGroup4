package kr.ac.dankook.campuson.service;

import jakarta.annotation.PostConstruct;
import kr.ac.dankook.campuson.dto.HomePrivateChatSummary;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.ChatReadStatus;
import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.entity.ChatRoomMember;
import kr.ac.dankook.campuson.repository.ChatMessageRepository;
import kr.ac.dankook.campuson.repository.ChatReadStatusRepository;
import kr.ac.dankook.campuson.repository.ChatRoomMemberRepository;
import kr.ac.dankook.campuson.repository.ChatRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public ChatService(ChatRoomRepository chatRoomRepository,
                       ChatMessageRepository chatMessageRepository,
                       ChatReadStatusRepository chatReadStatusRepository,
                       ChatRoomMemberRepository chatRoomMemberRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatReadStatusRepository = chatReadStatusRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
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

    public List<ChatRoom> getGroupRoomsForUser(String studentId) {
        return chatRoomMemberRepository.findByStudentIdAndRoom_Type(studentId, ChatRoom.RoomType.GROUP)
                .stream().map(ChatRoomMember::getRoom).collect(Collectors.toList());
    }

    public List<String> getGroupRoomMemberIds(Long roomId) {
        return chatRoomMemberRepository.findByRoomId(roomId)
                .stream().map(ChatRoomMember::getStudentId).collect(Collectors.toList());
    }

    @Transactional
    public ChatRoom createGroupRoom(String name, String creatorId, List<String> memberIds) {
        String key = "group_" + UUID.randomUUID();
        ChatRoom room = chatRoomRepository.save(new ChatRoom(name, key, ChatRoom.RoomType.GROUP));
        chatRoomMemberRepository.save(new ChatRoomMember(room, creatorId));
        for (String mid : memberIds) {
            if (!mid.isBlank() && !mid.equals(creatorId)) {
                chatRoomMemberRepository.save(new ChatRoomMember(room, mid));
            }
        }
        return room;
    }

    @Transactional
    public boolean addMemberToGroup(Long roomId, String studentId) {
        if (chatRoomMemberRepository.existsByRoomIdAndStudentId(roomId, studentId)) {
            return false;
        }
        findById(roomId).ifPresent(room -> chatRoomMemberRepository.save(new ChatRoomMember(room, studentId)));
        return true;
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

    public List<ChatMessage> searchMessages(Long roomId, String keyword) {
        return chatMessageRepository.findByRoomIdAndContentContainingOrderBySentAtAsc(roomId, keyword);
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
                            () -> chatReadStatusRepository
                                    .save(new ChatReadStatus(studentId, roomId, lastMsg.getId())));
        });
    }

    @Transactional
    public void updatePinnedNotice(Long roomId, String title, String content, Long talkBoardId) {
        chatRoomRepository.findById(roomId).ifPresent(room -> {
            // 기존 단일 공지 (최신 공지용)
            room.setPinnedNotice(content);
            room.setPinnedNoticeTitle(title);
            room.setPinnedTalkBoardId(talkBoardId);

            // 목록에 추가 (중복 방지)
            int existingIdx = room.getPinnedTalkBoardIds().indexOf(talkBoardId);
            if (existingIdx >= 0) {
                room.getPinnedTalkBoardIds().remove(existingIdx);
                room.getPinnedNoticeTitles().remove(existingIdx);
            }
            room.getPinnedNoticeTitles().add(title);
            room.getPinnedTalkBoardIds().add(talkBoardId);
            chatRoomRepository.save(room);
        });
    }

    @Transactional
    public void clearPinnedNotice(Long roomId, Long talkBoardId) {
        chatRoomRepository.findById(roomId).ifPresent(room -> {
            // 목록에서 제거
            int idx = room.getPinnedTalkBoardIds().indexOf(talkBoardId);
            if (idx >= 0) {
                room.getPinnedTalkBoardIds().remove(idx);
                room.getPinnedNoticeTitles().remove(idx);
            }

            // 단일 공지 업데이트 (가장 최근 것으로)
            if (room.getPinnedTalkBoardIds().isEmpty()) {
                room.setPinnedNotice(null);
                room.setPinnedNoticeTitle(null);
                room.setPinnedTalkBoardId(null);
            } else {
                int lastIdx = room.getPinnedTalkBoardIds().size() - 1;
                room.setPinnedNoticeTitle(room.getPinnedNoticeTitles().get(lastIdx));
                room.setPinnedTalkBoardId(room.getPinnedTalkBoardIds().get(lastIdx));
            }
            chatRoomRepository.save(room);
        });
    }

    public List<ChatRoom> getPublicRooms() {
        return chatRoomRepository.findByTypeIn(
                List.of(ChatRoom.RoomType.GLOBAL, ChatRoom.RoomType.GRADE));
    }

    public Optional<ChatRoom> findByRoomKey(String roomKey) {
        return chatRoomRepository.findByRoomKey(roomKey);
    }

    @Transactional
    public ChatMessage sendSystemMessage(Long roomId, String content) {
        return chatRoomRepository.findById(roomId).map(room -> {
            ChatMessage message = new ChatMessage();
            message.setRoom(room);
            message.setSender("SYSTEM");
            message.setSenderName("채팅게시판");
            message.setContent(content);
            return chatMessageRepository.save(message);
        }).orElse(null);
    }

    @Transactional
    public ChatRoom getOrCreateGroupRoom(String name, String roomKey) {
        return chatRoomRepository.findByRoomKey(roomKey)
                .orElseGet(() -> chatRoomRepository.save(
                        new ChatRoom(name, roomKey, ChatRoom.RoomType.GROUP))); // 여기
    }

    @Transactional
    public void addMemberToRoom(Long roomId, String studentId) {
        // 채팅방 입장 처리 - 기존 메시지 읽음 처리
        markAsRead(studentId, roomId);
    }

    public List<ChatRoom> getTradeRoomsForUser(String studentId) {
        return chatRoomRepository.findTradeRoomsForStudent(studentId);
    }

    @Transactional
    public ChatRoom getOrCreateTradeRoom(String myName, String myId, String targetName, String targetId) {
        String key = "trade_" + (myId.compareTo(targetId) < 0
                ? myId + "_" + targetId
                : targetId + "_" + myId);
        String name = myId.compareTo(targetId) < 0
                ? myName + "/" + targetName
                : targetName + "/" + myName;
        return chatRoomRepository.findByRoomKey(key)
                .orElseGet(() -> chatRoomRepository.save(
                        new ChatRoom(name, key, ChatRoom.RoomType.TRADE)));
    }
}
