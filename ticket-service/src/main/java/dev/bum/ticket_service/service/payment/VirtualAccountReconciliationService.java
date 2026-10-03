package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.ticket_service.audit.AuditContext;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountReconciliationService {

    private static final int PAGE_SIZE = 20;

    private final PaymentJpaRepository payments;
    private final PaymentGatewayVirtualAccountClient gateway;
    private final VirtualAccountReconciliationCompletionService completion;
    private final VirtualAccountReconciliationValidator validator;

    @Transactional(readOnly = true)
    public CustomPageResponse<PaymentResponse> list(int pageNumber, String paymentNo, PaymentStatus status) {
        String search = paymentNo == null ? "" : paymentNo.trim();
        var pageable = PageRequest.of(pageNumber, PAGE_SIZE,
                Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("paymentId")));
        Page<Payment> page;
        if (search.isEmpty()) {
            page = status == null
                    ? payments.findByMethod(PaymentMethod.BANK_TRANSFER, pageable)
                    : payments.findByMethodAndStatus(PaymentMethod.BANK_TRANSFER, status, pageable);
        } else {
            page = status == null
                    ? payments.findByMethodAndPaymentNoContainingIgnoreCase(PaymentMethod.BANK_TRANSFER, search, pageable)
                    : payments.findByMethodAndStatusAndPaymentNoContainingIgnoreCase(
                            PaymentMethod.BANK_TRANSFER, status, search, pageable);
        }
        return CustomPageResponse.of(page.map(Payment::toResponse).getContent(),
                page.getSize(), page.getNumber(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public VirtualAccountReconciliationResponse inspect(String paymentNo) {
        Payment payment = payments.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("무통장 결제만 조회할 수 있습니다.");
        }
        GatewayVirtualAccountStatusResponse status = gateway.status(paymentNo);
        String problem = validator.problem(payment, status);
        return new VirtualAccountReconciliationResponse(
                payment.toResponse(), status, problem == null,
                problem == null ? "입금 정보를 확인했습니다. 결제 완료 재반영이 가능합니다." : problem
        );
    }

    public PaymentResponse complete(String paymentNo, String adminId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("처리 사유를 입력해주세요.");
        }
        GatewayVirtualAccountStatusResponse status = gateway.status(paymentNo);
        PaymentResponse result = completion.complete(paymentNo, status);
        AuditContext.setAfterData(Map.of(
                "paymentNo", paymentNo,
                "reason", reason.trim(),
                "gatewayStatus", status.status().name(),
                "paymentStatus", result.getStatus().name()
        ));
        log.info("관리자 가상계좌 결제 재반영: paymentNo={}, adminId={}, status={}",
                paymentNo, adminId, result.getStatus());
        return result;
    }

}
