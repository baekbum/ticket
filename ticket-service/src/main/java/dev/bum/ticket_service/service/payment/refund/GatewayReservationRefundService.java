package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayReservationRefundService {

    // 최초 접수 커밋과 실행 트랜잭션을 분리하는 프록시 경계.
    private final GatewayReservationRefundCommands commands;

    /**
     * 신청 기록을 먼저 커밋하고 같은 요청 ID로 한 번만 PG를 호출한다.
     */
    public GatewayRefundActionResponse request(
            String userId,
            long reservationId,
            CancelReservationRequest request
    ) {

        long id = commands.prepare(userId, reservationId, request);
        return commands.execute(userId, reservationId, id, false);
    }

    /**
     * 브라우저가 보내는 새 금액 없이 최초 스냅샷 그대로 재시도한다.
     */
    public GatewayRefundActionResponse retry(String userId, long reservationId, long processId) {

        return commands.execute(userId, reservationId, processId, false);
    }

    /**
     * 철회 의도를 커밋한 후 은행의 종료 또는 기존 환불 성공을 확인한다.
     */
    public GatewayRefundActionResponse withdraw(String userId, long reservationId, long processId) {

        commands.prepareWithdrawal(userId, reservationId, processId);
        return commands.execute(userId, reservationId, processId, true);
    }

    /**
     * 사용자가 소유한 예매의 신청 이력을 제공한다.
     */
    public List<PaymentRefundProcessResponse> find(String userId, long reservationId) {

        return commands.findApplications(userId, reservationId);
    }
}
