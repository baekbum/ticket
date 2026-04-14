package dev.bum.user_service.jpa;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.user_service.exception.UserDuplicateException;
import dev.bum.user_service.exception.UserNotExistException;
import dev.bum.user_service.vo.InsertUserInfo;
import dev.bum.user_service.vo.UpdateUserInfo;
import dev.bum.user_service.vo.UserCond;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final JPAQueryFactory queryFactory;
    private final UserJpaRepository jpaRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private QUser user;

    /**
     * 유저 등록
     * @param info
     * @return
     */
    @Override
    public User insert(InsertUserInfo info) {
        if (jpaRepository.findByUserId(info.getUserId()).isPresent()) {
            throw new UserDuplicateException("해당 사용자 ID는 이미 존재합니다.");
        }

        // 비밀번호 암호화 작업
        info.setPassword(passwordEncoder.encode(info.getPassword()));

        User user = new User(info);
        jpaRepository.save(user);

        return user;
    }

    /**
     * 전체 유저 검색
     * @param pageable
     * @return
     */
    @Override
    public Page<User> selectAll(Pageable pageable) {
        return jpaRepository.findAll(pageable);
    }

    /**
     * ID로 유저 검색
     * @param userId
     * @return
     */
    @Override
    public User selectById(String userId) {
        return jpaRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotExistException("해당 유저를 발견하지 못했습니다."));
    }

    /**
     * 조건을 통해 유저 검색
     * @param cond
     * @param pageable
     * @return
     */
    @Override
    public Page<User> selectByCond(UserCond cond, Pageable pageable) {
        user = QUser.user;

        // 1. Pageable 객체에서 Sort 정보를 추출하여 OrderSpecifier 리스트를 생성
        List<OrderSpecifier> orderSpecifiers = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            String property = order.getProperty();
            PathBuilder<User> entityPath = new PathBuilder<>(User.class, "user");
            orderSpecifiers.add(new OrderSpecifier(direction, entityPath.get(property)));
        });

        List<User> content = queryFactory
                .select(user)
                .from(user)
                .where(
                        userIdEq(cond.getUserId()),
                        userIdIn(cond.getUserIdList()),
                        nameEq(cond.getEmail()),
                        nameIn(cond.getNameList()),
                        phoneNumberEq(cond.getPhoneNumber()),
                        emailEq(cond.getEmail()),
                        birthDateEq(cond.getBirthDate()),
                        addressLike(cond.getAddress()),
                        isBlacklistedEq(cond.getIsBlacklisted())
                )
                .offset(pageable.getOffset()) // 오프셋 적용
                .limit(pageable.getPageSize()) // 페이지 크기 적용
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0])) // 정렬 정보 적용
                .fetch();

        Long total = queryFactory
                .select(user.count())
                .from(user)
                .where(
                        userIdEq(cond.getUserId()),
                        userIdIn(cond.getUserIdList()),
                        nameEq(cond.getEmail()),
                        nameIn(cond.getNameList()),
                        phoneNumberEq(cond.getPhoneNumber()),
                        emailEq(cond.getEmail()),
                        birthDateEq(cond.getBirthDate()),
                        addressLike(cond.getAddress()),
                        isBlacklistedEq(cond.getIsBlacklisted())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 유저 정보 업데이트
     * @param userId
     * @param info
     * @return
     */
    @Override
    public User update(String userId, UpdateUserInfo info) {
        User user = selectById(userId);

        if (StringUtils.hasText(info.getPassword())) {
            String encodePassword = passwordEncoder.encode(info.getPassword());
            info.setPassword(encodePassword);
        }

        user.updateInfo(info);

        return user;
    }

    /**
     * 유저 삭제
     * @param userId
     * @return
     */
    @Override
    public User delete(String userId) {
        User user = selectById(userId);
        jpaRepository.delete(user);

        return user;
    }


    // QueryDsl 동적 쿼리 관련 메서드
    private BooleanExpression userIdEq(String userId) {
        return StringUtils.hasText(userId) ? user.userId.eq(userId) : null;
    }

    private BooleanExpression userIdIn(List<String> userIdList) {
        return userIdList != null && !userIdList.isEmpty()
                ? user.userId.in(userIdList) : null;
    }

    private BooleanExpression nameEq(String name) {
        return StringUtils.hasText(name) ? user.name.eq(name) : null;
    }

    private BooleanExpression nameIn(List<String> nameList) {
        return nameList != null && !nameList.isEmpty()
                ? user.name.in(nameList) : null;
    }

    private BooleanExpression phoneNumberEq(String phoneNumber) {
        return StringUtils.hasText(phoneNumber) ? user.phoneNumber.eq(phoneNumber) : null;
    }

    private BooleanExpression emailEq(String email) {
        return StringUtils.hasText(email) ? user.email.eq(email) : null;
    }

    private BooleanExpression birthDateEq(LocalDate birthDate) {
        return birthDate != null ? user.birthDate.eq(birthDate) : null;
    }

    private BooleanExpression addressLike(String address) {
        return StringUtils.hasText(address) ? user.address.like("%" + address + "%") : null;
    }

    private BooleanExpression isBlacklistedEq(Boolean isBlacklisted) {
        return isBlacklisted != null ? user.isBlacklisted.eq(isBlacklisted) : null;
    }
}
