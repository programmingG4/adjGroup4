package kr.ac.dankook.campuson.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "room_id"}))
public class ChatReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id")
    private String studentId;

    @Column(name = "room_id")
    private Long roomId;

    private Long lastReadMessageId;

    public ChatReadStatus(String studentId, Long roomId, Long lastReadMessageId) {
        this.studentId = studentId;
        this.roomId = roomId;
        this.lastReadMessageId = lastReadMessageId;
    }
}
