package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRoomIdOrderBySentAtAsc(Long roomId);
    List<ChatMessage> findByRoomIdAndContentContainingOrderBySentAtAsc(Long roomId, String keyword);
    Optional<ChatMessage> findTopByRoomIdOrderBySentAtDesc(Long roomId);
    long countByRoomId(Long roomId);
    long countByRoomIdAndIdGreaterThan(Long roomId, Long messageId);
    long countByRoomIdAndSenderNot(Long roomId, String sender);
    long countByRoomIdAndIdGreaterThanAndSenderNot(Long roomId, Long messageId, String sender);
    Optional<ChatMessage> findTopByRoomIdAndSenderNotOrderBySentAtDesc(Long roomId, String sender);
}
