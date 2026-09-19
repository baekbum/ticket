package dev.bum.support_service.service.faq;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.faq.dto.CreateFaqRequest;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.dto.FaqSearchRequest;
import dev.bum.common.service.support.faq.dto.UpdateFaqRequest;
import dev.bum.common.service.support.notice.enums.PublicationStatus;
import dev.bum.support_service.jpa.faq.Faq;
import dev.bum.support_service.jpa.faq.FaqRepository;
import dev.bum.support_service.mapper.FaqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class FaqManagementService {

    private final FaqRepository faqRepository;

    public FaqResponse insert(CreateFaqRequest request, String authorId) {
        if (!StringUtils.hasText(authorId)) {
            throw new IllegalArgumentException("FAQ 작성자 정보가 필요합니다.");
        }

        Faq faq = Faq.create(
                request.question().trim(),
                request.answer().trim(),
                request.category(),
                request.displayOrder(),
                authorId.trim()
        );
        if (request.published()) {
            faq.publish();
        }

        return FaqMapper.toResponse(faqRepository.save(faq));
    }

    @Transactional(readOnly = true)
    public FaqResponse selectById(Long faqId) {
        return FaqMapper.toResponse(findById(faqId));
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<FaqResponse> select(FaqSearchRequest request) {
        Page<FaqResponse> result = faqRepository.findForManagement(
                        request,
                        PageRequest.of(request.getPage(), request.getSize())
                )
                .map(FaqMapper::toResponse);

        return toPageResponse(result);
    }

    public FaqResponse update(Long faqId, UpdateFaqRequest request) {
        Faq faq = findById(faqId);
        faq.update(
                request.question().trim(),
                request.answer().trim(),
                request.category(),
                request.displayOrder()
        );
        changeStatus(faq, request.status());
        return FaqMapper.toResponse(faq);
    }

    public void delete(Long faqId) {
        findById(faqId).archive();
    }

    private Faq findById(Long faqId) {
        return faqRepository.findById(faqId);
    }

    private void changeStatus(Faq faq, PublicationStatus requestedStatus) {
        if (faq.getStatus() == requestedStatus) {
            return;
        }

        switch (requestedStatus) {
            case DRAFT -> faq.draft();
            case PUBLISHED -> faq.publish();
            case HIDDEN -> faq.hide();
            case ARCHIVED -> faq.archive();
        }
    }

    private CustomPageResponse<FaqResponse> toPageResponse(Page<FaqResponse> page) {
        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
