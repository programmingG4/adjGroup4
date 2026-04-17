package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {
    // 카테고리별로 게시글을 찾아오는 기능
    List<Board> findByCategory(String category);
}