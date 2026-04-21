package dev.bum.common.kafka.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TopicEventType {
    CREATE, UPDATE, DELETE
}
