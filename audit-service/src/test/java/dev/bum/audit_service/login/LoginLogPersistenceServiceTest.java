package dev.bum.audit_service.login;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.audit.dto.LoginLogCondRequest;
import dev.bum.common.service.audit.dto.LoginLogResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginLogPersistenceServiceTest {

    private final LoginLogJpaRepository repository = mock(LoginLogJpaRepository.class);
    private final LoginLogPersistenceService service = new LoginLogPersistenceService(repository);

    @Test
    @DisplayName("ID로 로그인 로그 상세 조회")
    void selectById_returns_login_log_response() {
        LoginLogEntity entity = loginLogEntity();
        given(repository.findById(1L)).willReturn(Optional.of(entity));

        LoginLogResponse response = service.selectById(1L);

        assertThat(response.getLoginId()).isEqualTo("user01");
        assertThat(response.getResult()).isEqualTo("SUCCESS");
        assertThat(response.getAuthMethod()).isEqualTo("PASSWORD");
        assertThat(response.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("없는 로그인 로그 상세 조회 시 예외")
    void selectById_throws_when_not_found() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.selectById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 로그인 로그가 존재하지 않습니다.");
    }

    @Test
    @DisplayName("목록 조회의 페이지 크기와 정렬 조건을 정규화")
    @SuppressWarnings("unchecked")
    void selectByCond_normalizes_paging_and_sort() {
        LoginLogCondRequest cond = LoginLogCondRequest.builder()
                .page(-1)
                .size(500)
                .sort(List.of("loginId-asc", "unknown-desc"))
                .build();
        given(repository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(loginLogEntity()), PageRequest.of(0, 100), 1));

        CustomPageResponse<LoginLogResponse> response = service.selectByCond(cond);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("loginId").isAscending()).isTrue();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
    }

    private LoginLogEntity loginLogEntity() {
        return LoginLogEntity.builder()
                .eventId("550e8400-e29b-41d4-a716-446655440000")
                .authId(1L)
                .loginId("user01")
                .result(LoginResult.SUCCESS)
                .authMethod(LoginAuthMethod.PASSWORD)
                .ipAddress("127.0.0.1")
                .occurredAt(LocalDateTime.of(2026, 9, 25, 21, 0))
                .build();
    }
}
