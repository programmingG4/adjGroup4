package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.TalkBoard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    List<TalkBoard> findAllByRoomKeyOrderByRegDateDesc(String roomKey);
    List<TalkBoard> findByRoomKeyOrderByRegDateDesc(String roomKey);

    // 투표/사진/동영상/파일 필터링도 roomKey 추가
    @Query("SELECT t FROM TalkBoard t WHERE t.roomKey = :roomKey AND t.voteItems IS NOT EMPTY ORDER BY t.regDate DESC")
    List<TalkBoard> findWithVoteByRoomKey(@Param("roomKey") String roomKey);

    @Query("SELECT t FROM TalkBoard t WHERE t.roomKey = :roomKey AND t.imagePaths IS NOT EMPTY ORDER BY t.regDate DESC")
    List<TalkBoard> findWithImageByRoomKey(@Param("roomKey") String roomKey);

    @Query("SELECT t FROM TalkBoard t WHERE t.roomKey = :roomKey AND t.videoPaths IS NOT EMPTY ORDER BY t.regDate DESC")
    List<TalkBoard> findWithVideoByRoomKey(@Param("roomKey") String roomKey);

    @Query("SELECT t FROM TalkBoard t WHERE t.roomKey = :roomKey AND t.filePaths IS NOT EMPTY ORDER BY t.regDate DESC")
    List<TalkBoard> findWithFileByRoomKey(@Param("roomKey") String roomKey);

    // 투표 종료됐지만 결과 알림 미전송 글 (voteItems 즉시 로딩)
    @Query("SELECT DISTINCT t FROM TalkBoard t JOIN FETCH t.voteItems WHERE t.voteEndTime IS NOT NULL AND t.voteEndTime < :now AND t.voteResultNotified = false")
    List<TalkBoard> findEndedVotesNotNotified(@Param("now") LocalDateTime now);
}