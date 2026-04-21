package dev.bum.user_service.service;

import dev.bum.common.kafka.UserDtoForEvent;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.user_service.dto.UserDto;
import dev.bum.user_service.jpa.User;
import dev.bum.user_service.jpa.UserRepository;
import dev.bum.user_service.vo.InsertUserInfo;
import dev.bum.user_service.vo.UpdateUserInfo;
import dev.bum.user_service.vo.UserCond;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final KafkaTemplate<String, UserDtoForEvent> kafkaTemplate;

    @Value("${topic.name}")
    private String userTopic;

    /**
     * 유저 등록
     * @param info
     * @return
     */
    public UserDto insert(InsertUserInfo info) {
        User savedUser = repository.insert(info);

        UserDtoForEvent event = UserDtoForEvent.builder()
                .eventType(TopicEventType.CREATE)
                .id(savedUser.getId())
                .userId(savedUser.getUserId())
                .password(savedUser.getPassword())
                .role(savedUser.getRole().name())
                .build();

        sendTopicToKafka(event);

        return new UserDto(savedUser);
    }

    /**
     * 유저 전체 조회
     * @param cond
     * @return
     */
    @Transactional(readOnly = true)
    public Page<UserDto> selectAll(UserCond cond) {
        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<User> users = repository.selectAll(pageRequest);

        return users.map(UserDto::new);
    }

    /**
     * ID로 유저 조회
     * @param userId
     * @return
     */
    @Transactional(readOnly = true)
    public UserDto selectById(String userId) {
        return new UserDto(repository.selectById(userId));
    }

    /**
     * 조건을 통해 유저 조회
     * @param cond
     * @return
     */
    @Transactional(readOnly = true)
    public Page<UserDto> selectByCond(UserCond cond) {
        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<User> users = repository.selectByCond(cond, pageRequest);

        return users.map(UserDto::new);
    }

    /**
     * 유저 정보 수정
     * @param userId
     * @param info
     * @return
     */
    public UserDto update(String userId, UpdateUserInfo info) {
        return new UserDto(repository.update(userId, info));
    }

    /**
     * 유저 삭제
     * @param userId
     * @return
     */
    public UserDto delete(String userId) {
        User deletedUser = repository.delete(userId);

        UserDtoForEvent event = UserDtoForEvent.builder()
                .eventType(TopicEventType.DELETE)
                .id(deletedUser.getId())
                .userId(deletedUser.getUserId())
                .build();

        sendTopicToKafka(event);

        return new UserDto(deletedUser);
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
