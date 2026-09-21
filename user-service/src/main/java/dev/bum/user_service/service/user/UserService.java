package dev.bum.user_service.service.user;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.common.service.user.user.dto.DeleteUserBulkRequest;
import dev.bum.common.service.user.user.dto.FindPasswordRequest;
import dev.bum.common.service.user.user.dto.FindPasswordResponse;
import dev.bum.common.service.user.user.dto.FindUserIdRequest;
import dev.bum.common.service.user.user.dto.FindUserIdResponse;
import dev.bum.common.service.user.user.dto.ResetPasswordRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.common.service.user.user.enums.UserStatus;
import dev.bum.user_service.audit.AuditContext;
import dev.bum.user_service.audit.AuditLog;
import dev.bum.user_service.exception.PasswordIncorrectException;
import dev.bum.user_service.exception.UserNotExistException;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserRepository;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.dto.UserCondRequest;
import dev.bum.common.service.user.user.dto.ValidatePasswordRequest;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, UserDtoForEvent> kafkaTemplate;
    private final Map<String, PasswordResetToken> passwordResetTokens = new ConcurrentHashMap<>();

    @Value("${topic.user.name}")
    private String userTopic;

    /**
     * ID 중복 체크 로직
     * @param userId
     */
    @Transactional(readOnly = true)
    public void validateIsUserIdDuplicated(String userId) {
        repository.validateIsUserIdDuplicated(normalizeUserId(userId));
    }

    /**
     * 유저 등록
     * @param info
     * @return
     */
    @AuditLog(action = "USER_CREATE", targetType = "USER")
    public UserResponse insert(InsertUserRequest info) {
        info.setUserId(normalizeUserId(info.getUserId()));
        log.info("[INSERT] insertUserInfo : {}", info.toString());
        User savedUser = repository.insert(info);

        UserDtoForEvent event = UserDtoForEvent.builder()
                .eventType(TopicEventType.CREATE)
                .id(savedUser.getId())
                .userId(savedUser.getUserId())
                .password(savedUser.getPassword())
                .role(savedUser.getRole().name())
                .status(savedUser.getStatus().name())
                .build();

        sendTopicToKafka(event);

        return savedUser.toResponse();
    }

    /**
     * ID로 유저 조회
     * @param userId
     * @return
     */
    @Transactional(readOnly = true)
    public UserResponse selectById(String userId) {
        log.info("[SELECT] userId : {}", userId);
        return repository.selectById(userId).toResponse();
    }

    @Transactional(readOnly = true)
    public UserResponse selectMyInfo(String userId) {
        User user = repository.selectById(userId);
        requireActive(user);
        return user.toResponse();
    }

    @Transactional(readOnly = true)
    public FindUserIdResponse findUserIdByPhoneNumber(FindUserIdRequest request) {
        log.info("[FIND USER ID BY PHONE] name : {}", request.getName());
        if (!StringUtils.hasText(request.getPhoneNumber())) {
            throw new UserNotExistException("사용자 정보가 일치하지 않습니다.");
        }

        User user = repository.selectByNameAndPhoneNumber(request.getName(), request.getPhoneNumber());
        requireActive(user);

        return FindUserIdResponse.builder()
                .maskedUserId(maskUserId(user.getUserId()))
                .build();
    }

    @Transactional(readOnly = true)
    public FindUserIdResponse findUserIdByEmail(FindUserIdRequest request) {
        log.info("[FIND USER ID BY EMAIL] name : {}", request.getName());
        if (!StringUtils.hasText(request.getEmail())) {
            throw new UserNotExistException("사용자 정보가 일치하지 않습니다.");
        }

        User user = repository.selectByNameAndEmail(request.getName(), request.getEmail());
        requireActive(user);

        return FindUserIdResponse.builder()
                .maskedUserId(maskUserId(user.getUserId()))
                .build();
    }

    @Transactional(readOnly = true)
    public FindPasswordResponse findPasswordByPhoneNumber(FindPasswordRequest request) {
        log.info("[FIND PASSWORD BY PHONE] userId : {}, name : {}", request.getUserId(), request.getName());
        if (!StringUtils.hasText(request.getPhoneNumber())) {
            throw new UserNotExistException("사용자 정보가 일치하지 않습니다.");
        }

        User user = repository.selectByUserIdAndNameAndPhoneNumber(
                request.getUserId(),
                request.getName(),
                request.getPhoneNumber()
        );
        requireActive(user);

        return createPasswordResetToken(user);
    }

    @Transactional(readOnly = true)
    public FindPasswordResponse findPasswordByEmail(FindPasswordRequest request) {
        log.info("[FIND PASSWORD BY EMAIL] userId : {}, name : {}", request.getUserId(), request.getName());
        if (!StringUtils.hasText(request.getEmail())) {
            throw new UserNotExistException("사용자 정보가 일치하지 않습니다.");
        }

        User user = repository.selectByUserIdAndNameAndEmail(
                request.getUserId(),
                request.getName(),
                request.getEmail()
        );
        requireActive(user);

        return createPasswordResetToken(user);
    }

    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokens.remove(request.getResetToken());

        if (resetToken == null || resetToken.isExpired()) {
            throw new UserNotExistException("비밀번호 재설정 요청이 만료되었습니다.");
        }
        requireActive(repository.selectById(resetToken.userId()));

        User updatedUser = repository.update(
                resetToken.userId(),
                UpdateUserRequest.builder()
                        .password(request.getPassword())
                        .build()
        );

        sendTopicToKafka(UserDtoForEvent.builder()
                .eventType(TopicEventType.UPDATE)
                .id(updatedUser.getId())
                .userId(updatedUser.getUserId())
                .password(updatedUser.getPassword())
                .role(updatedUser.getRole().name())
                .build());
    }

    /**
     * 조건을 통해 유저 조회
     * @param cond
     * @return
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<UserResponse> selectByCond(UserCondRequest cond) {
        log.info("[SELECT : {}]", cond.toString());

        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<UserResponse> userPage = repository.selectByCond(cond, pageRequest).map(User::toResponse);

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
    @AuditLog(action = "USER_UPDATE", targetType = "USER")
    public UserResponse update(String userId, UpdateUserRequest info) {
        log.info("[UPDATE] updateUserInfo : {}", info.toString());

        User beforeUser = repository.selectById(userId);
        if (beforeUser.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("탈퇴한 계정은 수정할 수 없습니다.");
        }
        UserRole originalRole = beforeUser.getRole();
        Map<String, Object> beforeData = new LinkedHashMap<>();
        Map<String, Object> afterData = new LinkedHashMap<>();
        putUserUpdateAuditData(beforeData, afterData, beforeUser, info);

        UserResponse updatedUser = repository.update(userId, info).toResponse();
        AuditContext.setBeforeData(beforeData);
        AuditContext.setAfterData(afterData);

        // ROLE이 변경됐을 때 AUTH DB에 적용
        if (StringUtils.hasText(info.getRole()) && !originalRole.name().equals(info.getRole())) {
            UserDtoForEvent event = UserDtoForEvent.builder()
                    .eventType(TopicEventType.UPDATE)
                    .id(updatedUser.getId())
                    .userId(updatedUser.getUserId())
                    .build();

            sendTopicToKafka(event);
        }

        return updatedUser;
    }

    @AuditLog(action = "USER_WITHDRAW", targetType = "USER")
    public UserResponse withdraw(String userId, String password) {
        User user = repository.selectById(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("이미 탈퇴한 계정입니다.");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new PasswordIncorrectException("비밀번호가 일치하지 않습니다.");
        }

        user.withdraw(LocalDateTime.now());
        AuditContext.setBeforeData(Map.of("status", UserStatus.ACTIVE.name()));
        AuditContext.setAfterData(Map.of("status", UserStatus.WITHDRAWN.name(), "withdrawAt", user.getWithdrawAt().toString()));
        sendTopicToKafka(UserDtoForEvent.builder()
                .eventType(TopicEventType.UPDATE)
                .id(user.getId())
                .userId(user.getUserId())
                .status(UserStatus.WITHDRAWN.name())
                .build());
        return user.toResponse();
    }

    @Transactional(readOnly = true)
    @AuditLog(action = "USER_PASSWORD_VALIDATE", targetType = "USER")
    public void validateInfo(ValidatePasswordRequest info) {
        log.info("[VALIDATE] : {}", info);
        User user = repository.selectById(info.getUserId());
        requireActive(user);

        if (!passwordEncoder.matches(info.getPassword(), user.getPassword())) {
            throw new PasswordIncorrectException("사용자 정보가 일치하지 않습니다.");
        }
    }

    @AuditLog(action = "USER_PASSWORD_INIT", targetType = "USER")
    public void initPassword(String userId) {
        log.info("[INIT PASSWORD] userId : {}", userId);
        UpdateUserRequest info = UpdateUserRequest.builder()
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
    @AuditLog(action = "USER_DELETE", targetType = "USER")
    public UserResponse delete(String userId) {
        log.info("[DELETE] userId : {}", userId);

        User deletedUser = repository.delete(userId);

        UserDtoForEvent event = UserDtoForEvent.builder()
                .eventType(TopicEventType.DELETE)
                .id(deletedUser.getId())
                .userId(deletedUser.getUserId())
                .build();

        sendTopicToKafka(event);

        return deletedUser.toResponse();
    }

    @AuditLog(action = "USER_DELETE_BULK", targetType = "USER")
    public void deleteBulk(DeleteUserBulkRequest info) {
        if (info.getUserIds() == null || info.getUserIds().isEmpty()) {
            throw new IllegalArgumentException("삭제할 유저 정보가 없습니다.");
        }

        log.info("[BULK DELETE] userIds : {}", info.getUserIds());
        info.getUserIds().forEach(this::delete);
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

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim().toLowerCase(Locale.ROOT) : userId;
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserNotExistException("사용자 정보가 일치하지 않습니다.");
        }
    }

    private void putUserUpdateAuditData(
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            User beforeUser,
            UpdateUserRequest info
    ) {
        putPasswordChanged(beforeData, afterData, info.getPassword());
        putChangedText(beforeData, afterData, "phoneNumber", info.getPhoneNumber(),
                beforeUser.getPhoneNumber(), maskPhoneNumber(beforeUser.getPhoneNumber()), maskPhoneNumber(info.getPhoneNumber()));
        putChangedText(beforeData, afterData, "email", info.getEmail(),
                beforeUser.getEmail(), maskEmail(beforeUser.getEmail()), maskEmail(info.getEmail()));
        putChangedValue(beforeData, afterData, "birthDate", info.getBirthDate(),
                beforeUser.getBirthDate(), "MASKED", "CHANGED");
        putChangedText(beforeData, afterData, "address", info.getAddress(),
                beforeUser.getAddress(), "MASKED", "CHANGED");
        Boolean requestedBlacklist = info.getIsBlacklisted() != null
                ? info.getIsBlacklisted()
                : info.getBlacklistedUntil() != null ? true : null;
        putChangedValue(beforeData, afterData, "isBlacklisted", requestedBlacklist,
                beforeUser.getIsBlacklisted(), beforeUser.getIsBlacklisted(), requestedBlacklist);
        if (Boolean.FALSE.equals(info.getIsBlacklisted()) && beforeUser.getBlacklistedUntil() != null) {
            beforeData.put("blacklistedUntil", beforeUser.getBlacklistedUntil());
            afterData.put("blacklistedUntil", null);
        } else {
            putChangedValue(beforeData, afterData, "blacklistedUntil", info.getBlacklistedUntil(),
                    beforeUser.getBlacklistedUntil(), beforeUser.getBlacklistedUntil(), info.getBlacklistedUntil());
        }
        putChangedText(beforeData, afterData, "role", info.getRole(),
                beforeUser.getRole() != null ? beforeUser.getRole().name() : null,
                beforeUser.getRole() != null ? beforeUser.getRole().name() : null, info.getRole());
        putChangedText(beforeData, afterData, "grade", info.getGrade(),
                beforeUser.getGrade() != null ? beforeUser.getGrade().name() : null,
                beforeUser.getGrade() != null ? beforeUser.getGrade().name() : null, info.getGrade());
    }

    private void putChangedText(
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            String fieldName,
            String requestedValue,
            String originalValue,
            Object beforeValue,
            Object afterValue
    ) {
        if (!StringUtils.hasText(requestedValue)) {
            return;
        }

        if (Objects.equals(originalValue, requestedValue)) {
            return;
        }

        beforeData.put(fieldName, beforeValue);
        afterData.put(fieldName, afterValue);
    }

    private void putPasswordChanged(
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            String requestedPassword
    ) {
        if (!StringUtils.hasText(requestedPassword)) {
            return;
        }

        beforeData.put("password", "UNCHANGED");
        afterData.put("password", "CHANGED");
    }

    private void putChangedValue(
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            String fieldName,
            Object requestedValue,
            Object originalValue,
            Object beforeValue,
            Object afterValue
    ) {
        if (requestedValue == null) {
            return;
        }

        if (Objects.equals(originalValue, requestedValue)) {
            return;
        }

        beforeData.put(fieldName, beforeValue);
        afterData.put(fieldName, afterValue);
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return email;
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "MASKED";
        }

        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return phoneNumber;
        }

        int visibleLength = Math.min(4, phoneNumber.length());
        return "***" + phoneNumber.substring(phoneNumber.length() - visibleLength);
    }

    private String maskUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return "";
        }

        int maskLength = Math.min(userId.length(), userId.length() >= 8 ? 4 : 3);
        int visibleLength = userId.length() - maskLength;

        if (visibleLength <= 0) {
            return "*".repeat(userId.length());
        }

        return userId.substring(0, visibleLength) + "*".repeat(maskLength);
    }

    private FindPasswordResponse createPasswordResetToken(User user) {
        String resetToken = UUID.randomUUID().toString();
        passwordResetTokens.put(resetToken, new PasswordResetToken(user.getUserId(), LocalDateTime.now().plusMinutes(5)));

        return FindPasswordResponse.builder()
                .resetToken(resetToken)
                .build();
    }

    private record PasswordResetToken(String userId, LocalDateTime expiresAt) {
        private boolean isExpired() {
            return expiresAt.isBefore(LocalDateTime.now());
        }
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
