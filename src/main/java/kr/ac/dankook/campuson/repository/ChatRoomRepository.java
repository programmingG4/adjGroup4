package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByRoomKey(String roomKey);
    List<ChatRoom> findByType(ChatRoom.RoomType type);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'PRIVATE' AND (r.roomKey LIKE CONCAT(:sid, '_%') OR r.roomKey LIKE CONCAT('%_', :sid))")
    List<ChatRoom> findPrivateRoomsForStudent(@Param("sid") String studentId);
    
    List<ChatRoom> findByTypeIn(List<ChatRoom.RoomType> types);
}
