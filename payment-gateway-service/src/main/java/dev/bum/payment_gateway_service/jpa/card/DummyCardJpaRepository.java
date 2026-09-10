package dev.bum.payment_gateway_service.jpa.card;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface DummyCardJpaRepository extends JpaRepository<DummyCard, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DummyCard c where c.dummyCardId = :id")
    Optional<DummyCard> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DummyCard> findByUserIdAndCardCompanyAndCardNumberHash(
            String userId,
            CardCompany cardCompany,
            String cardNumberHash
    );
}
