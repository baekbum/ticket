package dev.bum.ticket_service.controller;

import dev.bum.ticket_service.controller.advice.GlobalExceptionHandler;
import dev.bum.ticket_service.exception.reservation.ReservationCancellationInProgressException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationCancellationErrorResponseTest {
    @Test
    void lockConflictReturnsConflictWithCancellationMessage() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConflictingCancellationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/test/cancellation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_CANCELLATION_IN_PROGRESS"))
                .andExpect(jsonPath("$.message").value(
                        "이미 해당 예매의 취소·환불 처리가 진행 중입니다. 잠시 후 다시 확인해 주세요."
                ));
    }

    @RestController
    static class ConflictingCancellationController {
        @PostMapping("/test/cancellation")
        void cancel() {
            throw new ReservationCancellationInProgressException(new CannotAcquireLockException("잠금 충돌"));
        }
    }
}
