package dev.bum.user_service.vo;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCond {

    private String userId;
    private List<String> userIdList;
    private String name;
    private List<String> nameList;
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
}
