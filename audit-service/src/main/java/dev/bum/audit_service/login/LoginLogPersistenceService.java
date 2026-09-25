package dev.bum.audit_service.login;

import dev.bum.common.kafka.login.LoginLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginLogPersistenceService {

    private final LoginLogJpaRepository repository;

    @Transactional
    public LoginLogEntity save(LoginLogEvent event) {
        return repository.saveAndFlush(LoginLogEntity.from(event));
    }
}
