package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByBoardIdOrderByRegDateAsc(Long boardId);
    void deleteByMemberId(Long memberId);
}