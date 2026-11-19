package dev.bum.common.service.paymentgateway.notification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "paymentMethod", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CardPaymentNotification.class, name = "CREDIT_CARD"),
        @JsonSubTypes.Type(value = BankDepositNotification.class, name = "BANK_TRANSFER")
})
public sealed interface GatewayPaymentNotification permits CardPaymentNotification, BankDepositNotification {

    // 카드 승인·은행 입금을 같은 등록 URL로 보내는 공통 계약. 임의 클래스명을 역직렬화하지 않는다.
    String CONTRACT = "PAYMENT_NOTIFICATION_V1";
}
