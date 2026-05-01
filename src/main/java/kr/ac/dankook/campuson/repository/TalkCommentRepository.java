package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.TalkComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TalkCommentRepository extends JpaRepository<TalkComment, Long> {
}