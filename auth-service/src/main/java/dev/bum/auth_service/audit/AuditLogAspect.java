package dev.bum.auth_service.audit;

import dev.bum.common.service.auth.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILURE = "FAILURE";

    private final AuditLogPersistenceService persistenceService;

    @Value("${spring.application.name:auth-service}")
    private String serviceName;

    @Around("@annotation(auditLog)")
    public Object writeAuditLog(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            saveAuditLog(joinPoint, auditLog, RESULT_SUCCESS, null);
            return result;
        } catch (Throwable throwable) {
            saveAuditLog(joinPoint, auditLog, RESULT_FAILURE, throwable);
            throw throwable;
        }
    }

    private void saveAuditLog(
            ProceedingJoinPoint joinPoint,
            AuditLog auditLog,
            String result,
            Throwable throwable
    ) {
        try {
            HttpServletRequest request = currentRequest();
            String actorId = findActorId(joinPoint.getArgs());

            AuditLogEntity entity = AuditLogEntity.builder()
                    .occurredAt(LocalDateTime.now())
                    .serviceName(serviceName)
                    .actorType(StringUtils.hasText(actorId) ? "USER" : "ANONYMOUS")
                    .actorId(actorId)
                    .action(auditLog.action())
                    .targetType(blankToNull(auditLog.targetType()))
                    .targetId(actorId)
                    .result(result)
                    .reason(reasonOf(throwable))
                    .ipAddress(ipAddressOf(request))
                    .userAgent(headerOf(request, "User-Agent"))
                    .requestId(firstHeaderOf(request, "X-Request-Id", "X-Correlation-Id"))
                    .traceId(firstHeaderOf(request, "traceparent", "X-B3-TraceId"))
                    .metadata(metadataOf(joinPoint))
                    .build();

            persistenceService.save(entity);
        } catch (Exception e) {
            log.warn("Failed to save audit log. action={}", auditLog.action(), e);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }

        return null;
    }

    private String findActorId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof LoginRequest loginRequest) {
                return loginRequest.getUserId();
            }
        }

        return null;
    }

    private String reasonOf(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        String message = throwable.getMessage();
        if (!StringUtils.hasText(message)) {
            return throwable.getClass().getSimpleName();
        }

        String reason = throwable.getClass().getSimpleName() + ": " + message;
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    private String ipAddressOf(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String firstHeaderOf(HttpServletRequest request, String... names) {
        if (request == null) {
            return null;
        }

        for (String name : names) {
            String value = request.getHeader(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return null;
    }

    private String headerOf(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }

        return blankToNull(request.getHeader(name));
    }

    private Map<String, Object> metadataOf(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("className", signature.getDeclaringType().getSimpleName());
        metadata.put("methodName", signature.getMethod().getName());

        String actorId = findActorId(joinPoint.getArgs());
        if (StringUtils.hasText(actorId)) {
            metadata.put("loginUserId", actorId);
        }

        return metadata;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
