package dev.bum.user_service.vo;

import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.StringJoiner;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCond {

    private String userId;
    private String name;
    private String phoneNumber;
    private String email;
    private LocalDate birthDate;
    private String address;
    private Boolean isBlacklisted;

    @Builder.Default // 빌더 패턴을 사용해서 만들 때도 기본값을 유지
    private Integer page = 0; // page 필드에 기본값 0 할당

    @Builder.Default
    private Integer size = 10; // size 필드에 기본값 10 할당

    List<String> sort; // "sort": ["teamName-asc","createdAt-desc"] 예시

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

        // 정렬 조건 필드 검증
        if (sort != null && !sort.isEmpty()) {
            sj.add("sort=[" + String.join(", ", sort) + "]");
        }

        return sj.toString();
    }
}
