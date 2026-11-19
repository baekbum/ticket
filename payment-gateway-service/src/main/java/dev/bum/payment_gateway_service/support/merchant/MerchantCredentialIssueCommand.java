package dev.bum.payment_gateway_service.support.merchant;

import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class MerchantCredentialIssueCommand {

    // HTTP 관리 API를 공개하지 않고 PG 운영자가 DB 권한으로 실행하는 일회성 발급 명령이다.
    private MerchantCredentialIssueCommand() {
    }

    // 원문 키는 로그가 아닌 새 로컬 설정 파일에만 제공한다. 기존 파일이나 키를 자동 덮어쓰지 않는다.
    public static void main(String[] args) throws Exception {

        if (args.length != 3 || !args[0].matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("가맹점 코드, 키 접두사, 새 출력 파일 경로가 필요합니다.");
        }

        long merchantId = Long.parseLong(args[0]);
        String credentialKey = MerchantCredentialKey.generate(args[1]);
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        String config = "app.payment-gateway.merchant-id=" + merchantId + System.lineSeparator()
                + "app.payment-gateway.credential-key=" + credentialKey + System.lineSeparator();

        if (Files.exists(output) || !Files.isDirectory(output.getParent())) {
            throw new IllegalArgumentException("출력 파일은 기존 디렉터리 아래의 새 파일이어야 합니다.");
        }

        try (Connection connection = DriverManager.getConnection(requiredEnvironment("PG_CREDENTIAL_DB_URL"),
                requiredEnvironment("PG_CREDENTIAL_DB_USER"), requiredEnvironment("PG_CREDENTIAL_DB_PASSWORD"))) {
            connection.setAutoCommit(false);

            try (PreparedStatement lock = connection.prepareStatement(
                    "select credential_hash from merchants where merchant_id = ? for update")) {
                lock.setLong(1, merchantId);

                try (ResultSet merchant = lock.executeQuery()) {
                    if (!merchant.next()) {
                        throw new IllegalArgumentException("발급 대상 가맹점이 없습니다.");
                    }
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "update merchants set credential_hash = ?, updated_at = current_timestamp where merchant_id = ?")) {
                    update.setString(1, MerchantCredentialKey.hash(credentialKey));
                    update.setLong(2, merchantId);
                    update.executeUpdate();
                }

                Files.writeString(output, config, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                // 커밋 응답 유실 가능성이 있으므로 생성된 키 파일은 지우지 않고 운영자가 DB 해시와 대조한다.
                throw exception;
            }
        }

        System.out.println("가맹점 키를 발급했습니다. 원문 키 설정 파일: " + output);
    }

    // DB 접속 비밀값은 명령행·소스·로그 대신 발급 프로세스 환경으로만 받는다.
    private static String requiredEnvironment(String name) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException("발급 명령에 필요한 DB 환경 설정이 없습니다: " + name);
        }

        return value;
    }
}
