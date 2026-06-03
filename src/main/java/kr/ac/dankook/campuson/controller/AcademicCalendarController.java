package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.dto.AcademicScheduleEvent;
import kr.ac.dankook.campuson.dto.CalendarMemoDto;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.AcademicCalendarService;
import kr.ac.dankook.campuson.service.CalendarMemoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Controller
public class AcademicCalendarController {

    private final MemberRepository memberRepository;
    private final AcademicCalendarService academicCalendarService;
    private final CalendarMemoService calendarMemoService;

    public AcademicCalendarController(MemberRepository memberRepository,
                                      AcademicCalendarService academicCalendarService,
                                      CalendarMemoService calendarMemoService) {
        this.memberRepository = memberRepository;
        this.academicCalendarService = academicCalendarService;
        this.calendarMemoService = calendarMemoService;
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) Integer year,
                           @RequestParam(required = false) Integer month,
                           Model model,
                           Principal principal) {
        LocalDate today = LocalDate.now();
        int targetYear = year == null ? today.getYear() : year;
        int targetMonth = month == null ? today.getMonthValue() : month;
        YearMonth yearMonth = YearMonth.of(targetYear, targetMonth);
        LocalDate calendarStart = startOfCalendar(yearMonth);
        LocalDate calendarEnd = calendarStart.plusDays(41);

        Member member = memberRepository.findByStudentId(principal.getName());
        List<AcademicScheduleEvent> monthlySchedules = academicCalendarService.findMonthlyScheduleList(targetYear, targetMonth);
        List<CalendarMemoDto> memos = calendarMemoService.findMemos(principal.getName(), calendarStart, calendarEnd);

        model.addAttribute("member", member);
        model.addAttribute("activeMenu", "calendar");
        model.addAttribute("today", today);
        model.addAttribute("yearMonth", yearMonth);
        model.addAttribute("prevMonth", yearMonth.minusMonths(1));
        model.addAttribute("nextMonth", yearMonth.plusMonths(1));
        model.addAttribute("calendarWeeks", buildCalendarWeeks(calendarStart));
        model.addAttribute("monthlySchedules", monthlySchedules);
        model.addAttribute("memos", memos);
        model.addAttribute("selectedDate", today);
        return "calendar/index";
    }

    @PostMapping("/calendar/memo")
    public String addMemo(@RequestParam String memoDate,
                          @RequestParam String content,
                          @RequestParam int year,
                          @RequestParam int month,
                          Principal principal) {
        calendarMemoService.saveMemo(principal.getName(), LocalDate.parse(memoDate), content);
        return "redirect:/calendar?year=" + year + "&month=" + month;
    }

    @PostMapping("/calendar/memo/delete")
    public String deleteMemo(@RequestParam Long memoId,
                             @RequestParam int year,
                             @RequestParam int month,
                             Principal principal) {
        calendarMemoService.deleteMemo(principal.getName(), memoId);
        return "redirect:/calendar?year=" + year + "&month=" + month;
    }

    private LocalDate startOfCalendar(YearMonth yearMonth) {
        LocalDate firstDay = yearMonth.atDay(1);
        int offset = firstDay.getDayOfWeek().getValue() % 7;
        return firstDay.minusDays(offset);
    }

    private List<List<LocalDate>> buildCalendarWeeks(LocalDate calendarStart) {
        List<List<LocalDate>> weeks = new ArrayList<>();
        LocalDate cursor = calendarStart;
        for (int week = 0; week < 6; week++) {
            List<LocalDate> days = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                days.add(cursor);
                cursor = cursor.plusDays(1);
            }
            weeks.add(days);
        }
        return weeks;
    }
}
