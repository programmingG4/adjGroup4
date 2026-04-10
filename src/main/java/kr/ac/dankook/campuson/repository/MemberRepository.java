package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByStudentId(String studentId);
    Member findByStudentId(String studentId);
}