package dev.bum.payment_gateway_service.security.card;

import dev.bum.common.service.paymentgateway.card.GatewayCardSessionResponse;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPayment;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPaymentStatus;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

@Component
public class GatewayCardSessionToken {

    public static final String HEADER = "X-PG-Payment-Session";

    // 사용자 JWT·가맹점 키와 분리한 PG 전용 서명 키.
    private final Key key;

    private final Clock clock;

    @Autowired
    public GatewayCardSessionToken(@Value("${app.checkout.signing-key:}") String secret) {

        this(secret, Clock.systemUTC());
    }

    GatewayCardSessionToken(String secret, Clock clock) {

        this.key = secret == null || secret.isBlank() ? null
                : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    // 승인 세션은 승인 기한 이내, 조회 세션은 기한 이후에도 최대 5분만 허용한다.
    public GatewayCardSessionResponse issue(GatewayPayment payment, boolean approvalAllowed) {

        requireKey();
        Instant now = clock.instant();
        Instant deadline = payment.getExpiresAt().atZone(ZoneId.of("Asia/Seoul")).toInstant();
        boolean approve = approvalAllowed && payment.getStatus() == GatewayPaymentStatus.READY && deadline.isAfter(now);
        Instant expires = now.plusSeconds(300);

        if (approve && deadline.isBefore(expires)) {
            expires = deadline;
        }

        String token = Jwts.builder().setIssuer("payment-gateway").setAudience("card-checkout")
                .setSubject(payment.getBuyerReference()).setIssuedAt(Date.from(now)).setExpiration(Date.from(expires))
                .claim("tokenType", "pg-card-session").claim("merchantId", payment.getMerchant().getMerchantId())
                .claim("paymentNo", payment.getMerchantPaymentNo()).claim("pgPaymentId", payment.getPgPaymentId()).claim("paymentMethod", "CREDIT_CARD")
                .claim("methodRevision", payment.getMethodRevision()).claim("scope", approve ? "APPROVE QUERY" : "QUERY")
                .signWith(key, SignatureAlgorithm.HS256).compact();

        return new GatewayCardSessionResponse(payment.getMerchant().getMerchantId(), payment.getPgPaymentId(),
                payment.getMerchantPaymentNo(), payment.getBuyerReference(), payment.getAmount(), token, expires, approve);
    }

    // JWT 사용자와 세션 사용자를 대조한다. 다른 용도의 JWT를 결제 세션으로 수용하지 않는다.
    public GatewayCardSessionClaims verify(String token, String userId, String paymentId, boolean approve) {

        requireKey();

        try {
            if (token == null || token.length() > 4096 || userId == null) {
                throw new IllegalArgumentException();
            }

            Claims claims = Jwts.parserBuilder().setSigningKey(key).requireIssuer("payment-gateway")
                    .requireAudience("card-checkout").setClock(() -> Date.from(clock.instant())).build()
                    .parseClaimsJws(token).getBody();
            Number merchant = claims.get("merchantId", Number.class);
            Number revision = claims.get("methodRevision", Number.class);
            String scope = claims.get("scope", String.class);

            if (claims.getExpiration() == null || claims.getIssuedAt() == null || claims.get("paymentNo", String.class) == null
                    || !"pg-card-session".equals(claims.get("tokenType")) || !"CREDIT_CARD".equals(claims.get("paymentMethod"))
                    || !userId.equals(claims.getSubject()) || !paymentId.equals(claims.get("pgPaymentId"))
                    || merchant == null || merchant.longValue() <= 0 || revision == null || revision.intValue() < 0
                    || (!"QUERY".equals(scope) && !"APPROVE QUERY".equals(scope))
                    || (approve && !"APPROVE QUERY".equals(scope))) {
                throw new IllegalArgumentException();
            }

            return new GatewayCardSessionClaims(merchant.longValue(), paymentId, claims.get("paymentNo", String.class), userId, revision.intValue(),
                    "APPROVE QUERY".equals(scope));
        } catch (JwtException | IllegalArgumentException failure) {
            throw new AccessDeniedException("유효한 본인 결제 세션이 필요합니다.");
        }
    }

    private void requireKey() {

        if (key == null) {
            throw new IllegalStateException("PG 카드 세션 서명 설정이 필요합니다.");
        }
    }
}
