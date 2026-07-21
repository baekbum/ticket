package dev.bum.ticket_service.service.coupon.coupon;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.InsertCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    @AuditLog(action = "COUPON_CREATE", targetType = "COUPON")
    public CouponResponse insert(InsertCouponRequest request) {
        log.info("[COUPON][INSERT] request name={}, code={}, discountType={}, discountValue={}, status={}, validDaysAfterIssue={}",
                request.getName(), request.getCode(), request.getDiscountType(), request.getDiscountValue(), request.getStatus(), request.getValidDaysAfterIssue());

        validateCouponPolicy(request.getDiscountType(), request.getDiscountValue(), request.getMaxDiscountAmount());
        validateValidDaysAfterIssue(request.getValidDaysAfterIssue());

        if (couponRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("이미 존재하는 쿠폰 코드입니다.");
        }

        Coupon savedCoupon = couponRepository.insert(new Coupon(request));
        log.info("[COUPON][INSERT][SUCCESS] couponId={}, code={}, status={}",
                savedCoupon.getCouponId(), savedCoupon.getCode(), savedCoupon.getStatus());

        return savedCoupon.toResponse();
    }

    @AuditLog(action = "COUPON_UPDATE", targetType = "COUPON")
    public CouponResponse update(Long couponId, UpdateCouponRequest request) {
        log.info("[COUPON][UPDATE] couponId={}, request name={}, code={}, discountType={}, discountValue={}, status={}, validDaysAfterIssue={}",
                couponId, request.getName(), request.getCode(), request.getDiscountType(), request.getDiscountValue(), request.getStatus(), request.getValidDaysAfterIssue());

        Coupon coupon = selectCoupon(couponId);
        AuditDataMapper.setChangedData(coupon, request);
        log.info("[COUPON][UPDATE][BEFORE] couponId={}, name={}, code={}, discountType={}, discountValue={}, maxDiscountAmount={}, minOrderAmount={}, validFrom={}, validUntil={}, validDaysAfterIssue={}, status={}",
                coupon.getCouponId(), coupon.getName(), coupon.getCode(), coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(), coupon.getMinOrderAmount(), coupon.getValidFrom(), coupon.getValidUntil(),
                coupon.getValidDaysAfterIssue(), coupon.getStatus());

        if (StringUtils.hasText(request.getCode()) && couponRepository.existsByCodeExceptId(request.getCode(), couponId)) {
            throw new IllegalArgumentException("이미 존재하는 쿠폰 코드입니다.");
        }

        validateCouponPolicy(
                request.getDiscountType() != null ? request.getDiscountType() : coupon.getDiscountType(),
                request.getDiscountValue() != null ? request.getDiscountValue() : coupon.getDiscountValue(),
                request.getMaxDiscountAmount() != null ? request.getMaxDiscountAmount() : coupon.getMaxDiscountAmount()
        );
        validateValidDaysAfterIssue(request.getValidDaysAfterIssue());

        coupon.update(request);
        Coupon updatedCoupon = couponRepository.update(coupon);
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
        Page<CouponResponse> page = couponRepository.selectByCond(cond, pageRequest).map(Coupon::toResponse);

        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private Coupon selectCoupon(Long couponId) {
        return couponRepository.selectById(couponId);
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

}
