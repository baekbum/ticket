package dev.bum.user_service.service.user;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.common.kafka.user.UserDtoForEvent;
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
import dev.bum.user_service.exception.WithdrawnUserException;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserKafkaEventState;
import dev.bum.user_service.jpa.user.UserRepository;
import dev.bum.user_service.service.outbox.UserOutboxService;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.dto.UserCondRequest;
import dev.bum.common.service.user.user.dto.ValidatePasswordRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    // 사용자 데이터를 조회하고 저장하는 레포지토리.
    private final UserRepository repository;
    // 비밀번호를 암호화하고 일치 여부를 확인하는 인코더.
    private final PasswordEncoder passwordEncoder;
    // 사용자 Outbox 이벤트 업무 처리를 담당하는 서비스.
    private final UserOutboxService userOutboxService;
    // 사용자 이벤트 상태 업무 처리를 담당하는 서비스.
    private final UserKafkaEventStateService userKafkaEventStateService;
    // 비밀번호 재설정 토큰과 사용자 정보를 보관하는 저장소.
    private final Map<String, PasswordResetToken> passwordResetTokens = new ConcurrentHashMap<>();

    /**
     * 유저 등록
     * @param info
     * @return
     */
    @AuditLog(action = "USER_CREATE", targetType = "USER")
    public UserResponse insert(InsertUserRequest info) {

        info.setUserId(normalizeUserId(info.getUserId()));
        log.info("[INSERT] userId={}", info.getUserId());
        User savedUser = repository.insert(info);

        enqueueUserEvent(savedUser, TopicEventType.CREATE, false);

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

    /**
     * 현재 사용자의 회원 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public UserResponse selectMyInfo(String userId) {

        User user = repository.selectById(userId);
        validateIsUserStatusActive(user);
        return user.toResponse();
    }

    /**
     * 이름과 휴대전화 번호로 사용자 ID를 찾는다.
     */
    @Transactional(readOnly = true)
    public FindUserIdResponse findUserIdByPhoneNumber(FindUserIdRequest request) {

        log.info("[FIND USER ID BY PHONE] name : {}", request.getName());
        if (!StringUtils.hasText(request.getPhoneNumber())) {
            throw new UserNotExistException("사용자 정보가 일치하지 않습니다.");
        }

        User user = repository.selectByNameAndPhoneNumber(request.getName(), request.getPhoneNumber());
        validateTicksyUser(user);

        return FindUserIdResponse.builder()
                .maskedUserId(maskUserId(user.getUserId()))
                .build();
    }

    /**
     * 이름과 이메일 주소로 사용자 ID를 찾는다.
     */
    @Transactional(readOnly = true)
    public FindUserIdResponse findUserIdByEmail(FindUserIdRequest request) {

        log.info("[FIND USER ID BY EMAIL] name : {}", request.getName());
        if (!StringUtils.hasText(request.getEmail())) {
            throw new UserNotExistException("사용자 정보가 일치하지 않습니다.");
        }

        User user = repository.selectByNameAndEmail(request.getName(), request.getEmail());
        validateTicksyUser(user);

        return FindUserIdResponse.builder()
                .maskedUserId(maskUserId(user.getUserId()))
                .build();
    }

    /**
     * 휴대전화 인증 정보로 비밀번호 재설정 토큰을 발급한다.
     */
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
        validateTicksyUser(user);

        return createPasswordResetToken(user);
    }

    /**
     * 이메일 인증 정보로 비밀번호 재설정 토큰을 발급한다.
     */
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
        validateTicksyUser(user);

        return createPasswordResetToken(user);
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
     * 유효한 재설정 토큰으로 사용자 비밀번호를 변경한다.
     */
    public void resetPassword(ResetPasswordRequest request) {

        PasswordResetToken resetToken = passwordResetTokens.remove(request.getResetToken());

        if (resetToken == null || resetToken.isExpired()) {
            throw new UserNotExistException("비밀번호 재설정 요청이 만료되었습니다.");
        }
        validateTicksyUser(repository.selectByIdForUpdate(resetToken.userId()));

        User updatedUser = repository.update(
                resetToken.userId(),
                UpdateUserRequest.builder()
                        .password(request.getPassword())
                        .build()
        );

        enqueueUserEvent(updatedUser, TopicEventType.UPDATE, true);
    }

    /**
     * 유저 정보 수정
     * @param userId
     * @param info
     * @return
     */
    @AuditLog(action = "USER_UPDATE", targetType = "USER")
    public UserResponse update(String userId, UpdateUserRequest info) {

        log.info("[UPDATE] userId={}", userId);

        User beforeUser = repository.selectByIdForUpdate(userId);
        if (beforeUser.getStatus() != UserStatus.ACTIVE && info.getStatus() == null) {
            throw new IllegalArgumentException("탈퇴한 계정은 수정할 수 없습니다.");
        }

        if (info.getWithdrawAt() != null && info.getStatus() != UserStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴 일시는 탈퇴 상태에서만 설정할 수 있습니다.");
        }
        UserRole originalRole = beforeUser.getRole();
        UserStatus originalStatus = beforeUser.getStatus();
        LocalDateTime originalWithdrawAt = beforeUser.getWithdrawAt();
        Map<String, Object> beforeData = new LinkedHashMap<>();
        Map<String, Object> afterData = new LinkedHashMap<>();
        putUserUpdateAuditData(beforeData, afterData, beforeUser, info);

        User updatedEntity = repository.update(userId, info);
        UserResponse updatedUser = updatedEntity.toResponse();
        if (info.getStatus() != null && (originalStatus != updatedUser.getStatus()
                || !Objects.equals(originalWithdrawAt, updatedUser.getWithdrawAt()))) {
            beforeData.put("status", originalStatus);
            afterData.put("status", updatedUser.getStatus());
            beforeData.put("withdrawAt", originalWithdrawAt);
            afterData.put("withdrawAt", updatedUser.getWithdrawAt());
        }
        AuditContext.setBeforeData(beforeData);
        AuditContext.setAfterData(afterData);

        // 인증에 필요한 정보가 변경됐을 때 AUTH DB에 적용
        if ((StringUtils.hasText(info.getRole()) && !originalRole.name().equals(info.getRole()))
                || StringUtils.hasText(info.getPassword())
                || originalStatus != updatedUser.getStatus()
                || info.getIsBlacklisted() != null
                || info.getBlacklistedUntil() != null) {
            enqueueUserEvent(updatedEntity, TopicEventType.UPDATE, false);
        }

        return updatedUser;
    }

    /**
     * 사용자 비밀번호를 초기값으로 변경한다.
     */
    @AuditLog(action = "USER_PASSWORD_INIT", targetType = "USER")
    public void initPassword(String userId) {

        log.info("[INIT PASSWORD] userId : {}", userId);
        UpdateUserRequest info = UpdateUserRequest.builder()
                .password("123456789!")
                .build();

        User updatedUser = repository.update(userId, info);
        enqueueUserEvent(updatedUser, TopicEventType.UPDATE, false);
        log.info("[비밀빈호 초기화 완료] userId : {}", userId);
    }

    /**
     * 현재 비밀번호를 검증한 뒤 새 비밀번호로 변경한다.
     */
    @AuditLog(action = "USER_PASSWORD_CHANGE", targetType = "USER")
    public void changeMyPassword(String userId, String currentPassword, String newPassword, String newPasswordConfirm) {

        User user = repository.selectByIdForUpdate(userId);

        validateIsUserStatusActive(user);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new PasswordIncorrectException("비밀번호가 일치하지 않습니다.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("새로운 비밀번호는 이전 비밀번호와 같을 수 없습니다.");
        }

        if (!newPassword.equals(newPasswordConfirm)) {
            throw new IllegalArgumentException("새 비밀번호 확인이 일치하지 않습니다.");
        }

        User updatedUser = repository.update(userId, UpdateUserRequest.builder().password(newPassword).build());

        AuditContext.setBeforeData(Map.of("password", "UNCHANGED"));
        AuditContext.setAfterData(Map.of("password", "CHANGED"));

        enqueueUserEvent(updatedUser, TopicEventType.UPDATE, false);
    }

    /**
     * 사용자 처리에서 회원을 탈퇴 상태로 변경한다.
     */
    @AuditLog(action = "USER_WITHDRAW", targetType = "USER")
    public UserResponse withdraw(String userId, String password) {

        User user = repository.selectByIdForUpdate(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("이미 탈퇴한 계정입니다.");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new PasswordIncorrectException("비밀번호가 일치하지 않습니다.");
        }

        user.withdraw(LocalDateTime.now());
        AuditContext.setBeforeData(Map.of("status", UserStatus.ACTIVE.name()));
        AuditContext.setAfterData(Map.of("status", UserStatus.WITHDRAWN.name(), "withdrawAt", user.getWithdrawAt().toString()));
        enqueueUserEvent(user, TopicEventType.UPDATE, false);
        return user.toResponse();
    }

    /**
     * 유저 삭제
     * @param userId
     * @return
     */
    @AuditLog(action = "USER_DELETE", targetType = "USER")
    public UserResponse delete(String userId) {

        log.info("[DELETE] userId : {}", userId);

        User user = repository.selectByIdForUpdate(userId);
        // 상태를 삭제하기 전에 마지막 버전을 발급하고 삭제 이벤트를 Outbox에 저장한다.
        enqueueUserEvent(user, TopicEventType.DELETE, false);
        User deletedUser = repository.delete(userId);

        return deletedUser.toResponse();
    }

    /**
     * 사용자를 일괄 삭제한다.
     */
    @AuditLog(action = "USER_DELETE_BULK", targetType = "USER")
    public void deleteBulk(DeleteUserBulkRequest info) {

        if (info.getUserIds() == null || info.getUserIds().isEmpty()) {
            throw new IllegalArgumentException("삭제할 유저 정보가 없습니다.");
        }

        log.info("[BULK DELETE] userIds : {}", info.getUserIds());
        info.getUserIds().forEach(this::delete);
    }

    /**
     * ID 중복 체크 로직
     * @param userId
     */
    @Transactional(readOnly = true)
    public void validateIsUserIdDuplicated(String userId) {

        repository.validateIsUserIdDuplicated(normalizeUserId(userId));
    }

    /**
     * 사용자 상태와 입력한 비밀번호를 검증한다.
     */
    @Transactional(readOnly = true)
    @AuditLog(action = "USER_PASSWORD_VALIDATE", targetType = "USER")
    public void validateInfo(ValidatePasswordRequest info) {

        log.info("[VALIDATE] userId={}", info.getUserId());
        User user = repository.selectById(info.getUserId());
        validateIsUserStatusActive(user);

        if (!passwordEncoder.matches(info.getPassword(), user.getPassword())) {
            throw new PasswordIncorrectException("사용자 정보가 일치하지 않습니다.");
        }
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
     * 사용자 ID의 공백과 대소문자를 정규화한다.
     */
    private String normalizeUserId(String userId) {

        return StringUtils.hasText(userId) ? userId.trim().toLowerCase(Locale.ROOT) : userId;
    }

    /**
     * 사용자가 활성 상태인지 검증한다.
     */
    private void validateIsUserStatusActive(User user) {

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new WithdrawnUserException();
        }
    }

    /**
     * 일반 사용자 계정이며 활성 상태인지 검증한다.
     */
    private void validateTicksyUser(User user) {

        if (user.getRole() != UserRole.ROLE_USER) {
            // 관리자 계정 여부를 공개 복구 API의 응답으로 노출하지 않는다.
            throw new UserNotExistException("사용자 정보가 일치하지 않습니다.");
        }
        validateIsUserStatusActive(user);
    }

    /**
     * 현재 사용자의 비밀번호가 일치하는지 검증한다.
     */
    @Transactional(readOnly = true)
    @AuditLog(action = "USER_PASSWORD_VALIDATE", targetType = "USER")
    public void validateMyPassword(String userId, String password) {

        User user = repository.selectById(userId);

        validateTicksyUser(user);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new PasswordIncorrectException("비밀번호가 일치하지 않습니다.");
        }
    }

    /**
     * 최신 사용자 인증 상태를 Outbox 이벤트로 저장한다.
     */
    private void enqueueUserEvent(User user, TopicEventType type, boolean passwordReset) {

        UserKafkaEventState state = userKafkaEventStateService.advance(user, passwordReset);
        // 최신 이벤트만으로도 인증 정보를 복원할 수 있도록 변경 차분 대신 전체 상태를 기록한다.
        userOutboxService.enqueue(UserDtoForEvent.builder()
                .eventVersion(state.getEventVersion())
                .passwordResetVersion(state.getPasswordResetVersion())
                .eventType(type)
                .id(user.getId())
                .userId(user.getUserId())
                .password(user.getPassword())
                .passwordReset(passwordReset)
                .role(user.getRole() == null ? null : user.getRole().name())
                .status(user.getStatus().name())
                .isBlacklisted(Boolean.TRUE.equals(user.getIsBlacklisted()))
                .blacklistedUntil(user.getBlacklistedUntil())
                .build());
    }

    /**
     * 사용자 변경 전후의 감사 로그 데이터를 구성한다.
     */
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

    /**
     * 변경된 문자열 항목을 감사 로그 데이터에 추가한다.
     */
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

    /**
     * 비밀번호 원문 없이 변경 여부를 감사 로그에 기록한다.
     */
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

    /**
     * 변경된 값의 이전 값과 새 값을 감사 로그에 추가한다.
     */
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

    /**
     * 이메일 주소의 일부를 가린다.
     */
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

    /**
     * 휴대전화 번호의 일부를 가린다.
     */
    private String maskPhoneNumber(String phoneNumber) {

        if (!StringUtils.hasText(phoneNumber)) {
            return phoneNumber;
        }

        int visibleLength = Math.min(4, phoneNumber.length());
        return "***" + phoneNumber.substring(phoneNumber.length() - visibleLength);
    }

    /**
     * 사용자 ID의 일부를 가린다.
     */
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

    /**
     * 사용자 비밀번호 재설정 토큰과 만료 시각을 생성한다.
     */
    private FindPasswordResponse createPasswordResetToken(User user) {

        String resetToken = UUID.randomUUID().toString();
        passwordResetTokens.put(resetToken, new PasswordResetToken(user.getUserId(), LocalDateTime.now().plusMinutes(5)));

        return FindPasswordResponse.builder()
                .resetToken(resetToken)
                .build();
    }

    private record PasswordResetToken(
            // 사용자 ID.
            String userId,

            // 만료 시각.
            LocalDateTime expiresAt
    ) {

        // 유효 기간이 만료되었는지 확인한다.
        private boolean isExpired() {

            return expiresAt.isBefore(LocalDateTime.now());
        }
    }
}
