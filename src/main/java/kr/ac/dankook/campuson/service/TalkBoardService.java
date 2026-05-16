package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.entity.TalkBoard;
import kr.ac.dankook.campuson.entity.TalkComment;
import kr.ac.dankook.campuson.entity.VoteItem;
import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.repository.TalkBoardRepository;
import kr.ac.dankook.campuson.repository.TalkCommentRepository;
import kr.ac.dankook.campuson.repository.VoteItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TalkBoardService {

    @Autowired
    private TalkBoardRepository talkBoardRepository;
    @Autowired
    private TalkCommentRepository talkCommentRepository;
    @Autowired
    private VoteItemRepository voteItemRepository;
    @Autowired private MemberRepository memberRepository;

    public void save(TalkBoard post) {
        talkBoardRepository.save(post);
    }

    // 좋아요 토글
    public int toggleLike(Long postId, Long memberId) {
        TalkBoard post = talkBoardRepository.findById(postId).orElseThrow(); // boardRepository 아닌지 확인
        if (post.getLikedMemberIds().contains(memberId)) {
            post.getLikedMemberIds().remove(memberId);
        } else {
            post.getLikedMemberIds().add(memberId);
        }
        talkBoardRepository.save(post);
        return post.getLikedMemberIds().size();
    }

    // 상단 고정 토글
    public void togglePin(Long postId, Long memberId) {
        TalkBoard post = findById(postId);
        if (post.getMemberId().equals(memberId)) {
            post.setPinned(!post.isPinned());
            talkBoardRepository.save(post);
        }
    }

    // 검색
    public List<TalkBoard> search(String keyword) {
        return talkBoardRepository.findAllByOrderByRegDateDesc().stream()
                .filter(p -> p.getTitle().contains(keyword) || p.getContent().contains(keyword))
                .toList();
    }

    // 목록 조회 - pinned 먼저 정렬
    public List<TalkBoard> getPostsByCategory(String category) {
        List<TalkBoard> posts = switch (category) {
            case "투표" -> talkBoardRepository.findAllWithVoteOrderByRegDateDesc();
            case "사진" -> talkBoardRepository.findAllWithImageOrderByRegDateDesc();
            case "동영상" -> talkBoardRepository.findAllWithVideoOrderByRegDateDesc();
            case "파일" -> talkBoardRepository.findAllWithFileOrderByRegDateDesc();
            default -> talkBoardRepository.findAllByOrderByRegDateDesc();
        };
        // pinned 글 먼저
        return posts.stream()
                .sorted((a, b) -> Boolean.compare(b.isPinned(), a.isPinned()))
                .toList();
    }

    public TalkBoard findById(Long id) {
        return talkBoardRepository.findById(id).orElseThrow();
    }

    public boolean delete(Long id, Long memberId) {
        TalkBoard post = findById(id);
        if (post.getMemberId() != null && post.getMemberId().equals(memberId)) {
            talkBoardRepository.delete(post);
            return true;
        }
        return false;
    }

    public void saveComment(Long postId, String content, String author, Long memberId) {
        TalkBoard post = findById(postId);
        TalkComment comment = new TalkComment();
        comment.setContent(content);
        comment.setAuthor(author);
        comment.setMemberId(memberId);
        comment.setTalkBoard(post);
        talkCommentRepository.save(comment);
    }

    public boolean deleteComment(Long commentId, Long memberId) {
        TalkComment comment = talkCommentRepository.findById(commentId).orElseThrow();
        if (comment.getMemberId() != null && comment.getMemberId().equals(memberId)) {
            talkCommentRepository.delete(comment);
            return true;
        }
        return false;
    }

    public void saveVoteItems(TalkBoard post, List<String> items) {
        for (String item : items) {
            if (item != null && !item.isBlank()) {
                VoteItem vi = new VoteItem();
                vi.setItemText(item);
                vi.setTalkBoard(post);
                voteItemRepository.save(vi);
            }
        }
    }

    public String vote(Long voteItemId, Long memberId) {
        VoteItem newItem = voteItemRepository.findById(voteItemId).orElseThrow();
        TalkBoard post = newItem.getTalkBoard();

        VoteItem previousItem = post.getVoteItems().stream()
                .filter(v -> v.getVotedMemberIds().contains(memberId))
                .findFirst()
                .orElse(null);

        if (previousItem != null) {
            if (previousItem.getId().equals(voteItemId)) {
                previousItem.setVoteCount(previousItem.getVoteCount() - 1);
                previousItem.getVotedMemberIds().remove(memberId);
                previousItem.getVotedMemberNames().removeIf(name -> memberRepository.findById(memberId)
                        .map(m -> (m.getName() + "_" + m.getStudentId().substring(2, 4)).equals(name))
                        .orElse(false));
                voteItemRepository.save(previousItem);
                return "cancelled";
            }
            previousItem.setVoteCount(previousItem.getVoteCount() - 1);
            previousItem.getVotedMemberIds().remove(memberId);
            previousItem.getVotedMemberNames().removeIf(name -> memberRepository.findById(memberId)
                    .map(m -> (m.getName() + "_" + m.getStudentId().substring(2, 4)).equals(name))
                    .orElse(false));
            voteItemRepository.save(previousItem);
        }

        Member member = memberRepository.findById(memberId).orElseThrow();
        String year = member.getStudentId().substring(2, 4);
        String memberName = member.getName() + "_" + year;

        newItem.setVoteCount(newItem.getVoteCount() + 1);
        newItem.getVotedMemberIds().add(memberId);
        newItem.getVotedMemberNames().add(memberName);
        voteItemRepository.save(newItem);
        return "ok";
    }
}