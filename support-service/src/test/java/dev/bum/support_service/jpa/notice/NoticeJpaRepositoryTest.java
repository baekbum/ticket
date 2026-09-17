package dev.bum.support_service.jpa.notice;

import dev.bum.support_service.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({QuerydslConfig.class, NoticeRepositoryImpl.class})
class NoticeJpaRepositoryTest {

    @Autowired
    private NoticeJpaRepository noticeJpaRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Test
    void 공개_공지사항의_조회수를_원자적으로_증가시킨다() {
        Notice notice = Notice.create("점검 안내", "점검 내용", NoticeCategory.MAINTENANCE, "admin");
        notice.publish();
        Notice savedNotice = noticeJpaRepository.saveAndFlush(notice);

        noticeRepository.increaseViewCount(savedNotice.getId());
        Notice selectedNotice = noticeJpaRepository.findById(savedNotice.getId()).orElseThrow();

        assertThat(selectedNotice.getViewCount()).isOne();
    }

    @Test
    void 공개_상태와_카테고리로_공지사항을_조회한다() {
        Notice published = Notice.create("결제 안내", "결제 내용", NoticeCategory.SERVICE, "admin");
        published.publish();
        noticeJpaRepository.save(published);
        noticeJpaRepository.save(Notice.create("초안", "작성 중", NoticeCategory.SERVICE, "admin"));
        noticeJpaRepository.flush();

        var result = noticeRepository.findPublished(
                NoticeCategory.SERVICE,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Notice::getTitle)
                .containsExactly("결제 안내");
    }
}
