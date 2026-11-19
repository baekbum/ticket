package dev.bum.user_service.service.user;

import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserKafkaEventState;
import dev.bum.user_service.jpa.user.UserKafkaEventStateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserKafkaEventStateService {

    // 사용자 이벤트 상태 데이터를 조회하고 저장하는 레포지토리.
    private final UserKafkaEventStateJpaRepository repository;

    /**
     * 사용자 이벤트 상태 처리에서 이벤트 버전을 증가시키고 최신 상태를 반영한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserKafkaEventState advance(User user, boolean passwordReset) {

        // 호출자가 회원 행을 잠근 상태이므로 상태가 아직 없어도 동시 생성이 직렬화된다.
        UserKafkaEventState state = repository.findById(user.getId())
                .orElseGet(() -> repository.save(UserKafkaEventState.builder().user(user).build()));

        state.advance(passwordReset);
        return state;
    }
}
