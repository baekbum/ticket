package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.ticket.Ticket;

import jakarta.persistence.*;

import lombok.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 환불 요청 시 확정한 대상 티켓, 금액 계산 근거와 환불 계좌. 재시도에서 변경하지 않는다. */
@Entity
@Table(name = "payment_refund_snapshots")
@Getter
@NoArgsConstructor
public class PaymentRefundSnapshot {

    // 환불 시점 스냅샷 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentRefundSnapshotId;

    // 환불 금액.
    @Column(nullable = false, updatable = false)
    private int refundAmount;

    // 취소 수수료 금액.
    @Column(nullable = false, updatable = false)
    private int cancellationFeeAmount;

    // 전체 취소 여부.
    @Column(nullable = false, updatable = false)
    private boolean fullCancellation;

    // 티켓 금액.
    @Column(nullable = false, updatable = false)
    private int ticketAmount;

    // 적용된 할인 금액.
    @Column(nullable = false, updatable = false)
    private int discountAmount;

    // 배송비 환불 금액.
    @Column(nullable = false, updatable = false)
    private int deliveryRefundAmount;

    // 환불받을 은행.
    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private BankCompany refundBankCompany;

    // 마스킹한 환불 계좌 번호.
    @Column(length = 50, updatable = false)
    private String refundAccountNumberMasked;

    // 환불 계좌 번호 검증용 해시.
    @Column(length = 64, updatable = false)
    private String refundAccountNumberHash;

    // 환불 계좌 예금주.
    @Column(length = 50, updatable = false)
    private String refundAccountHolder;

    // 스냅샷 생성 시각.
    @Column(nullable = false, updatable = false)
    private LocalDateTime capturedAt;

    // 쿠폰 복원 여부.
    @Column(nullable = false, updatable = false)
    private boolean restoreCoupon;

    // 티켓 목록.
    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paymentRefundSnapshotTicketId ASC")
    private List<PaymentRefundSnapshotTicket> tickets = new ArrayList<>();

    // 전달받은 값으로 환불 스냅샷 객체를 초기화한다.
    public PaymentRefundSnapshot(
            Payment payment,
            List<Ticket> tickets,
            int amount,
            int fee,
            boolean full,
            RefundAccountRequest account
    ) {

        this(payment, tickets, amount, fee, full, account, LocalDateTime.now(), false);
    }

    // 전달받은 값으로 환불 스냅샷 객체를 초기화한다.
    @Builder
    public PaymentRefundSnapshot(
            Payment payment,
            List<Ticket> tickets,
            int refundAmount,
            int cancellationFeeAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount,
            LocalDateTime capturedAt,
            boolean restoreCoupon
    ) {

        this.refundAmount = refundAmount;
        this.cancellationFeeAmount = cancellationFeeAmount;
        this.fullCancellation = fullCancellation;
        this.capturedAt = Objects.requireNonNull(capturedAt);
        this.restoreCoupon = restoreCoupon;
        for (Ticket ticket : tickets)
            this.tickets.add(PaymentRefundSnapshotTicket.capture(this, ticket));
        ticketAmount = this.tickets.stream().mapToInt(PaymentRefundSnapshotTicket::getTicketPrice).sum();
        deliveryRefundAmount = fullCancellation ? payment.getDeliveryFeeAmount() : 0;
        discountAmount = Math.max(0, ticketAmount + deliveryRefundAmount - refundAmount - cancellationFeeAmount);
        if (refundAccount != null) {
            refundBankCompany = refundAccount.getBankCompany();
            refundAccountNumberMasked = mask(refundAccount.getAccountNumber());
            refundAccountNumberHash = hash(refundAccount.getAccountNumber());
            refundAccountHolder = refundAccount.getAccountHolder();
        }
    }

    // 전달받은 값으로 환불 스냅샷 객체를 초기화한다.
    // 기존 생성 API를 유지하되 요청 정보는 스냅샷 한 곳에만 저장한다.
    @Builder(builderMethodName = "legacyBuilder", builderClassName = "LegacyBuilder")
    public PaymentRefundSnapshot(
            Payment payment,
            String ids,
            Integer amount,
            Integer fee,
            boolean full,
            BankCompany bank,
            String masked,
            String holder
    ) {

        this(
                payment,
                resolveTickets(payment, ids),
                amount == null ? 0 : amount,
                fee == null ? 0 : fee,
                full,
                null);
        refundBankCompany = bank;
        refundAccountNumberMasked = masked;
        refundAccountHolder = holder;
    }

    // 요청에 대응하는 처리 대상 티켓을 결정한다.
    private static List<Ticket> resolveTickets(Payment payment, String ids) {

        if (ids == null || ids.isBlank()) return List.of();
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(Long::valueOf)
                .map(
                        id -> {
                            if (payment != null
                                    && payment.getReservation() != null
                                    && payment.getReservation().getTickets() != null)
                                for (Ticket ticket : payment.getReservation().getTickets())
                                    if (Objects.equals(id, ticket.getTicketId())) return ticket;
                            return Ticket.builder().ticketId(id).build();
                        })
                .toList();
    }

    // 선택한 티켓의 식별자 목록을 구성한다.
    public String selectedTicketIds() {

        return tickets.stream()
                .map(t -> String.valueOf(t.getTicketId()))
                .collect(Collectors.joining(","));
    }

    // 티켓 목록 값을 반환한다.
    public List<PaymentRefundSnapshotTicket> getTickets() {

        return Collections.unmodifiableList(tickets);
    }

    // 환불 계좌 정보가 유효한지 검증한다.
    public void validateAccount(RefundAccountRequest account) {

        if (account == null) {
            if (refundBankCompany != null || refundAccountHolder != null || refundAccountNumberHash != null) {
                throw new IllegalArgumentException("기존 환불 요청의 계좌 정보가 필요합니다.");
            }
            return;
        }
        boolean numberMatches =
                refundAccountNumberHash == null
                        ? Objects.equals(
                                refundAccountNumberMasked, mask(account.getAccountNumber()))
                        : Objects.equals(refundAccountNumberHash, hash(account.getAccountNumber()));
        if (refundBankCompany != account.getBankCompany()
                || !numberMatches
                || !Objects.equals(refundAccountHolder, account.getAccountHolder()))
            throw new IllegalArgumentException("기존 환불 요청의 계좌 정보를 변경할 수 없습니다.");
    }

    // 민감한 정보의 일부를 가린다.
    private static String mask(String value) {

        if (value == null || value.isBlank()) return null;
        String v = value.replaceAll("\\s+", "");
        return v.length() <= 4
                ? "*".repeat(v.length())
                : "*".repeat(v.length() - 4) + v.substring(v.length() - 4);
    }

    // 동일 값 여부를 검증할 해시를 계산한다.
    private static String hash(String value) {

        if (value == null) return null;
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
