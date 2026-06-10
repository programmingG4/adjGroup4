package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByStudentId(String studentId);
    Member findByStudentId(String studentId);

    @Query("SELECT m FROM Member m WHERE (m.studentId LIKE %:q% OR m.name LIKE %:q%) AND m.studentId <> :excludeId ORDER BY m.name")
    List<Member> searchByKeyword(@Param("q") String q, @Param("excludeId") String excludeId);

    List<Member> findByGradeAndStudentIdNot(int grade, String excludeStudentId);
}