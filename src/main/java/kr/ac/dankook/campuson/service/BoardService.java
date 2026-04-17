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

    // 게시글 저장
    public void save(Board board) {
        boardRepository.save(board);
    }

    // 카테고리별 조회
    public List<Board> getPostsByCategory(String category) {
        if ("전체".equals(category)) {
            return boardRepository.findAll();
        }
        return boardRepository.findByCategory(category);
    }

    public Board findById(Long id) {
        return boardRepository.findById(id).orElseThrow();
    }

    // 댓글 저장
    public void saveComment(Long boardId, String content, String author) {
        Board board = findById(boardId);
        Comment comment = new Comment();
        comment.setContent(content);
        comment.setAuthor(author);
        comment.setBoard(board);
        commentRepository.save(comment);
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

    // 투표 처리
    public void vote(Long voteItemId) {
        VoteItem item = voteItemRepository.findById(voteItemId).orElseThrow();
        item.setVoteCount(item.getVoteCount() + 1);
        voteItemRepository.save(item);
    }
}