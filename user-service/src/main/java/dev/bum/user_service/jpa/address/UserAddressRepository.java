package dev.bum.user_service.jpa.address;

import dev.bum.common.service.user.address.dto.InsertUserAddressRequest;
import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserAddressRepository {
    UserAddress insert(String userId, InsertUserAddressRequest info);
    UserAddress selectById(Long addressId);
    Page<UserAddress> selectByCond(UserAddressCondRequest cond, Pageable pageable);
    UserAddress update(Long addressId, UpdateUserAddressRequest info);
    UserAddress delete(Long addressId);
    void unsetDefaultAddresses(String userId);
}
