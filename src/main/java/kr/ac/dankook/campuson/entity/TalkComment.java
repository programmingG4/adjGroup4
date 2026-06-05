package kr.ac.dankook.campuson.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class TalkComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String content;
    private String author;
    private Long memberId;
    private LocalDateTime regDate = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "talkboard_id")
    private TalkBoard talkBoard;
}