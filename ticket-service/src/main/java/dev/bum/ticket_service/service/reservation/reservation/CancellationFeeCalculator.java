package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 이용 안내에 고지된 관람 취소 수수료를 계산한다.
 */
@NoArgsConstructor
public final class CancellationFeeCalculator {

    private static final int BOOKING_GRACE_PERIOD_DAYS = 7;

    public static int calculateRefundAmount(
            Reservation reservation,
            int refundAmountBeforeFee,
            List<Ticket> cancelledTickets,
            LocalDateTime cancelledAt
    ) {
        if (refundAmountBeforeFee <= 0) {
            throw new IllegalArgumentException("수수료 적용 전 환불 금액은 0보다 커야 합니다.");
        }
        if (reservation == null || reservation.getEvent() == null
                || reservation.getEvent().getEventDateTime() == null) {
            throw new IllegalArgumentException("관람 일시를 확인할 수 없습니다.");
        }
        if (cancelledTickets == null || cancelledTickets.isEmpty()) {
            throw new IllegalArgumentException("취소 수수료를 계산할 티켓이 없습니다.");
        }
        if (cancelledAt == null) {
            throw new IllegalArgumentException("취소 시각을 확인할 수 없습니다.");
        }

        int feeRate = resolveFeeRate(reservation, cancelledAt);
        int cancelledTicketAmount = cancelledTickets.stream()
                .mapToInt(ticket -> ticket.getPrice() != null ? ticket.getPrice() : 0)
                .sum();
        int cancellationFee = BigDecimal.valueOf(cancelledTicketAmount)
                .multiply(BigDecimal.valueOf(feeRate))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .intValueExact();

        return Math.max(refundAmountBeforeFee - cancellationFee, 0);
    }

    public static CancellationRefundCalculation calculate(
            Reservation reservation,
            Payment payment,
            List<Ticket> activeTickets,
            List<Ticket> cancelledTickets,
            List<ReservationDiscount> discounts,
            boolean fullCancellation,
            LocalDateTime cancelledAt
    ) {
        validateCancellationContext(reservation, cancelledTickets, cancelledAt);
        if (payment == null) {
            throw new IllegalArgumentException("결제 정보를 찾을 수 없습니다.");
        }
        if (activeTickets == null || activeTickets.isEmpty()) {
            throw new IllegalArgumentException("취소 가능한 티켓이 없습니다.");
        }

        int activeTicketAmount = sumTicketAmount(activeTickets);
        int cancelledTicketAmount = sumTicketAmount(cancelledTickets);
        if (cancelledTicketAmount <= 0 || cancelledTicketAmount > activeTicketAmount) {
            throw new IllegalArgumentException("취소할 티켓 금액을 계산할 수 없습니다.");
        }

        int remainingTicketAmount = activeTicketAmount - cancelledTicketAmount;
        DiscountCalculation discountCalculation = calculateDiscountDeduction(
                discounts, activeTicketAmount, cancelledTicketAmount, remainingTicketAmount, fullCancellation);
        int deliveryRefundAmount = fullCancellation ? payment.getDeliveryFeeAmount() : 0;
        int ticketRefundAmount = hasPositiveDiscount(discounts)
                ? cancelledTicketAmount - discountCalculation.deductionAmount()
                : calculateTicketRefundWithoutDiscountSnapshot(
                        payment, activeTicketAmount, cancelledTicketAmount, fullCancellation);
        int amountBeforeCancellationFee = Math.max(ticketRefundAmount + deliveryRefundAmount, 0);
        int settleableAmount = Math.min(amountBeforeCancellationFee, payment.getRefundableAmount());
        int cancellationFeeAmount = Math.min(
                calculateCancellationFee(reservation, cancelledTicketAmount, cancelledAt),
                settleableAmount);

        return new CancellationRefundCalculation(
                settleableAmount - cancellationFeeAmount,
                cancellationFeeAmount,
                deliveryRefundAmount,
                discountCalculation.deductionAmount(),
                discountCalculation.restoreCoupon());
    }

    private static void validateCancellationContext(
            Reservation reservation,
            List<Ticket> cancelledTickets,
            LocalDateTime cancelledAt
    ) {
        if (reservation == null || reservation.getEvent() == null
                || reservation.getEvent().getEventDateTime() == null) {
            throw new IllegalArgumentException("관람 일시를 확인할 수 없습니다.");
        }
        if (cancelledTickets == null || cancelledTickets.isEmpty()) {
            throw new IllegalArgumentException("취소 수수료를 계산할 티켓이 없습니다.");
        }
        if (cancelledAt == null) {
            throw new IllegalArgumentException("취소 시각을 확인할 수 없습니다.");
        }
    }

