package dev.bum.payment_gateway_service.jpa.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantJpaRepository extends JpaRepository<Merchant, Long> {
}
