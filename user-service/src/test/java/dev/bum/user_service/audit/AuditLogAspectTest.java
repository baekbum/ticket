package dev.bum.user_service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.audit.AuditLogEvent;
import dev.bum.common.kafka.audit.AuditLogProducer;
import dev.bum.common.service.user.user.dto.ValidatePasswordRequest;
import dev.bum.user_service.exception.PasswordIncorrectException;
import dev.bum.user_service.service.user.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {
    @InjectMocks AuditLogAspect aspect;
    @Mock AuditLogProducer producer;
    @Mock ProceedingJoinPoint joinPoint;
    @Mock MethodSignature signature;

    @AfterEach
    void cleanup() {
        AuditContext.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void admin_password_verification_audits_target_account_without_credentials(boolean failed) throws Throwable {
        var method = UserService.class.getMethod("validateInfo", ValidatePasswordRequest.class);
        var request = ValidatePasswordRequest.builder().userId("member").password("password-marker").build();
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(UserService.class);
        if (failed) {
            when(joinPoint.proceed()).thenThrow(new PasswordIncorrectException("비밀번호가 일치하지 않습니다."));
            assertThatThrownBy(() -> aspect.writeAuditLog(joinPoint, method.getAnnotation(AuditLog.class)))
                    .isInstanceOf(PasswordIncorrectException.class);
        } else {
            aspect.writeAuditLog(joinPoint, method.getAnnotation(AuditLog.class));
        }

        var event = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(producer).send(event.capture());
        assertThat(event.getValue().getTargetId()).isEqualTo("member");
        assertThat(event.getValue().getMetadata()).containsEntry("targetId", "member");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(event.getValue()))
                .doesNotContain("password-marker", "token-marker");
    }
}
