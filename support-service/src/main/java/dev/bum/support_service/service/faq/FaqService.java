package dev.bum.support_service.service.faq;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.enums.FaqCategory;
import dev.bum.support_service.jpa.faq.FaqRepository;
import dev.bum.support_service.mapper.FaqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private static final int MAX_PAGE_SIZE = 100;

    private final FaqRepository faqRepository;

    public CustomPageResponse<FaqResponse> selectPublished(
            FaqCategory category,
            String keyword,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지는 0 이상, 페이지 크기는 1~100이어야 합니다.");
        }

        Page<FaqResponse> result = faqRepository.findPublished(
                        category,
                        keyword,
                        PageRequest.of(page, size)
                )
                .map(FaqMapper::toResponse);

        return CustomPageResponse.of(
                result.getContent(),
                result.getSize(),
                result.getNumber(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
