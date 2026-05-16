package kr.ac.dankook.campuson.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String roomKey;

    @Enumerated(EnumType.STRING)
    private RoomType type;

    // 공지 필드 추가
    @Column(columnDefinition = "TEXT")
    private String pinnedNotice; // 공지 내용
    private String pinnedNoticeTitle; // 공지 제목
    private Long pinnedTalkBoardId; // 연동된 채팅게시판 글 ID

    public enum RoomType {
        GLOBAL, GRADE, PRIVATE
    }

    public ChatRoom(String name, String roomKey, RoomType type) {
        this.name = name;
        this.roomKey = roomKey;
        this.type = type;
    }

    @ElementCollection
    @CollectionTable(name = "chat_room_pinned_notices", 
                    joinColumns = @JoinColumn(name = "chat_room_id"))
    @OrderColumn
    private List<String> pinnedNoticeTitles = new ArrayList<>();

    @ElementCollection  
    @CollectionTable(name = "chat_room_pinned_ids",
                    joinColumns = @JoinColumn(name = "chat_room_id"))
    @OrderColumn
    private List<Long> pinnedTalkBoardIds = new ArrayList<>();
}
