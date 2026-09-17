package dev.bum.support_service.dto.notice;

import dev.bum.support_service.jpa.notice.NoticeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNoticeRequest(
        @NotBlank(message = "공지 제목을 입력해주세요.")
        @Size(max = 200, message = "공지 제목은 200자 이하여야 합니다.")
        String title,

        @NotBlank(message = "공지 내용을 입력해주세요.")
        String content,

        @NotNull(message = "공지 카테고리를 선택해주세요.")
        NoticeCategory category,

        boolean pinned,
        boolean published
) {
}
