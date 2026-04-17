package kr.ac.dankook.campuson.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Getter @Setter
public class Board {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String category;    // 선택한 카테고리가 여기에 저장됩니다
    private String author;
    private String imagePath; // 사진 첨부
    private LocalDateTime regDate = LocalDateTime.now();

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL)
    private List<Comment> comments; // 댓글

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL)
    private List<VoteItem> voteItems; // 투표 항목
}