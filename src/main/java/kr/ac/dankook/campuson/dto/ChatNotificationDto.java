package kr.ac.dankook.campuson.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatNotificationDto {
    private Long roomId;
    private String roomName;
    private String senderName;
    private String content;
}
