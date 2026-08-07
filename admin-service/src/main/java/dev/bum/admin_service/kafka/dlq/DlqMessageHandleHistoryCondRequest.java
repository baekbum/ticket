package dev.bum.admin_service.kafka.dlq;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DlqMessageHandleHistoryCondRequest {

    private String dltTopic;
    private Integer partitionNo;
    private Long messageOffset;
    private String messageKey;
    private String action;
    private String status;
    private String operator;
    private Boolean payloadModified;
    private LocalDateTime handledFrom;
    private LocalDateTime handledTo;
    private Integer page;
    private Integer size;
}
