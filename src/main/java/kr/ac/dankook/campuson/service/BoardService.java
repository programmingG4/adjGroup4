package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.entity.Board;
import kr.ac.dankook.campuson.entity.Comment;
import kr.ac.dankook.campuson.entity.VoteItem;
import kr.ac.dankook.campuson.repository.BoardRepository;
import kr.ac.dankook.campuson.repository.CommentRepository;
import kr.ac.dankook.campuson.repository.VoteItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BoardService {

    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private VoteItemRepository voteItemRepository;

    public void save(Board board) {
        boardRepository.save(board);
    }

    // 최신순 조회
    public List<Board> getPostsByCategory(String category) {
        if ("전체".equals(category))
            return boardRepository.findAllByOrderByRegDateDesc();
        return boardRepository.findByCategoryOrderByRegDateDesc(category);
    }

    public Board findById(Long id) {
        return boardRepository.findById(id).orElseThrow();
    }

    // 게시글 삭제 (본인만)
    public boolean delete(Long boardId, Long memberId) {
        Board board = findById(boardId);
        if (board.getMemberId() != null && board.getMemberId().equals(memberId)) {
            boardRepository.delete(board);
            return true;
        }
        return false;
    }

    // 댓글 저장
    public void saveComment(Long boardId, String content, String author, Long memberId) {
        Board board = findById(boardId);
        Comment comment = new Comment();
        comment.setContent(content);
        comment.setAuthor(author);
        comment.setMemberId(memberId);
        comment.setBoard(board);
        commentRepository.save(comment);
    }

    // 댓글 삭제 (본인만)
    public boolean deleteComment(Long commentId, Long memberId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow();
        if (comment.getMemberId() != null && comment.getMemberId().equals(memberId)) {
            commentRepository.delete(comment);
            return true;
        }
        return false;
    }

    // 투표 항목 저장
    public void saveVoteItems(Board board, List<String> items) {
        for (String item : items) {
            if (item != null && !item.isBlank()) {
                VoteItem vi = new VoteItem();
                vi.setItemText(item);
                vi.setBoard(board);
                voteItemRepository.save(vi);
            }
        }
    }

    // 투표 처리 (로그인 유저, 중복 방지)
    public String vote(Long voteItemId, Long memberId) {
        VoteItem item = voteItemRepository.findById(voteItemId).orElseThrow();
        Board board = item.getBoard();

        // 이 게시글의 모든 투표 항목에서 이미 투표했는지 확인
        boolean alreadyVoted = board.getVoteItems().stream()
                .anyMatch(v -> v.getVotedMemberIds().contains(memberId));

        if (alreadyVoted)
            return "already_voted";

        item.setVoteCount(item.getVoteCount() + 1);
        item.getVotedMemberIds().add(memberId);
        voteItemRepository.save(item);
        return "ok";
    }
}