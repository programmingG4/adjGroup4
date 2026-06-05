package kr.ac.dankook.campuson.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String studentId;

    @Column(nullable = false)
    private String password;

    @Column
    private String department;

    @Column
    private String imagePath;

    @Column(nullable = false)
    private int grade;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MemberStatus status = MemberStatus.PENDING;

    @Column(nullable = false)
    private boolean admin = false;

    @Column(nullable = false)
    private boolean blocked = false;

    @Column
    private LocalDateTime blockedUntil; // null이면 영구차단, not null이면 기간 정지

    public enum MemberStatus {
        PENDING, APPROVED, REJECTED
    }
}