package kr.ac.dankook.campuson.entity;

//import java.util.ArrayList;
//import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class VoteItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String itemText;
    private int voteCount = 0;

    @ManyToOne
    @JoinColumn(name = "board_id")
    private Board board;
}