package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRoomIdOrderBySentAtAsc(Long roomId);
    Optional<ChatMessage> findTopByRoomIdOrderBySentAtDesc(Long roomId);
    long countByRoomId(Long roomId);
    long countByRoomIdAndIdGreaterThan(Long roomId, Long messageId);
}
