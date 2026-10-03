package dev.bum.auth_service.audit;

import dev.bum.auth_service.kafka.LoginLogProducer;
import dev.bum.auth_service.service.AuthService;
import dev.bum.common.kafka.audit.AuditLogProducer;
import dev.bum.common.kafka.login.LoginLogEvent;
import dev.bum.common.service.auth.dto.LoginRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditLogAspectTest {

    @Test
    @DisplayName("로그인 액션은 전용 로그인 로그로만 발행")
    void login_action_publishes_only_login_log() throws Throwable {
        AuditLogProducer auditLogProducer = mock(AuditLogProducer.class);
        LoginLogProducer loginLogProducer = mock(LoginLogProducer.class);
        AuditLogAspect aspect = new AuditLogAspect(auditLogProducer, loginLogProducer);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        AuditLog auditLog = mock(AuditLog.class);
        LoginRequest request = new LoginRequest("user01", "password");

        given(joinPoint.proceed()).willReturn("token");
        given(joinPoint.getArgs()).willReturn(new Object[]{request});
        given(joinPoint.getSignature()).willReturn(signature);
        given(signature.getDeclaringType()).willReturn(AuthService.class);
        given(signature.getMethod()).willReturn(
                AuthService.class.getMethod("LoginAndCreateToken", LoginRequest.class)
        );
        given(auditLog.action()).willReturn("LOGIN");
        given(auditLog.targetType()).willReturn("AUTH");

        Object result = aspect.writeAuditLog(joinPoint, auditLog);

        ArgumentCaptor<LoginLogEvent> eventCaptor = ArgumentCaptor.forClass(LoginLogEvent.class);
        verify(loginLogProducer).send(eventCaptor.capture());
        verifyNoInteractions(auditLogProducer);
        assertThat(result).isEqualTo("token");
        assertThat(eventCaptor.getValue().getLoginId()).isEqualTo("user01");
        assertThat(eventCaptor.getValue().getResult()).isEqualTo("SUCCESS");
    }
}
