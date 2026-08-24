package dev.bum.ticket_service.service.payment;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessCondRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentRefundProcessService {

    private final PaymentRefundProcessJpaRepository paymentRefundProcessJpaRepository;

    @Transactional(readOnly = true)
    public CustomPageResponse<PaymentRefundProcessResponse> selectByCond(PaymentRefundProcessCondRequest cond) {
        PaymentRefundProcessCondRequest normalizedCond = cond != null ? cond : new PaymentRefundProcessCondRequest();
        PageRequest pageable = PageRequest.of(
                normalizedCond.getPage() != null ? normalizedCond.getPage() : 0,
                normalizedCond.getSize() != null ? normalizedCond.getSize() : 10,
                makeSort(normalizedCond.getSort())
        );

        Page<PaymentRefundProcessResponse> page = paymentRefundProcessJpaRepository
                .findAll(makeSpec(normalizedCond), pageable)
                .map(PaymentRefundProcess::toResponse);

        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount
    ) {
        PaymentRefundProcess process = paymentRefundProcessJpaRepository.save(
                PaymentRefundProcess.create(payment, selectedTickets, refundAmount, fullCancellation, refundAccount)
        );
        return process.getPaymentRefundProcessId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGatewaySucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::gatewaySucceeded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGatewayFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(process -> process.gatewayFailed(messageOf(throwable)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLocalSucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::localSucceeded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLocalFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(process -> process.localFailed(messageOf(throwable)));
    }

    private String messageOf(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }

    private Specification<PaymentRefundProcess> makeSpec(PaymentRefundProcessCondRequest cond) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("payment", JoinType.LEFT);
                root.fetch("reservation", JoinType.LEFT);
            }

            if (cond.getPaymentRefundProcessId() != null && cond.getPaymentRefundProcessId() > 0) {
                predicates.add(cb.equal(root.get("paymentRefundProcessId"), cond.getPaymentRefundProcessId()));
            }
            if (cond.getReservationId() != null && cond.getReservationId() > 0) {
                predicates.add(cb.equal(root.get("reservation").get("reservationId"), cond.getReservationId()));
            }
            if (cond.getPaymentId() != null && cond.getPaymentId() > 0) {
                predicates.add(cb.equal(root.get("payment").get("paymentId"), cond.getPaymentId()));
            }
            if (StringUtils.hasText(cond.getPaymentNo())) {
                predicates.add(cb.like(root.get("paymentNo"), "%" + cond.getPaymentNo() + "%"));
            }
            if (cond.getMethod() != null) {
                predicates.add(cb.equal(root.get("method"), cond.getMethod()));
            }
            if (StringUtils.hasText(cond.getStatus())) {
                predicates.add(cb.equal(root.get("status"), PaymentRefundProcessStatus.valueOf(cond.getStatus())));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Sort makeSort(List<String> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "paymentRefundProcessId");
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (String sort : sorts) {
            String[] parts = sort.split("-");
            if (parts.length == 2) {
                orders.add(new Sort.Order(Sort.Direction.fromString(parts[1]), parts[0]));
            }
        }

        return orders.isEmpty() ? Sort.by(Sort.Direction.DESC, "paymentRefundProcessId") : Sort.by(orders);
    }
}
