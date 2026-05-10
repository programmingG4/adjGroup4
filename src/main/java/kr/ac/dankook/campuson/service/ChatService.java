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
                            () -> chatReadStatusRepository.save(new ChatReadStatus(studentId, roomId, lastMsg.getId()))
                    );
        });
    }
}
