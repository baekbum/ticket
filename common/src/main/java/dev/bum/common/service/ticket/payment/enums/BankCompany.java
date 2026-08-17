package dev.bum.common.service.ticket.payment.enums;

public enum BankCompany {
    KB("KB국민은행", "1111"),
    SHINHAN("신한은행", "2222"),
    WOORI("우리은행", "3333"),
    HANA("하나은행", "4444"),
    NH("NH농협은행", "5555"),
    IBK("IBK기업은행", "6666"),
    KAKAO("카카오뱅크", "7777"),
    TOSS("토스뱅크", "8888"),
    BUSAN("부산은행", "9999");

    private final String bankName;
    private final String accountPrefix;

    BankCompany(String bankName, String accountPrefix) {
        this.bankName = bankName;
        this.accountPrefix = accountPrefix;
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountPrefix() {
        return accountPrefix;
    }
}
