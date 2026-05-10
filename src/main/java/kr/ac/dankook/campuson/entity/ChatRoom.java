package kr.ac.dankook.campuson.entity;

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

    public enum RoomType {
        GLOBAL, GRADE, PRIVATE
    }

    public ChatRoom(String name, String roomKey, RoomType type) {
        this.name = name;
        this.roomKey = roomKey;
        this.type = type;
    }
}
