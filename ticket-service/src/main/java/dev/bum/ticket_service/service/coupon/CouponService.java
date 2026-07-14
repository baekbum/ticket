package dev.bum.ticket_service.service.coupon;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.dto.CouponCondRequest;
import dev.bum.common.service.ticket.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.dto.InsertCouponRequest;
import dev.bum.common.service.ticket.coupon.dto.IssueCouponRequest;
import dev.bum.common.service.ticket.coupon.dto.UpdateCouponRequest;
import dev.bum.common.service.ticket.coupon.dto.UserCouponResponse;
import dev.bum.common.service.ticket.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.enums.UserCouponStatus;
import dev.bum.ticket_service.jpa.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.CouponJpaRepository;
import dev.bum.ticket_service.jpa.coupon.QCoupon;
import dev.bum.ticket_service.jpa.coupon.UserCoupon;
import dev.bum.ticket_service.jpa.coupon.UserCouponJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CouponService {

    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final JPAQueryFactory queryFactory;

    public CouponResponse insert(InsertCouponRequest request) {
        log.info("[COUPON][INSERT] request name={}, code={}, discountType={}, discountValue={}, status={}, validDaysAfterIssue={}",
                request.getName(), request.getCode(), request.getDiscountType(), request.getDiscountValue(), request.getStatus(), request.getValidDaysAfterIssue());

        validateCouponPolicy(request.getDiscountType(), request.getDiscountValue(), request.getMaxDiscountAmount());
        validateValidDaysAfterIssue(request.getValidDaysAfterIssue());

        if (couponJpaRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("이미 존재하는 쿠폰 코드입니다.");
        }

        Coupon savedCoupon = couponJpaRepository.save(new Coupon(request));
        log.info("[COUPON][INSERT][SUCCESS] couponId={}, code={}, status={}",
                savedCoupon.getCouponId(), savedCoupon.getCode(), savedCoupon.getStatus());

        return savedCoupon.toResponse();
    }

    public CouponResponse update(Long couponId, UpdateCouponRequest request) {
        log.info("[COUPON][UPDATE] couponId={}, request name={}, code={}, discountType={}, discountValue={}, status={}, validDaysAfterIssue={}",
                couponId, request.getName(), request.getCode(), request.getDiscountType(), request.getDiscountValue(), request.getStatus(), request.getValidDaysAfterIssue());

        Coupon coupon = selectCoupon(couponId);
        log.info("[COUPON][UPDATE][BEFORE] couponId={}, name={}, code={}, discountType={}, discountValue={}, maxDiscountAmount={}, minOrderAmount={}, validFrom={}, validUntil={}, validDaysAfterIssue={}, status={}",
                coupon.getCouponId(), coupon.getName(), coupon.getCode(), coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(), coupon.getMinOrderAmount(), coupon.getValidFrom(), coupon.getValidUntil(),
                coupon.getValidDaysAfterIssue(), coupon.getStatus());

        if (StringUtils.hasText(request.getCode()) && couponJpaRepository.existsByCodeAndCouponIdNot(request.getCode(), couponId)) {
            throw new IllegalArgumentException("이미 존재하는 쿠폰 코드입니다.");
        }

        validateCouponPolicy(
                request.getDiscountType() != null ? request.getDiscountType() : coupon.getDiscountType(),
                request.getDiscountValue() != null ? request.getDiscountValue() : coupon.getDiscountValue(),
                request.getMaxDiscountAmount() != null ? request.getMaxDiscountAmount() : coupon.getMaxDiscountAmount()
        );
        validateValidDaysAfterIssue(request.getValidDaysAfterIssue());

        coupon.update(request);
        Coupon updatedCoupon = couponJpaRepository.saveAndFlush(coupon);
        log.info("[COUPON][UPDATE][SUCCESS] couponId={}, name={}, code={}, discountType={}, discountValue={}, maxDiscountAmount={}, minOrderAmount={}, validFrom={}, validUntil={}, validDaysAfterIssue={}, status={}",
                updatedCoupon.getCouponId(), updatedCoupon.getName(), updatedCoupon.getCode(), updatedCoupon.getDiscountType(), updatedCoupon.getDiscountValue(),
                updatedCoupon.getMaxDiscountAmount(), updatedCoupon.getMinOrderAmount(), updatedCoupon.getValidFrom(), updatedCoupon.getValidUntil(),
                updatedCoupon.getValidDaysAfterIssue(), updatedCoupon.getStatus());

        return updatedCoupon.toResponse();
    }

    @Transactional(readOnly = true)
    public CouponResponse selectById(Long couponId) {
        return selectCoupon(couponId).toResponse();
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<CouponResponse> selectByCond(CouponCondRequest cond) {
        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<CouponResponse> page = selectCouponPage(cond, pageRequest).map(Coupon::toResponse);

        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public UserCouponResponse issue(IssueCouponRequest request) {
        Coupon coupon = selectCoupon(request.getCouponId());

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new IllegalArgumentException("활성화된 쿠폰만 발급할 수 있습니다.");
        }

        userCouponJpaRepository.findByUserIdAndCoupon(request.getUserId(), coupon)
                .ifPresent(userCoupon -> {
                    throw new IllegalArgumentException("이미 발급된 쿠폰입니다.");
                });

        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = resolveUserCouponExpiresAt(request, coupon, issuedAt);

        return userCouponJpaRepository.save(new UserCoupon(request.getUserId(), coupon, issuedAt, expiresAt)).toResponse();
    }

    @Transactional(readOnly = true)
    public List<UserCouponResponse> selectByUserId(String userId) {
        return userCouponJpaRepository.findByUserId(userId).stream()
                .map(UserCoupon::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponAvailabilityResponse checkAvailable(CouponAvailabilityRequest request) {
        UserCoupon userCoupon = userCouponJpaRepository.findById(request.getUserCouponId())
                .orElseThrow(() -> new IllegalArgumentException("사용 가능한 쿠폰이 존재하지 않습니다."));
        Coupon coupon = userCoupon.getCoupon();
        LocalDateTime now = LocalDateTime.now();

        String unavailableReason = getUnavailableReason(request, userCoupon, coupon, now);
        if (unavailableReason != null) {
            return CouponAvailabilityResponse.builder()
                    .available(false)
                    .discountAmount(0)
                    .reason(unavailableReason)
                    .build();
        }

        return CouponAvailabilityResponse.builder()
                .available(true)
                .discountAmount(calculateDiscountAmount(coupon, request.getOrderAmount()))
                .reason(null)
                .build();
    }

    private Coupon selectCoupon(Long couponId) {
        return couponJpaRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰이 존재하지 않습니다."));
    }

    private Page<Coupon> selectCouponPage(CouponCondRequest cond, PageRequest pageRequest) {
        QCoupon coupon = QCoupon.coupon;

        List<OrderSpecifier> orderSpecifiers = new ArrayList<>();
        pageRequest.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            PathBuilder<Coupon> entityPath = new PathBuilder<>(Coupon.class, "coupon");
            orderSpecifiers.add(new OrderSpecifier(direction, entityPath.get(order.getProperty())));
        });

        List<Coupon> content = queryFactory
                .selectFrom(coupon)
                .where(
                        nameLike(cond.getName()),
                        codeLike(cond.getCode()),
                        statusEq(cond.getStatus())
                )
                .offset(pageRequest.getOffset())
                .limit(pageRequest.getPageSize())
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .fetch();

        Long total = queryFactory
                .select(coupon.count())
                .from(coupon)
                .where(
                        nameLike(cond.getName()),
                        codeLike(cond.getCode()),
                        statusEq(cond.getStatus())
                )
                .fetchOne();

        return new PageImpl<>(content, pageRequest, total != null ? total : 0L);
    }

    private String getUnavailableReason(CouponAvailabilityRequest request, UserCoupon userCoupon, Coupon coupon, LocalDateTime now) {
        if (!request.getUserId().equals(userCoupon.getUserId())) {
            return "해당 사용자의 쿠폰이 아닙니다.";
        }

        if (userCoupon.getStatus() != UserCouponStatus.ISSUED) {
            return "이미 사용했거나 사용할 수 없는 쿠폰입니다.";
        }

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            return "활성화된 쿠폰이 아닙니다.";
        }

        if (coupon.getValidFrom() != null && coupon.getValidFrom().isAfter(now)) {
            return "아직 사용할 수 없는 쿠폰입니다.";
        }

        if (coupon.getValidUntil() != null && coupon.getValidUntil().isBefore(now)) {
            return "만료된 쿠폰입니다.";
        }

        if (userCoupon.getExpiresAt() != null && userCoupon.getExpiresAt().isBefore(now)) {
            return "만료된 쿠폰입니다.";
        }

        if (coupon.getMinOrderAmount() != null && request.getOrderAmount() < coupon.getMinOrderAmount()) {
            return "쿠폰 최소 주문 금액을 충족하지 못했습니다.";
        }

        if (calculateDiscountAmount(coupon, request.getOrderAmount()) <= 0) {
            return "쿠폰 할인 금액이 유효하지 않습니다.";
        }

        return null;
    }

    private int calculateDiscountAmount(Coupon coupon, int orderAmount) {
        int discountAmount;

        if (coupon.getDiscountType() == CouponDiscountType.FIXED_AMOUNT) {
            discountAmount = coupon.getDiscountValue();
        } else if (coupon.getDiscountType() == CouponDiscountType.PERCENT) {
            discountAmount = orderAmount * coupon.getDiscountValue() / 100;
            if (coupon.getMaxDiscountAmount() != null) {
                discountAmount = Math.min(discountAmount, coupon.getMaxDiscountAmount());
            }
        } else {
            throw new IllegalArgumentException("지원하지 않는 쿠폰 할인 방식입니다.");
        }

        return Math.min(discountAmount, orderAmount);
    }

    private LocalDateTime resolveUserCouponExpiresAt(IssueCouponRequest request, Coupon coupon, LocalDateTime issuedAt) {
        if (request.getExpiresAt() != null) {
            return request.getExpiresAt();
        }

        if (coupon.getValidDaysAfterIssue() != null && coupon.getValidDaysAfterIssue() > 0) {
            return issuedAt.plusDays(coupon.getValidDaysAfterIssue());
        }

        return coupon.getValidUntil();
    }

    private void validateCouponPolicy(CouponDiscountType discountType, Integer discountValue, Integer maxDiscountAmount) {
        if (discountType == null || discountValue == null || discountValue <= 0) {
            throw new IllegalArgumentException("쿠폰 할인 값을 확인해주세요.");
        }

        if (discountType == CouponDiscountType.PERCENT && discountValue > 100) {
            throw new IllegalArgumentException("퍼센트 할인은 100 이하로 입력해주세요.");
        }

        if (maxDiscountAmount != null && maxDiscountAmount < 0) {
            throw new IllegalArgumentException("최대 할인 금액은 0 이상이어야 합니다.");
        }
    }

    private void validateValidDaysAfterIssue(Integer validDaysAfterIssue) {
        if (validDaysAfterIssue != null && validDaysAfterIssue < 0) {
            throw new IllegalArgumentException("발급 후 유효 일수는 0 이상이어야 합니다.");
        }
    }

    private Sort makeSortInfo(List<String> sorts) {
        Sort sort = Sort.unsorted();
        if (sorts != null && !sorts.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();
            for (String infoStr : sorts) {
                String[] infos = infoStr.split("-");
                if (infos.length == 2) {
                    orders.add(new Sort.Order(Sort.Direction.fromString(infos[1]), infos[0]));
                }
            }
            sort = Sort.by(orders);
        }

        return sort;
    }

    private BooleanExpression nameLike(String name) {
        return StringUtils.hasText(name) ? QCoupon.coupon.name.like("%" + name + "%") : null;
    }

    private BooleanExpression codeLike(String code) {
        return StringUtils.hasText(code) ? QCoupon.coupon.code.like("%" + code + "%") : null;
    }

    private BooleanExpression statusEq(CouponStatus status) {
        return status != null ? QCoupon.coupon.status.eq(status) : null;
    }
}
