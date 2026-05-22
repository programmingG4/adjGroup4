package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.entity.Board;
import kr.ac.dankook.campuson.entity.Comment;
import kr.ac.dankook.campuson.entity.VoteItem;
import kr.ac.dankook.campuson.repository.BoardRepository;
import kr.ac.dankook.campuson.repository.CommentRepository;
import kr.ac.dankook.campuson.repository.VoteItemRepository;
import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
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
    @Autowired
    private MemberRepository memberRepository;

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
        VoteItem newItem = voteItemRepository.findById(voteItemId).orElseThrow();
        Board board = newItem.getBoard();

        // 기존에 투표한 항목 찾기
        VoteItem previousItem = board.getVoteItems().stream()
                .filter(v -> v.getVotedMemberIds().contains(memberId))
                .findFirst()
                .orElse(null);

        if (previousItem != null) {
            // 같은 항목 다시 누르면 취소
            if (previousItem.getId().equals(voteItemId)) {
                previousItem.setVoteCount(previousItem.getVoteCount() - 1);
                previousItem.getVotedMemberIds().remove(memberId);
                previousItem.getVotedMemberNames().removeIf(name -> memberRepository.findById(memberId)
                        .map(m -> (m.getName() + "_" + m.getStudentId().substring(2, 4)).equals(name))
                        .orElse(false));
                voteItemRepository.save(previousItem);
                return "cancelled";
            }
            // 다른 항목 누르면 기존 취소 후 새로 투표
            previousItem.setVoteCount(previousItem.getVoteCount() - 1);
            previousItem.getVotedMemberIds().remove(memberId);
            previousItem.getVotedMemberNames().removeIf(name -> memberRepository.findById(memberId)
                    .map(m -> (m.getName() + "_" + m.getStudentId().substring(2, 4)).equals(name))
                    .orElse(false));
            voteItemRepository.save(previousItem);
        }

        // 새 항목에 투표
        Member member = memberRepository.findById(memberId).orElseThrow();
        String year = member.getStudentId().substring(2, 4);
        String memberName = member.getName() + "_" + year;

        newItem.setVoteCount(newItem.getVoteCount() + 1);
        newItem.getVotedMemberIds().add(memberId);
        newItem.getVotedMemberNames().add(memberName);
        voteItemRepository.save(newItem);
        return "ok";
    }

    // 좋아요 토글
    public int toggleLike(Long boardId, Long memberId) {
        Board board = findById(boardId);
        if (board.getLikedMemberIds().contains(memberId)) {
            board.getLikedMemberIds().remove(memberId);
        } else {
            board.getLikedMemberIds().add(memberId);
        }
        boardRepository.save(board);
        return board.getLikedMemberIds().size();
    }

    // 검색
    public List<Board> search(String keyword) {
        return boardRepository.findAllByOrderByRegDateDesc().stream()
                .filter(b -> List.of("익명 게시판", "중고거래 게시판", "📢 모집중").contains(b.getCategory()))  // 카테고리 필터
                .filter(b -> b.getTitle() != null && b.getContent() != null)
                .filter(b -> b.getTitle().contains(keyword) || b.getContent().contains(keyword))
                .toList();
    }
}