package dev.bum.support_service.jpa.faq;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqJpaRepository extends JpaRepository<Faq, Long> {
}
