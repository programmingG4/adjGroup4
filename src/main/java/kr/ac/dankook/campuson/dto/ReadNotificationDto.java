package kr.ac.dankook.campuson.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReadNotificationDto {
    private String studentId;
    private Long lastReadMessageId;
}
