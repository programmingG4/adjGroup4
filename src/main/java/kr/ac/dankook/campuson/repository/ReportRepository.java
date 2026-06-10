package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByCreatedAtDesc();
    List<Report> findByResolvedFalseOrderByCreatedAtDesc();
    List<Report> findByTargetTypeAndTargetId(String targetType, Long targetId);
}
