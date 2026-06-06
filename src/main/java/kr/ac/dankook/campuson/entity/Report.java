package kr.ac.dankook.campuson.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // BOARD, TALKBOARD, COMMENT, TALKCOMMENT
    @Column(nullable = false)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    private String targetSummary;
    private String targetCategory; // 게시판 글인 경우 카테고리 (익명 게시판, 중고거래 게시판, 📢 모집중)

    @Column(columnDefinition = "TEXT")
    private String targetContent;   // 신고된 전체 내용

    private String authorName;      // 신고받은 글/댓글 작성자 이름
    private Long authorMemberId;    // 신고받은 글/댓글 작성자 회원 ID (익명이면 null)

    private String reporterName;
    private Long reporterId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private boolean resolved = false;
    private boolean contentDeleted = false; // 삭제처리 여부 (되돌리기 불가 판단용)

    private LocalDateTime createdAt = LocalDateTime.now();
}
