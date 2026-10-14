package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.ticket_service.jpa.payment.payment.Payment;

import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.ticket_service.jpa.ticket.Ticket;

import jakarta.persistence.*;

import lombok.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

/** 환불 요청 시 확정한 대상 티켓, 금액 계산 근거와 환불 계좌. 재시도에서 변경하지 않는다. */
@Entity
@Table(name = "payment_refund_snapshots")
@Getter
@NoArgsConstructor
public class PaymentRefundSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentRefundSnapshotId;

    @Column(nullable = false, updatable = false)
    private int refundAmount;

    @Column(nullable = false, updatable = false)
    private int cancellationFeeAmount;

    @Column(nullable = false, updatable = false)
    private boolean fullCancellation;

    @Column(nullable = false, updatable = false)
    private int ticketAmount;

    @Column(nullable = false, updatable = false)
    private int discountAmount;

    @Column(nullable = false, updatable = false)
    private int deliveryRefundAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private BankCompany refundBankCompany;

    @Column(length = 50, updatable = false)
    private String refundAccountNumberMasked;

    @Column(length = 64, updatable = false)
    private String refundAccountNumberHash;

    @Column(length = 50, updatable = false)
    private String refundAccountHolder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime capturedAt;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paymentRefundSnapshotTicketId ASC")
    private List<PaymentRefundSnapshotTicket> tickets = new ArrayList<>();

    public PaymentRefundSnapshot(
            Payment payment,
            List<Ticket> tickets,
            int amount,
            int fee,
            boolean full,
            RefundAccountRequest account) {
        refundAmount = amount;
        cancellationFeeAmount = fee;
        fullCancellation = full;
        capturedAt = LocalDateTime.now();
        for (Ticket ticket : tickets)
            this.tickets.add(PaymentRefundSnapshotTicket.capture(this, ticket));
        ticketAmount = this.tickets.stream().mapToInt(PaymentRefundSnapshotTicket::getTicketPrice).sum();
        deliveryRefundAmount = full ? payment.getDeliveryFeeAmount() : 0;
        discountAmount = Math.max(0, ticketAmount + deliveryRefundAmount - amount - fee);
        if (account != null) {
            refundBankCompany = account.getBankCompany();
            refundAccountNumberMasked = mask(account.getAccountNumber());
            refundAccountNumberHash = hash(account.getAccountNumber());
            refundAccountHolder = account.getAccountHolder();
        }
    }

    // 기존 생성 API를 유지하되 요청 정보는 스냅샷 한 곳에만 저장한다.
    public PaymentRefundSnapshot(
            Payment payment,
            String ids,
            Integer amount,
            Integer fee,
            boolean full,
            BankCompany bank,
            String masked,
            String holder) {
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

    public String selectedTicketIds() {
        return tickets.stream()
                .map(t -> String.valueOf(t.getTicketId()))
                .collect(java.util.stream.Collectors.joining(","));
    }

    public List<PaymentRefundSnapshotTicket> getTickets() {
        return Collections.unmodifiableList(tickets);
    }

    public void validateAccount(RefundAccountRequest account) {
        if (account == null) return;
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

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.replaceAll("\\s+", "");
        return v.length() <= 4
                ? "*".repeat(v.length())
                : "*".repeat(v.length() - 4) + v.substring(v.length() - 4);
    }

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
