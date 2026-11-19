package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;

import java.net.URI;

public interface MerchantCardNotificationAdapter {

    /**
     * 등록된 가맹점 URL에 공통 규격으로 전송하고 업무 수락/확정 거절을 반환한다.
     */
    boolean send(URI url, String serviceToken, CardPaymentNotification notification);
}
