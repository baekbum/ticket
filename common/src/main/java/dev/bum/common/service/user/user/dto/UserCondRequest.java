package dev.bum.common.service.user.user.dto;

import lombok.*;
import dev.bum.common.service.user.user.enums.UserStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.StringJoiner;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCondRequest {

    // 사용자 ID.
    private String userId;

    // 이름.
    private String name;

    // 휴대전화 번호.
    private String phoneNumber;

    // 이메일 주소.
    private String email;

    // 생년월일.
    private LocalDate birthDate;

    // 주소.
    private String address;

    // 블랙리스트 등록 여부.
    private Boolean isBlacklisted;

    // 사용자 상태.
    private UserStatus status;

    // 회원 등급.
    private String grade;

    // 조회할 페이지.
    @Builder.Default // 빌더 패턴을 사용해서 만들 때도 기본값을 유지
    private Integer page = 0; // page 필드에 기본값 0 할당

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 10; // size 필드에 기본값 10 할당

    // 조회 결과 정렬 조건.
    List<String> sort; // "sort": ["teamName-asc","createdAt-desc"] 예시

    // 객체의 문자열 표현을 반환한다.
    @Override
    public String toString() {

        // 🌟 콤마(,) 조립을 편하게 하기 위해 StringJoiner 활용
        StringJoiner sj = new StringJoiner(", ", "UserCond{", "}");

        // 필수 필드 (기본값이 항상 존재하므로 무조건 포함)
        sj.add("page=" + page);
        sj.add("size=" + size);

        // 단건 검색 필드 검증
        if (userId != null) sj.add("userId='" + userId + "'");

        if (name != null) sj.add("name='" + name + "'");

        if (phoneNumber != null) sj.add("phoneNumber='" + phoneNumber + "'");

        if (email != null) sj.add("email='" + email + "'");

        if (birthDate != null) sj.add("birthDate=" + birthDate);

        if (address != null) sj.add("address='" + address + "'");

        if (isBlacklisted != null) sj.add("isBlacklisted=" + isBlacklisted);

        if (status != null) sj.add("status=" + status);

        if (grade != null) sj.add("grade='" + grade + "'");

        // 정렬 조건 필드 검증
        if (sort != null && !sort.isEmpty()) {
            sj.add("sort=[" + String.join(", ", sort) + "]");
        }

        return sj.toString();
    }
}
