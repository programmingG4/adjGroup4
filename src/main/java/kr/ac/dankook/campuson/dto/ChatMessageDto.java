package kr.ac.dankook.campuson.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatMessageDto {
    private Long id;
    private String sender;      // studentId
    private String senderName;
    private String content;
    private String sentAt;
    private String mediaUrl;
    private String mediaType;
    private String fileName;
}
