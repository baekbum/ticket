package dev.bum.ticket_service.controller;

import dev.bum.ticket_service.controller.advice.GatewayRefundExceptionHandler;
import dev.bum.ticket_service.controller.reservation.reservation.ReservationRefundController;
import dev.bum.ticket_service.exception.reservation.GatewayRefundRequestException;
import dev.bum.ticket_service.service.payment.refund.GatewayReservationRefundService;
import org.junit.jupiter.api.*;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationRefundControllerTest {

    GatewayReservationRefundService service=mock(GatewayReservationRefundService.class);
    MockMvc mvc;

    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner",null));
        mvc=MockMvcBuilders.standaloneSetup(new ReservationRefundController(service))
                .setControllerAdvice(new GatewayRefundExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void nowaitConflictsReturn409WithTheStableProcessingCode() throws Exception {
        when(service.retry("owner",1L,2L)).thenThrow(new CannotAcquireLockException("internal SQL detail"));
        mvc.perform(post("/api/v1/reservation/1/refunds/2/retry")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_PROCESSING"))
                .andExpect(jsonPath("$.message").value("해당 예매에 대한 처리가 진행 중입니다. 잠시 후 다시 시도해 주세요."));
        verify(service,times(1)).retry("owner",1L,2L);
    }

    @Test void unconfirmedResultsAre503WithoutReturningInternalDetails() throws Exception {
        when(service.withdraw("owner",1L,2L)).thenThrow(new GatewayRefundRequestException(
                "GATEWAY_REFUND_UNCONFIRMED",503,"같은 신청으로 다시 시도해주세요.",null));
        mvc.perform(post("/api/v1/reservation/1/refunds/2/withdraw")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GATEWAY_REFUND_UNCONFIRMED"));
    }
}
