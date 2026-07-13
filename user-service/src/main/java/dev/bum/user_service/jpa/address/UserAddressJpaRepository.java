package dev.bum.user_service.jpa.address;

import dev.bum.common.service.user.address.enums.AddressStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAddressJpaRepository extends JpaRepository<UserAddress, Long> {
    boolean existsByUserUserIdAndStatus(String userId, AddressStatus status);
    List<UserAddress> findByUserUserIdAndDefaultAddressTrueAndStatus(String userId, AddressStatus status);
}
