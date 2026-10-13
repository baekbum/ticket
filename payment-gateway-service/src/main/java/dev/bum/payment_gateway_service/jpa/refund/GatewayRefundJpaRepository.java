package dev.bum.payment_gateway_service.jpa.refund;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface GatewayRefundJpaRepository extends JpaRepository<GatewayRefund, Long> {
    @Query("select coalesce(sum(r.refundAmount), 0) from GatewayRefund r where r.paymentNo = :paymentNo and r.method = :method")
    BigDecimal sumRefundAmount(@Param("paymentNo") String paymentNo, @Param("method") PaymentMethod method);
}
