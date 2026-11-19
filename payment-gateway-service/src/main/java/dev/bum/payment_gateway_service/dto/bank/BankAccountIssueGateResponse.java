package dev.bum.payment_gateway_service.dto.bank;

// closed=true는 미발급 확인과 향후 발급 차단이 함께 커밋됐다는 의미다.
public record BankAccountIssueGateResponse(BankVirtualAccountIssueRequest request, boolean closed,
                                          BankVirtualAccountIssueResponse account) { }
