package dev.bum.common.service.support.faq.dto;

import dev.bum.common.service.support.faq.enums.FaqCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FaqSearchRequest {

    // 자주 묻는 질문 상태.
    private PublicationStatus status;

    // 분류.
    private FaqCategory category;

    // 검색어.
    private String keyword;

    // 조회할 페이지.
    @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
    private int page = 0;

    // 페이지당 조회 건수.
    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    private int size = 10;
}
