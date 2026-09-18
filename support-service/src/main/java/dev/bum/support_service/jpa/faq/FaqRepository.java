package dev.bum.support_service.jpa.faq;

import dev.bum.common.service.support.faq.dto.FaqSearchRequest;
import dev.bum.common.service.support.faq.enums.FaqCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FaqRepository {

    Faq save(Faq faq);

    Faq findById(Long faqId);

    Page<Faq> findPublished(
            FaqCategory category,
            String keyword,
            Pageable pageable
    );

    Page<Faq> findForManagement(
            FaqSearchRequest request,
            Pageable pageable
    );
}
