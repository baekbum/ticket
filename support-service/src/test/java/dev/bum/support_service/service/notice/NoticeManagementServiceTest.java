package dev.bum.support_service.service.notice;

import dev.bum.support_service.dto.notice.CreateNoticeRequest;
import dev.bum.support_service.dto.notice.UpdateNoticeRequest;
import dev.bum.support_service.jpa.common.PublicationStatus;
import dev.bum.support_service.jpa.notice.Notice;
import dev.bum.support_service.jpa.notice.NoticeCategory;
import dev.bum.support_service.jpa.notice.NoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeManagementServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    private NoticeManagementService noticeManagementService;

    @BeforeEach
    void setUp() {
        noticeManagementService = new NoticeManagementService(noticeRepository);
    }

    @Test
    void 공개_공지사항을_생성한다() {
        CreateNoticeRequest request = new CreateNoticeRequest(
                "  서비스 점검 안내  ",
                "  점검 시간은 자정입니다.  ",
                NoticeCategory.MAINTENANCE,
                true,
                true
        );
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = noticeManagementService.insert(request, "admin");

        assertThat(response.title()).isEqualTo("서비스 점검 안내");
        assertThat(response.content()).isEqualTo("점검 시간은 자정입니다.");
        assertThat(response.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(response.pinned()).isTrue();
        assertThat(response.authorId()).isEqualTo("admin");
        assertThat(response.publishedAt()).isNotNull();
    }

    @Test
    void 공지사항을_수정하고_게시한다() {
        Notice notice = Notice.create("기존 제목", "기존 내용", NoticeCategory.GENERAL, "admin");
        when(noticeRepository.findById(1L)).thenReturn(notice);
        UpdateNoticeRequest request = new UpdateNoticeRequest(
                "수정 제목",
                "수정 내용",
                NoticeCategory.SERVICE,
                PublicationStatus.PUBLISHED,
                true
        );

        var response = noticeManagementService.update(1L, request);

        assertThat(response.title()).isEqualTo("수정 제목");
        assertThat(response.category()).isEqualTo(NoticeCategory.SERVICE);
        assertThat(response.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(response.pinned()).isTrue();
    }

    @Test
    void 공지사항_삭제는_보관_상태로_전환한다() {
        Notice notice = Notice.create("제목", "내용", NoticeCategory.GENERAL, "admin");
        notice.changePinned(true);
        when(noticeRepository.findById(1L)).thenReturn(notice);

        noticeManagementService.delete(1L);

        assertThat(notice.getStatus()).isEqualTo(PublicationStatus.ARCHIVED);
        assertThat(notice.isPinned()).isFalse();
    }
}
