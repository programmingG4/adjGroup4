package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.CalendarMemo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CalendarMemoRepository extends JpaRepository<CalendarMemo, Long> {
    List<CalendarMemo> findByMemberStudentIdAndMemoDateBetweenOrderByMemoDateAscIdAsc(String studentId, LocalDate startDate, LocalDate endDate);
    Optional<CalendarMemo> findByIdAndMemberStudentId(Long id, String studentId);
    void deleteByMemberStudentId(String studentId);
}
