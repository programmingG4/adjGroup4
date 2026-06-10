package kr.ac.dankook.campuson.dto;

import java.time.LocalDate;

public record AcademicScheduleEvent(
        String id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String category,
        String sourceUrl
) {
}
