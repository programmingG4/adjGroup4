package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.TalkBoard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TalkBoardRepository extends JpaRepository<TalkBoard, Long> {
    List<TalkBoard> findAllByOrderByRegDateDesc();

    // 투표 있는 글
    @Query("SELECT t FROM TalkBoard t WHERE t.voteItems IS NOT EMPTY ORDER BY t.regDate DESC")
    List<TalkBoard> findAllWithVoteOrderByRegDateDesc();

    // 이미지 있는 글
    @Query("SELECT t FROM TalkBoard t WHERE t.imagePaths IS NOT EMPTY ORDER BY t.regDate DESC")
    List<TalkBoard> findAllWithImageOrderByRegDateDesc();

    // 동영상 있는 글
    @Query("SELECT t FROM TalkBoard t WHERE t.videoPaths IS NOT EMPTY ORDER BY t.regDate DESC")
    List<TalkBoard> findAllWithVideoOrderByRegDateDesc();

    // 파일 있는 글
    @Query("SELECT t FROM TalkBoard t WHERE t.filePaths IS NOT EMPTY ORDER BY t.regDate DESC")
    List<TalkBoard> findAllWithFileOrderByRegDateDesc();
}