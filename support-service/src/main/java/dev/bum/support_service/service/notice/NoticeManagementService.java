package dev.bum.support_service.service.notice;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.notice.dto.CreateNoticeRequest;
import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.common.service.support.notice.dto.NoticeSearchRequest;
import dev.bum.common.service.support.notice.dto.UpdateNoticeRequest;
import dev.bum.common.service.support.notice.enums.PublicationStatus;
import dev.bum.support_service.jpa.notice.Notice;
import dev.bum.support_service.jpa.notice.NoticeRepository;
import dev.bum.support_service.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class NoticeManagementService {

    private final NoticeRepository noticeRepository;

    public NoticeResponse insert(CreateNoticeRequest request, String authorId) {
        if (!StringUtils.hasText(authorId)) {
            throw new IllegalArgumentException("공지 작성자 정보가 필요합니다.");
        }

        Notice notice = Notice.create(
                request.title().trim(),
                request.content().trim(),
                request.category(),
                authorId.trim()
        );
        notice.changePinned(request.pinned());
        if (request.published()) {
            notice.publish();
        }

        return NoticeMapper.toResponse(noticeRepository.save(notice));
    }

    @Transactional(readOnly = true)
    public NoticeResponse selectById(Long noticeId) {
        return NoticeMapper.toResponse(findById(noticeId));
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<NoticeResponse> select(NoticeSearchRequest request) {
        Page<NoticeResponse> result = noticeRepository.findForManagement(
                        request,
                        PageRequest.of(request.getPage(), request.getSize())
                )
                .map(NoticeMapper::toResponse);

        return CustomPageResponse.of(
                result.getContent(),
                result.getSize(),
                result.getNumber(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    public NoticeResponse update(Long noticeId, UpdateNoticeRequest request) {
        Notice notice = findById(noticeId);
        notice.update(request.title().trim(), request.content().trim(), request.category());
        notice.changePinned(request.pinned());
        changeStatus(notice, request.status());
        return NoticeMapper.toResponse(notice);
    }

    public void delete(Long noticeId) {
        Notice notice = findById(noticeId);
        notice.archive();
    }

    private Notice findById(Long noticeId) {
        return noticeRepository.findById(noticeId);
    }

    private void changeStatus(Notice notice, PublicationStatus requestedStatus) {
        if (notice.getStatus() == requestedStatus) {
            return;
        }

        switch (requestedStatus) {
            case DRAFT -> notice.draft();
            case PUBLISHED -> notice.publish();
            case HIDDEN -> notice.hide();
            case ARCHIVED -> notice.archive();
        }
    }
}
