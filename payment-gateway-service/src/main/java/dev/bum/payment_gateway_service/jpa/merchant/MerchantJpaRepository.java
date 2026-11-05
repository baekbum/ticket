package dev.bum.payment_gateway_service.jpa.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantJpaRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByCredentialKey(String credentialKey);
}
