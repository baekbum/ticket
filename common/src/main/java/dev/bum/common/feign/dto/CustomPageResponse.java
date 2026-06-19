package dev.bum.common.feign.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter @Setter
@NoArgsConstructor // 역직렬화를 위해 기본 생성자 필수
public class CustomPageResponse<T> {
    private List<T> content;
    private PageMetadata page;

    @Getter @Setter
    @NoArgsConstructor
    public static class PageMetadata {
        private int size;
        private int number; // 현재 페이지 (0-indexed)
        private long totalElements;
        private int totalPages;
    }

    public static <T> CustomPageResponse<T> of(List<T> content, int size, int number, long totalElements, int totalPages) {
        CustomPageResponse<T> response = new CustomPageResponse<>();
        response.setContent(content);

        PageMetadata meta = new PageMetadata();
        meta.setSize(size);
        meta.setNumber(number);
        meta.setTotalElements(totalElements);
        meta.setTotalPages(totalPages);
        response.setPage(meta);

        return response;
    }
}
