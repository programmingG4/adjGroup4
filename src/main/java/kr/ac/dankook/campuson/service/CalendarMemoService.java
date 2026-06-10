package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.dto.CalendarMemoDto;
import kr.ac.dankook.campuson.entity.CalendarMemo;
import kr.ac.dankook.campuson.repository.CalendarMemoRepository;
import kr.ac.dankook.campuson.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CalendarMemoService {

    private final CalendarMemoRepository calendarMemoRepository;
    private final MemberRepository memberRepository;

    public CalendarMemoService(CalendarMemoRepository calendarMemoRepository, MemberRepository memberRepository) {
        this.calendarMemoRepository = calendarMemoRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<CalendarMemoDto> findMemos(String studentId, LocalDate startDate, LocalDate endDate) {
        return calendarMemoRepository.findByMemberStudentIdAndMemoDateBetweenOrderByMemoDateAscIdAsc(studentId, startDate, endDate)
                .stream()
                .map(memo -> new CalendarMemoDto(memo.getId(), memo.getMemoDate(), memo.getContent()))
                .toList();
    }

    @Transactional
    public void saveMemo(String studentId, LocalDate memoDate, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        Member member = memberRepository.findByStudentId(studentId);
        if (member == null) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        CalendarMemo memo = new CalendarMemo();
        memo.setMember(member);
        memo.setMemoDate(memoDate);
        memo.setContent(content.trim());
        calendarMemoRepository.save(memo);
    }

    @Transactional
    public void deleteMemo(String studentId, Long memoId) {
        calendarMemoRepository.findByIdAndMemberStudentId(memoId, studentId)
                .ifPresent(calendarMemoRepository::delete);
    }
}
