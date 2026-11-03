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

    // 자주 묻는 질문 데이터를 조회하고 저장하는 레포지토리.
    private final FaqRepository faqRepository;

    /**
     * 자주 묻는 질문을 생성하고 저장한다.
     */
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

    /**
     * 자주 묻는 질문 정보를 식별자로 조회한다.
     */
    @Transactional(readOnly = true)
    public FaqResponse selectById(Long faqId) {

        return FaqMapper.toResponse(findById(faqId));
    }

    /**
     * 자주 묻는 질문 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<FaqResponse> select(FaqSearchRequest request) {

        Page<FaqResponse> result = faqRepository.findForManagement(
                        request,
                        PageRequest.of(request.getPage(), request.getSize())
                )
                .map(FaqMapper::toResponse);

        return toPageResponse(result);
    }

    /**
     * 자주 묻는 질문을 수정한다.
     */
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

    /**
     * 자주 묻는 질문을 삭제한다.
     */
    public void delete(Long faqId) {

        findById(faqId).archive();
    }

    /**
     * 자주 묻는 질문 정보를 식별자로 조회한다.
     */
    private Faq findById(Long faqId) {

        return faqRepository.findById(faqId);
    }

    /**
     * 처리 상태를 변경한다.
     */
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

    /**
     * 조회 결과와 페이지 정보를 응답으로 변환한다.
     */
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
