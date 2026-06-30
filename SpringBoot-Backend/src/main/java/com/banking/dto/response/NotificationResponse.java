package com.banking.dto.response;
import com.banking.enums.NotificationType;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private String title;
    private String message;
    private NotificationType type;
    private boolean read;
    private UUID referenceId;
    private LocalDateTime createdAt;
}
