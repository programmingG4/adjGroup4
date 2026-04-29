package kr.ac.dankook.campuson.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "board")
public class Board {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String category;
    private String author;
    private Long memberId; // 로그인 연동용 (익명이면 null)

    @ElementCollection
    @CollectionTable(name = "board_images", joinColumns = @JoinColumn(name = "board_id"))
    @Column(name = "image_path")
    private List<String> imagePaths = new ArrayList<>(); // 여러장 이미지

    private LocalDateTime regDate = LocalDateTime.now();

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VoteItem> voteItems = new ArrayList<>();
}