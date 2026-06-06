package kr.ac.dankook.campuson.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String content;
    private String author;
    private Long memberId; // 로그인 연동용
    private LocalDateTime regDate = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "board_id")
    private Board board;
}