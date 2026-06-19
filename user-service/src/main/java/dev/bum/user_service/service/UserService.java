package dev.bum.user_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.common.service.user.dto.UserDto;
import dev.bum.common.service.user.enums.UserRole;
import dev.bum.user_service.exception.PasswordIncorrectException;
import dev.bum.user_service.jpa.User;
import dev.bum.user_service.jpa.UserRepository;
import dev.bum.common.service.user.vo.InsertUserInfo;
import dev.bum.common.service.user.vo.UpdateUserInfo;
import dev.bum.common.service.user.vo.UserCond;
import dev.bum.common.service.user.vo.ValidatePasswordInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, UserDtoForEvent> kafkaTemplate;

    @Value("${topic.name}")
    private String userTopic;

    /**
     * ID 중복 체크 로직
     * @param userId
     */
    @Transactional(readOnly = true)
    public void isDuplicated(String userId) {
        repository.isExist(userId);
    }

    /**
     * 유저 등록
     * @param info
     * @return
     */
    public UserDto insert(InsertUserInfo info) {
        log.info("[INSERT] insertUserInfo : {}", info.toString());
        User savedUser = repository.insert(info);

        UserDtoForEvent event = UserDtoForEvent.builder()
                .eventType(TopicEventType.CREATE)
                .id(savedUser.getId())
                .userId(savedUser.getUserId())
                .password(savedUser.getPassword())
                .role(savedUser.getRole().name())
                .build();

        sendTopicToKafka(event);

        return savedUser.toDto();
    }

    /**
     * ID로 유저 조회
     * @param userId
     * @return
     */
    @Transactional(readOnly = true)
    public UserDto selectById(String userId) {
        log.info("[SELECT] userId : {}", userId);
        return repository.selectById(userId).toDto();
    }

    /**
     * 조건을 통해 유저 조회
     * @param cond
     * @return
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<UserDto> selectByCond(UserCond cond) {
        log.info("[SELECT : {}]", cond.toString());

        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<UserDto> userPage = repository.selectByCond(cond, pageRequest).map(User::toDto);

        return CustomPageResponse.of(
                userPage.getContent(),
                userPage.getSize(),
                userPage.getNumber(),
                userPage.getTotalElements(),
                userPage.getTotalPages()
        );
    }

    /**
     * 유저 정보 수정
     * @param userId
     * @param info
     * @return
     */
    public UserDto update(String userId, UpdateUserInfo info) {
        log.info("[UPDATE] updateUserInfo : {}", info.toString());

        UserRole originalRole = repository.selectById(userId).getRole();
        UserDto updatedUser = repository.update(userId, info).toDto();

        // ROLE이 변경됐을 때 AUTH DB에 적용
        if (!originalRole.name().equals(info.getRole())) {
            UserDtoForEvent event = UserDtoForEvent.builder()
                    .eventType(TopicEventType.UPDATE)
                    .id(updatedUser.getId())
                    .userId(updatedUser.getUserId())
                    .build();

            sendTopicToKafka(event);
        }

        return updatedUser;
    }

    @Transactional(readOnly = true)
    public void validateInfo(ValidatePasswordInfo info) {
        log.info("[VALIDATE] : {}", info);
        User user = repository.selectById(info.getUserId());

        if (!passwordEncoder.matches(info.getPassword(), user.getPassword())) {
            throw new PasswordIncorrectException("사용자 정보가 일치하지 않습니다.");
        }
    }

    public void initPassword(String userId) {
        log.info("[INIT PASSWORD] userId : {}", userId);
        UpdateUserInfo info = UpdateUserInfo.builder()
                .password("123456789!")
                .build();

        repository.update(userId, info);
        log.info("[비밀빈호 초기화 완료] userId : {}", userId);
    }

    /**
     * 유저 삭제
     * @param userId
     * @return
     */
    public UserDto delete(String userId) {
        log.info("[DELETE] userId : {}", userId);

        User deletedUser = repository.delete(userId);

        UserDtoForEvent event = UserDtoForEvent.builder()
                .eventType(TopicEventType.DELETE)
                .id(deletedUser.getId())
                .userId(deletedUser.getUserId())
                .build();

        sendTopicToKafka(event);

        return deletedUser.toDto();
    }

    /**
     * 검색 조건에서 sort 옵션을 처리하기 위한 메서드
     * @param sorts
     * @return
     */
    private Sort makeSortInfo(List<String> sorts) {
        Sort sort = Sort.unsorted();
        if (sorts != null && !sorts.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();

            for (String infoStr : sorts) {
                String[] infos = infoStr.split("-");

                if (infos.length == 2) {
                    String field = infos[0];
                    String direction = infos[1];
                    orders.add(new Sort.Order(Sort.Direction.fromString(direction), field));
                }
            }
            sort = Sort.by(orders);
        }

        return sort;
    }

    /**
     * 토픽을 카프카 큐에 전달.
     * @param event
     */
    private void sendTopicToKafka(UserDtoForEvent event) {
        // 주입받은 userTopic 변수 사용
        kafkaTemplate.send(userTopic, event.getUserId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Kafka 전송 성공: [topic: {}, userId: {}]", userTopic, event.getUserId());
                    } else {
                        log.error("Kafka 전송 실패: [userId: {}] 에러: {}", event.getUserId(), ex.getMessage());
                    }
                });
    }
}
