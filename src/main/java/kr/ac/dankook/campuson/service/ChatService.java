package kr.ac.dankook.campuson.service;

import jakarta.annotation.PostConstruct;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.ChatReadStatus;
import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.repository.ChatMessageRepository;
import kr.ac.dankook.campuson.repository.ChatReadStatusRepository;
import kr.ac.dankook.campuson.repository.ChatRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
