package dev.bum.payment_gateway_service.service.refund;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefund;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefundJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class GatewayRefundProcessServiceTest {

    @Mock
    private GatewayRefundJpaRepository refunds;

    @Mock
    private GatewayRefundCreator creator;

    @Mock
    private GatewayRefundFailureRecorder failureRecorder;

    @InjectMocks
    private GatewayRefundProcessService service;

    @Test
    void failure_recording_log_omits_sensitive_exception_messages_and_causes() {
        String accountNumber = "123-456-7890";
        String accountHolder = "홍길동";
        String sensitiveMessage = "계좌 " + accountNumber + ", 예금주 " + accountHolder;
        GatewayVirtualAccountRefundRequest request = GatewayVirtualAccountRefundRequest.builder()
                .paymentRefundProcessId(77L).paymentNo("PAY-LOG-77").refundBankCompany(BankCompany.KB)
                .refundAccountNumber(accountNumber).refundAccountHolder(accountHolder)
                .refundAmount(BigDecimal.valueOf(90000)).build();
        GatewayRefund refund = GatewayRefund.requested(request);
        given(refunds.findById(77L)).willReturn(Optional.of(refund));
        doThrow(new IllegalStateException(sensitiveMessage, new IllegalArgumentException(sensitiveMessage)))
                .when(failureRecorder).record(77L, "PAY-LOG-77", PaymentMethod.BANK_TRANSFER, null, sensitiveMessage);
        Logger logger = (Logger) LoggerFactory.getLogger(GatewayRefundProcessService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.prepare(request);
            refund.failed(sensitiveMessage);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }

            then(failureRecorder).should().record(77L, "PAY-LOG-77", PaymentMethod.BANK_TRANSFER, null, sensitiveMessage);
            assertThat(logs.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                        .contains("paymentNo=PAY-LOG-77", "processId=77", "errorType=IllegalStateException")
                        .doesNotContain(accountNumber, accountHolder);
                assertThat(event.getArgumentArray()).doesNotContain(accountNumber, accountHolder);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            logger.detachAppender(logs);
            logs.stop();
        }
    }
}
