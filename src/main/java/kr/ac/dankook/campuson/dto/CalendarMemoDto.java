package kr.ac.dankook.campuson.dto;

import java.time.LocalDate;

public record CalendarMemoDto(
        Long id,
        LocalDate memoDate,
        String content
) {
}
