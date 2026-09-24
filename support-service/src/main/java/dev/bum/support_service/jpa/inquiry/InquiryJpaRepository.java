package dev.bum.support_service.jpa.inquiry;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InquiryJpaRepository extends JpaRepository<Inquiry, Long>, JpaSpecificationExecutor<Inquiry> {

    Optional<Inquiry> findByIdAndRequesterId(Long inquiryId, String requesterId);

    Page<Inquiry> findAllByRequesterId(String requesterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inquiry from Inquiry inquiry where inquiry.id = :inquiryId")
    Optional<Inquiry> findByIdForUpdate(@Param("inquiryId") Long inquiryId);
}
