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

    // 페이지당 조회할 수 있는 최대 항목 수.
    private static final int MAX_PAGE_SIZE = 100;
    // 공지사항 데이터를 조회하고 저장하는 레포지토리.
    private final NoticeRepository noticeRepository;

    /**
     * 공개된 항목을 조회한다.
     */
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

    /**
     * 식별자로 공개된 항목을 조회한다.
     */
    @Transactional
    public NoticeResponse selectPublishedById(Long noticeId) {

        noticeRepository.increaseViewCount(noticeId);
        return NoticeMapper.toResponse(noticeRepository.findPublishedById(noticeId));
    }

    /**
     * 페이지 번호, 크기와 정렬 조건을 구성한다.
     */
    private PageRequest pageRequest(int page, int size) {

        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지는 0 이상, 페이지 크기는 1~100이어야 합니다.");
        }
        return PageRequest.of(page, size);
    }

    /**
     * 조회 결과와 페이지 정보를 응답으로 변환한다.
     */
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
