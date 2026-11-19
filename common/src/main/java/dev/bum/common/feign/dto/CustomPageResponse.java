package dev.bum.common.feign.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter @Setter
@NoArgsConstructor // 역직렬화를 위해 기본 생성자 필수
public class CustomPageResponse<T> {

    // 조회 결과 목록.
    private List<T> content;

    // 조회 결과의 페이지 메타데이터.
    private PageMetadata page;

    @Getter @Setter
    @NoArgsConstructor
    public static class PageMetadata {

        // 페이지당 조회 건수.
        private int size;

        // 현재 페이지 번호(0부터 시작).
        private int number; // 현재 페이지 (0-indexed)

        // 전체 조회 결과 수.
        private long totalElements;

        // 전체 페이지 수.
        private int totalPages;
    }

    // 전달받은 값으로 페이지 응답 객체를 구성한다.
    public static <T> CustomPageResponse<T> of(
            List<T> content,
            int size,
            int number,
            long totalElements,
            int totalPages
    ) {

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
