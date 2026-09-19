package dev.bum.support_service.service.notice;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.common.service.support.notice.enums.NoticeCategory;
import dev.bum.support_service.jpa.notice.NoticeRepository;
import dev.bum.support_service.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NoticeRepository noticeRepository;

    public CustomPageResponse<NoticeResponse> selectPublished(
            NoticeCategory category,
            String keyword,
            int page,
            int size
    ) {
        PageRequest pageRequest = pageRequest(page, size);
        Page<NoticeResponse> result = noticeRepository.findPublished(category, keyword, pageRequest)
                .map(NoticeMapper::toResponse);

        return toPageResponse(result);
    }

    @Transactional
    public NoticeResponse selectPublishedById(Long noticeId) {
        noticeRepository.increaseViewCount(noticeId);
        return NoticeMapper.toResponse(noticeRepository.findPublishedById(noticeId));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지는 0 이상, 페이지 크기는 1~100이어야 합니다.");
        }
        return PageRequest.of(page, size);
    }

    private CustomPageResponse<NoticeResponse> toPageResponse(Page<NoticeResponse> page) {
        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

}
