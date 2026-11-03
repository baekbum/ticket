package dev.bum.common.service.support.notice.dto;

import dev.bum.common.service.support.notice.enums.NoticeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNoticeRequest(
        // 제목.
        @NotBlank(message = "공지 제목을 입력해주세요.")
        @Size(max = 200, message = "공지 제목은 200자 이하여야 합니다.")
        String title,

        // 본문.
        @NotBlank(message = "공지 내용을 입력해주세요.")
        String content,

        // 분류.
        @NotNull(message = "공지 카테고리를 선택해주세요.")
        NoticeCategory category,

        // 상단 고정 여부.
        boolean pinned,

        // 발행 여부.
        boolean published
) {
}
