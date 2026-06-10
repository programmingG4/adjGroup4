package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    List<ChatRoomMember> findByStudentIdAndRoom_Type(String studentId, ChatRoom.RoomType type);
    List<ChatRoomMember> findByRoomId(Long roomId);
    boolean existsByRoomIdAndStudentId(Long roomId, String studentId);
    void deleteByStudentId(String studentId);
}
