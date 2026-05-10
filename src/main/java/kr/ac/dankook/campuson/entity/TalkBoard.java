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
public class TalkBoard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String author;
    private Long memberId;

    @ElementCollection
    @CollectionTable(name = "talkboard_images", joinColumns = @JoinColumn(name = "talkboard_id"))
    @Column(name = "image_path")
    private List<String> imagePaths = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "talkboard_videos", joinColumns = @JoinColumn(name = "talkboard_id"))
    @Column(name = "video_path")
    private List<String> videoPaths = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "talkboard_files", joinColumns = @JoinColumn(name = "talkboard_id"))
    @Column(name = "file_path")
    private List<String> filePaths = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "talkboard_file_names", joinColumns = @JoinColumn(name = "talkboard_id"))
    @Column(name = "file_name")
    private List<String> fileNames = new ArrayList<>(); // 원본 파일명

    private LocalDateTime regDate = LocalDateTime.now();

    @OneToMany(mappedBy = "talkBoard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TalkComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "talkBoard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VoteItem> voteItems = new ArrayList<>();

    // 필터링용 메서드
    public boolean hasVote() {
        return !voteItems.isEmpty();
    }

    public boolean hasImage() {
        return !imagePaths.isEmpty();
    }

    public boolean hasVideo() {
        return !videoPaths.isEmpty();
    }

    public boolean hasFile() {
        return !filePaths.isEmpty();
    }
}