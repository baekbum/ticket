package dev.bum.common.service.support.notice.dto;

import dev.bum.common.service.support.notice.enums.NoticeCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoticeSearchRequest {
    private PublicationStatus status;
    private NoticeCategory category;
    private String keyword;

    @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
    private int page = 0;

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    private int size = 10;
}
