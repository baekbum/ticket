package dev.bum.ticket_service.audit;


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
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILURE = "FAILURE";

    private final AuditLogPersistenceService persistenceService;

    @Value("${spring.application.name:ticket-service}")
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
        } finally {
            AuditContext.clear();
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
            String actorId = findActorId(request);
            String actorType = findActorType(request);
            String targetId = findTargetId(joinPoint.getArgs(), actorId);

            AuditLogEntity entity = AuditLogEntity.builder()
                    .occurredAt(LocalDateTime.now())
                    .serviceName(serviceName)
                    .actorType(actorType)
                    .actorId(actorId)
                    .action(auditLog.action())
                    .targetType(blankToNull(auditLog.targetType()))
                    .targetId(targetId)
                    .result(result)
                    .reason(reasonOf(throwable))
                    .ipAddress(ipAddressOf(request))
                    .userAgent(headerOf(request, "User-Agent"))
                    .requestId(valueOrNewUuid(firstHeaderOf(request, "X-Request-Id", "X-Correlation-Id")))
                    .traceId(valueOrNewTraceId(firstHeaderOf(request, "traceparent", "X-B3-TraceId")))
                    .beforeData(AuditContext.getBeforeData())
                    .afterData(AuditContext.getAfterData())
                    .metadata(metadataOf(joinPoint, actorId, targetId))
                    .build();

            persistenceService.save(entity);
        } catch (Exception e) {
            log.warn("Failed to save audit log. action={}", auditLog.action(), e);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes.getRequest();
        }

        return null;
    }

    private String findActorId(HttpServletRequest request) {
        String actorId = AuditContext.getActorId();
        if (StringUtils.hasText(actorId)) {
            return actorId;
        }

        return headerOf(request, "X-User-Id");
    }

    private String findActorType(HttpServletRequest request) {
        String actorType = AuditContext.getActorType();
        if (StringUtils.hasText(actorType)) {
            return actorType;
        }

        String role = headerOf(request, "X-User-Role");
        if ("ROLE_ADMIN".equals(role) || "ADMIN".equals(role)) {
            return "ADMIN";
        }

        if ("ROLE_USER".equals(role) || "USER".equals(role)) {
            return "USER";
        }

        return "ANONYMOUS";
    }

    private String findTargetId(Object[] args, String actorId) {
        for (Object arg : args) {
            if (arg instanceof Long) {
                return String.valueOf(arg);
            }
        }

        for (Object arg : args) {
            if (arg instanceof String && StringUtils.hasText((String) arg)) {
                return (String) arg;
            }

        }

        return actorId;
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
            return normalizeIp(forwardedFor.split(",")[0].trim());
        }

        return normalizeIp(request.getRemoteAddr());
    }

    private String normalizeIp(String ipAddress) {
        if ("0:0:0:0:0:0:0:1".equals(ipAddress) || "::1".equals(ipAddress)) {
            return "127.0.0.1";
        }

        return ipAddress;
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

    private String valueOrNewUuid(String value) {
        if (StringUtils.hasText(value)) {
            return value;
        }

        return UUID.randomUUID().toString();
    }

    private String valueOrNewTraceId(String value) {
        if (StringUtils.hasText(value)) {
            return value;
        }

        return UUID.randomUUID().toString().replace("-", "");
    }

    private String headerOf(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }

        return blankToNull(request.getHeader(name));
    }

    private Map<String, Object> metadataOf(ProceedingJoinPoint joinPoint, String actorId, String targetId) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("className", signature.getDeclaringType().getSimpleName());
        metadata.put("methodName", signature.getMethod().getName());

        if (StringUtils.hasText(actorId)) {
            metadata.put("actorId", actorId);
        }

        if (StringUtils.hasText(targetId)) {
            metadata.put("targetId", targetId);
        }

        return metadata;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
