package dev.bum.audit_service.kafka;

import dev.bum.audit_service.login.LoginLogEntity;
import dev.bum.audit_service.login.LoginLogPersistenceService;
import dev.bum.common.kafka.login.LoginLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogConsumer {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final LoginLogPersistenceService persistenceService;

    @KafkaListener(
            topics = "${topic.login.log.name}",
            groupId = "${app.kafka.login-log.group-id}",
            containerFactory = "loginLogKafkaListenerContainerFactory"
    )
    public void consume(LoginLogEvent event) {
        try {
            LoginLogEntity savedLoginLog = persistenceService.save(event);
            log.info(
                    "Saved login log. id={}, eventId={}, loginId={}, result={}",
                    savedLoginLog.getId(),
                    event.getEventId(),
                    event.getLoginId(),
                    event.getResult()
            );
        } catch (DataIntegrityViolationException exception) {
            if (!isUniqueConstraintViolation(exception)) {
                throw exception;
            }

            log.info("Skipped duplicate login log event. eventId={}", event.getEventId());
        }
    }

    private boolean isUniqueConstraintViolation(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }
}
