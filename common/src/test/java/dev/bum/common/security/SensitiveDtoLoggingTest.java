package dev.bum.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.auth.dto.LoginRequest;
import dev.bum.common.service.user.user.dto.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDtoLoggingTest {
    private static final String PASSWORD = "test-only-password-marker!";

    @ParameterizedTest
    @MethodSource("sensitiveDtos")
    void credentials_are_absent_from_to_string_but_retained_in_json(Object dto, Map<String, String> secrets) throws Exception {
        String logValue = dto.toString();
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.valueToTree(dto);
        Object decoded = mapper.treeToValue(json, dto.getClass());
        var decodedJson = mapper.valueToTree(decoded);

        secrets.forEach((field, value) -> {
            assertThat(logValue).doesNotContain(value);
            assertThat(decoded.toString()).doesNotContain(value);
            assertThat(json.get(field).asText()).isEqualTo(value);
            assertThat(decodedJson.get(field).asText()).isEqualTo(value);
        });
    }

    static Stream<Arguments> sensitiveDtos() {
        VerifyMyPasswordRequest verify = new VerifyMyPasswordRequest();
        verify.setPassword(PASSWORD);
        WithdrawUserRequest withdraw = new WithdrawUserRequest();
        withdraw.setPassword(PASSWORD);
        ChangeMyPasswordRequest change = new ChangeMyPasswordRequest();
        change.setCurrentPassword("current-password-marker!");
        change.setNewPassword("new-password-marker!");
        change.setNewPasswordConfirm("confirm-password-marker!");

        return Stream.of(
                Arguments.of(new LoginRequest("member", PASSWORD), Map.of("password", PASSWORD)),
                Arguments.of(InsertUserRequest.builder().userId("member").password(PASSWORD).build(), Map.of("password", PASSWORD)),
                Arguments.of(UpdateUserRequest.builder().password(PASSWORD).build(), Map.of("password", PASSWORD)),
                Arguments.of(ValidatePasswordRequest.builder().userId("member").password(PASSWORD).build(), Map.of("password", PASSWORD)),
                Arguments.of(ResetPasswordRequest.builder().resetToken("reset-token-marker").password(PASSWORD).build(),
                        Map.of("password", PASSWORD, "resetToken", "reset-token-marker")),
                Arguments.of(verify, Map.of("password", PASSWORD)),
                Arguments.of(withdraw, Map.of("password", PASSWORD)),
                Arguments.of(change, Map.of("currentPassword", "current-password-marker!", "newPassword", "new-password-marker!",
                        "newPasswordConfirm", "confirm-password-marker!")),
                Arguments.of(UserDtoForEvent.builder().userId("member").password("encoded-password-marker").build(),
                        Map.of("password", "encoded-password-marker"))
        );
    }
}