    private static DiscountCalculation calculateDiscountDeduction(
            List<ReservationDiscount> discounts,
            int activeTicketAmount,
            int cancelledTicketAmount,
            int remainingTicketAmount,
            boolean fullCancellation
    ) {
        int deductionAmount = 0;
        boolean restoreCoupon = false;

        for (ReservationDiscount discount : discounts != null ? discounts : List.<ReservationDiscount>of()) {
            int discountAmount = discount.getDiscountAmount() != null ? discount.getDiscountAmount() : 0;
            if (discountAmount <= 0) {
                continue;
            }

            if (discount.getUserCoupon() == null) {
                deductionAmount += fullCancellation
                        ? discountAmount
                        : proportionalAmount(discountAmount, cancelledTicketAmount, activeTicketAmount);
                continue;
            }

            if (discount.getUserCoupon().getStatus() != UserCouponStatus.USED) {
                continue;
            }

            Integer minOrderAmount = discount.getUserCoupon().getCoupon() != null
                    ? discount.getUserCoupon().getCoupon().getMinOrderAmount()
                    : null;
            boolean couponRemainsApplicable = !fullCancellation
                    && (minOrderAmount == null || remainingTicketAmount >= minOrderAmount);
            if (!couponRemainsApplicable) {
                deductionAmount += discountAmount;
                restoreCoupon = true;
            }
        }

        return new DiscountCalculation(deductionAmount, restoreCoupon);
    }

    private static int calculateCancellationFee(
            Reservation reservation,
            int cancelledTicketAmount,
            LocalDateTime cancelledAt
    ) {
        int feeRate = resolveFeeRate(reservation, cancelledAt);
        return BigDecimal.valueOf(cancelledTicketAmount)
                .multiply(BigDecimal.valueOf(feeRate))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .intValueExact();
    }

    private static int proportionalAmount(int amount, int selectedAmount, int totalAmount) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(selectedAmount))
                .divide(BigDecimal.valueOf(totalAmount), 0, RoundingMode.DOWN)
                .intValueExact();
    }

    private static int calculateTicketRefundWithoutDiscountSnapshot(
            Payment payment,
            int activeTicketAmount,
            int cancelledTicketAmount,
            boolean fullCancellation
    ) {
        int refundableTicketPool = Math.max(payment.getRefundableAmount() - payment.getDeliveryFeeAmount(), 0);
        if (fullCancellation) {
            return Math.min(activeTicketAmount, refundableTicketPool);
        }
        return proportionalAmount(
                Math.min(activeTicketAmount, refundableTicketPool),
                cancelledTicketAmount,
                activeTicketAmount);
    }

    private static boolean hasPositiveDiscount(List<ReservationDiscount> discounts) {
        return discounts != null && discounts.stream()
                .anyMatch(discount -> discount.getDiscountAmount() != null && discount.getDiscountAmount() > 0);
    }

    private static int sumTicketAmount(List<Ticket> tickets) {
        return tickets.stream()
                .mapToInt(ticket -> ticket.getPrice() != null ? ticket.getPrice() : 0)
                .sum();
    }

    /**
     * 환불 수수료 계산
     * @param reservation
     * @param cancelledAt
     * @return
     */
    static int resolveFeeRate(Reservation reservation, LocalDateTime cancelledAt) {
        LocalDate eventDate = reservation.getEvent().getEventDateTime().toLocalDate();
        long daysBeforeEvent = ChronoUnit.DAYS.between(cancelledAt.toLocalDate(), eventDate);
        if (daysBeforeEvent < 1) {
            throw new IllegalArgumentException("관람일 당일 또는 이후에는 취소할 수 없습니다.");
        }

        if (reservation.getReservedAt() != null
                && !cancelledAt.isAfter(reservation.getReservedAt().plusDays(BOOKING_GRACE_PERIOD_DAYS))) {
            return 0;
        }

        if (daysBeforeEvent >= 7 && daysBeforeEvent <= 9) {
            return 10;
        }
        if (daysBeforeEvent >= 3 && daysBeforeEvent <= 6) {
            return 20;
        }
        if (daysBeforeEvent >= 1 && daysBeforeEvent <= 2) {
            return 30;
        }
        return 0;
    }

    public record CancellationRefundCalculation(
            int refundAmount,
            int cancellationFeeAmount,
            int deliveryRefundAmount,
            int discountDeductionAmount,
            boolean restoreCoupon
    ) {
    }

    private record DiscountCalculation(int deductionAmount, boolean restoreCoupon) {
    }
}
