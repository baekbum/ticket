package dev.bum.user_service.jpa.address;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.user.address.dto.InsertUserAddressRequest;
import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import dev.bum.common.service.user.address.enums.AddressStatus;
import dev.bum.user_service.exception.UserAddressNotExistException;
import dev.bum.user_service.jpa.address.QUserAddress;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserAddressRepositoryImpl implements UserAddressRepository {

    private final JPAQueryFactory queryFactory;
    private final UserAddressJpaRepository jpaRepository;
    private final UserRepository userRepository;
    private QUserAddress userAddress;

    @Override
    public UserAddress insert(String userId, InsertUserAddressRequest info) {
        User user = userRepository.selectById(userId);
        boolean hasActiveAddress = jpaRepository.existsByUserUserIdAndStatus(userId, AddressStatus.ACTIVE);
        boolean defaultAddress = Boolean.TRUE.equals(info.getDefaultAddress()) || !hasActiveAddress;

        if (defaultAddress) {
            unsetDefaultAddresses(userId);
        }

        UserAddress address = new UserAddress(user, info, defaultAddress);
        return jpaRepository.save(address);
    }

    @Override
    public UserAddress selectById(Long addressId) {
        return jpaRepository.findById(addressId)
                .orElseThrow(() -> new UserAddressNotExistException("해당 배송지를 찾을 수 없습니다."));
    }

    @Override
    public Page<UserAddress> selectByCond(UserAddressCondRequest cond, Pageable pageable) {
        userAddress = QUserAddress.userAddress;

        List<OrderSpecifier> orderSpecifiers = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            String property = order.getProperty();
            PathBuilder<UserAddress> entityPath = new PathBuilder<>(UserAddress.class, "userAddress");
            orderSpecifiers.add(new OrderSpecifier(direction, entityPath.get(property)));
        });

        List<UserAddress> content = queryFactory
                .select(userAddress)
                .from(userAddress)
                .where(
                        addressIdEq(cond.getAddressId()),
                        userIdContains(cond.getUserId()),
                        aliasContains(cond.getAlias()),
                        recipientNameContains(cond.getRecipientName()),
                        recipientPhoneContains(cond.getRecipientPhone()),
                        zipCodeEq(cond.getZipCode()),
                        addressContains(cond.getAddress()),
                        defaultAddressEq(cond.getDefaultAddress()),
                        statusEq(cond.getStatus())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .fetch();

        Long total = queryFactory
                .select(userAddress.count())
                .from(userAddress)
                .where(
                        addressIdEq(cond.getAddressId()),
                        userIdContains(cond.getUserId()),
                        aliasContains(cond.getAlias()),
                        recipientNameContains(cond.getRecipientName()),
                        recipientPhoneContains(cond.getRecipientPhone()),
                        zipCodeEq(cond.getZipCode()),
                        addressContains(cond.getAddress()),
                        defaultAddressEq(cond.getDefaultAddress()),
                        statusEq(cond.getStatus())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public UserAddress update(Long addressId, UpdateUserAddressRequest info) {
        UserAddress address = selectById(addressId);

        if (Boolean.TRUE.equals(info.getDefaultAddress())) {
            unsetDefaultAddresses(address.getUser().getUserId());
        }

        address.updateInfo(info);
        return address;
    }

    @Override
    public UserAddress delete(Long addressId) {
        UserAddress address = selectById(addressId);
        address.delete();
        return address;
    }

    @Override
    public void unsetDefaultAddresses(String userId) {
        jpaRepository.findByUserUserIdAndDefaultAddressTrueAndStatus(userId, AddressStatus.ACTIVE)
                .forEach(address -> address.markDefault(false));
        jpaRepository.flush();
    }

    private BooleanExpression addressIdEq(Long addressId) {
        return addressId != null ? userAddress.addressId.eq(addressId) : null;
    }

    private BooleanExpression userIdContains(String userId) {
        return StringUtils.hasText(userId) ? userAddress.user.userId.containsIgnoreCase(userId) : null;
    }

    private BooleanExpression aliasContains(String alias) {
        return StringUtils.hasText(alias) ? userAddress.alias.containsIgnoreCase(alias) : null;
    }

    private BooleanExpression recipientNameContains(String recipientName) {
        return StringUtils.hasText(recipientName) ? userAddress.recipientName.contains(recipientName) : null;
    }

    private BooleanExpression recipientPhoneContains(String recipientPhone) {
        return StringUtils.hasText(recipientPhone) ? userAddress.recipientPhone.contains(recipientPhone) : null;
    }

    private BooleanExpression zipCodeEq(String zipCode) {
        return StringUtils.hasText(zipCode) ? userAddress.zipCode.eq(zipCode) : null;
    }

    private BooleanExpression addressContains(String address) {
        return StringUtils.hasText(address) ? userAddress.address.contains(address) : null;
    }

    private BooleanExpression defaultAddressEq(Boolean defaultAddress) {
        return defaultAddress != null ? userAddress.defaultAddress.eq(defaultAddress) : null;
    }

    private BooleanExpression statusEq(AddressStatus status) {
        return status != null ? userAddress.status.eq(status) : null;
    }
}
