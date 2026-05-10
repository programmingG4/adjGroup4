package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.ChatReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatReadStatusRepository extends JpaRepository<ChatReadStatus, Long> {
    Optional<ChatReadStatus> findByStudentIdAndRoomId(String studentId, Long roomId);
}
