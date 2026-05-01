package kr.ac.dankook.campuson.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class VoteItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String itemText;
    private int voteCount = 0;

    @ElementCollection
    @CollectionTable(name = "vote_item_voters", joinColumns = @JoinColumn(name = "vote_item_id"))
    @Column(name = "member_id")
    private List<Long> votedMemberIds = new ArrayList<>(); // 투표한 멤버 ID 목록

    @ManyToOne
    @JoinColumn(name = "board_id")
    private Board board;

    @ManyToOne
    @JoinColumn(name = "talkboard_id")
    private TalkBoard talkBoard;

    @ElementCollection
    @CollectionTable(name = "vote_item_voter_names", joinColumns = @JoinColumn(name = "vote_item_id"))
    @Column(name = "member_name")
    private List<String> votedMemberNames = new ArrayList<>();
}