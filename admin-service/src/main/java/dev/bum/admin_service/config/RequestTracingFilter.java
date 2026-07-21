package dev.bum.admin_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-B3-TraceId";
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String TRACE_ID_ATTRIBUTE = "traceId";
    public static final String FORWARDED_FOR_ATTRIBUTE = "xForwardedFor";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = valueOrNewUuid(request.getHeader(REQUEST_ID_HEADER));
        String traceId = valueOrNewTraceId(request.getHeader(TRACE_ID_HEADER));
        String forwardedFor = forwardedForOf(request);

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        request.setAttribute(FORWARDED_FOR_ATTRIBUTE, forwardedFor);

        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        filterChain.doFilter(request, response);
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

    private String forwardedForOf(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        String remoteAddr = normalizeIp(request.getRemoteAddr());

        if (!StringUtils.hasText(forwardedFor)) {
            return remoteAddr;
        }

        if (!StringUtils.hasText(remoteAddr)) {
            return forwardedFor;
        }

        return forwardedFor + ", " + remoteAddr;
    }

    private String normalizeIp(String ipAddress) {
        if ("0:0:0:0:0:0:0:1".equals(ipAddress) || "::1".equals(ipAddress)) {
            return "127.0.0.1";
        }

        return ipAddress;
    }
}
