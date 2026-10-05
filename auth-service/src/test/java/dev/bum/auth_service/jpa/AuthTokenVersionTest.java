package dev.bum.auth_service.jpa;

import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.user.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AuthTokenVersionTest {
    @ParameterizedTest
    @ValueSource(strings = {"password", "reset", "role", "withdrawal", "blacklist"})
    void security_change_revokes_previous_version_once(String change) {
        Auth auth = Auth.builder().id(1L).userId("member").password("old-hash").role(UserRole.ROLE_USER).build();
        UserDtoForEvent event = new UserDtoForEvent();
        switch (change) {
            case "password" -> event.setPassword("new-hash");
            case "reset" -> { event.setPassword("new-hash"); event.setPasswordReset(true); }
            case "role" -> event.setRole("ROLE_ADMIN");
            case "withdrawal" -> event.setStatus("WITHDRAWN");
            case "blacklist" -> event.setIsBlacklisted(true);
        }
        auth.updateInfo(event);
        assertThat(auth.getTokenVersion()).isEqualTo(2L);
        auth.updateInfo(event);
        assertThat(auth.getTokenVersion()).isEqualTo(2L);
    }

    @Test void unchanged_security_fields_preserve_sessions() {
        Auth auth = Auth.builder().id(1L).userId("member").password("hash").role(UserRole.ROLE_USER).build();
        auth.updateInfo(UserDtoForEvent.builder().password("hash").role("ROLE_USER").status("ACTIVE").build());
        assertThat(auth.getTokenVersion()).isEqualTo(1L);
    }
}
