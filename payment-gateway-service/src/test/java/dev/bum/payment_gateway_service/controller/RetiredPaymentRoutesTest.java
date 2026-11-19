package dev.bum.payment_gateway_service.controller;

import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayCardPaymentController;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayVirtualAccountController;
import dev.bum.payment_gateway_service.service.gateway.card.GatewayCardApprovalService;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountCancellationService;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountIssuanceService;
import dev.bum.payment_gateway_service.controller.financial.CardController;
import dev.bum.payment_gateway_service.controller.financial.BankController;
import dev.bum.payment_gateway_service.service.financial.card.CardApprovalService;
import dev.bum.payment_gateway_service.service.financial.card.CardPaymentRefundService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RetiredPaymentRoutesTest {

    private final GatewayCardApprovalService approvals = mock(GatewayCardApprovalService.class);
    private final GatewayVirtualAccountIssuanceService issuance = mock(GatewayVirtualAccountIssuanceService.class);
    private final GatewayVirtualAccountCancellationService cancellation = mock(GatewayVirtualAccountCancellationService.class);
    private final CardApprovalService financialCards = mock(CardApprovalService.class);
    private final BankVirtualAccountDepositService deposits = mock(BankVirtualAccountDepositService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new GatewayCardPaymentController(approvals),
            new GatewayVirtualAccountController(issuance, cancellation),
            new CardController(financialCards, mock(CardPaymentRefundService.class)),
            new BankController(mock(BankVirtualAccountIssuanceService.class),
                    mock(BankVirtualAccountIssueCommandService.class), deposits,
                    mock(BankVirtualAccountCancellationService.class), mock(BankVirtualAccountRefundService.class)))
            .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
            .build();

    @Test
    void retiredCardRoutesCannotReachFinancialServices() throws Exception {
        mvc.perform(post("/api/v1/payments/card/approve")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/payments/card/refund")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/payments/refunds/internal/resolve")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payments/card/PAY-OLD")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payments/card/internal/PAY-OLD/approval-exists")).andExpect(status().isNotFound());
        verifyNoInteractions(approvals, issuance, cancellation);
    }

    @Test
    void retiredAccountRoutesCannotReachFinancialServices() throws Exception {
        for (String operation : new String[]{"issue", "refund", "cancel"}) {
            mvc.perform(post("/api/v1/payments/virtual-account/" + operation)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/payments/virtual-account/internal/PAY-OLD/status")).andExpect(status().isNotFound());
        verifyNoInteractions(approvals, issuance, cancellation);
    }

    @Test
    void currentMerchantCardStatusRouteRemainsMapped() throws Exception {
        mvc.perform(get("/api/v1/payments/card/gateway/PG-ID")).andExpect(status().isOk());
        verify(approvals).status(null, "PG-ID");
    }

    @Test
    void retiredFinancialRoutesAreNotMapped() throws Exception {
        for (String operation : new String[]{"approve", "refund"}) {
            mvc.perform(post("/internal/financial-simulations/cards/" + operation)).andExpect(status().isNotFound());
        }
        for (String operation : new String[]{"issue", "refund", "cancel"}) {
            mvc.perform(post("/internal/financial-simulations/banks/virtual-accounts/" + operation)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/internal/financial-simulations/cards/payments/PAY-OLD")).andExpect(status().isNotFound());
        mvc.perform(get("/internal/financial-simulations/banks/virtual-accounts/PAY-OLD")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/banks/virtual-accounts/legacy/deposit")).andExpect(status().isNotFound());
        verifyNoInteractions(financialCards, deposits);
    }

    @Test
    void bankDepositBusinessEntryRemainsMapped() throws Exception {
        mvc.perform(post("/api/v1/banks/virtual-accounts/deposit")
                .contentType("application/json")
                .content("{\"accountNumber\":\"ACCOUNT\",\"depositorName\":\"buyer\",\"amount\":100}"))
                .andExpect(status().isOk());
        verify(deposits).deposit(any());
    }
}
