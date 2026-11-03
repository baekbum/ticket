package dev.bum.common.service.user.address.dto;

import dev.bum.common.service.user.address.enums.AddressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.StringJoiner;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAddressCondRequest {

    // 배송지 식별자.
    private Long addressId;

    // 사용자 ID.
    private String userId;

    // 표시용 별칭.
    private String alias;

    // 수령인 이름.
    private String recipientName;

    // 수령인 연락처.
    private String recipientPhone;

    // 우편번호.
    private String zipCode;

    // 주소.
    private String address;

    // 기본 배송지 여부.
    private Boolean defaultAddress;

    // 사용자 배송지 상태.
    private AddressStatus status;

    // 조회할 페이지.
    @Builder.Default
    private Integer page = 0;

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 10;

    // 조회 결과 정렬 조건.
    private List<String> sort;

    // 객체의 문자열 표현을 반환한다.
    @Override
    public String toString() {

        StringJoiner sj = new StringJoiner(", ", "UserAddressCond{", "}");
        sj.add("page=" + page);
        sj.add("size=" + size);
        if (addressId != null) sj.add("addressId=" + addressId);

        if (userId != null) sj.add("userId='" + userId + "'");

        if (alias != null) sj.add("alias='" + alias + "'");

        if (recipientName != null) sj.add("recipientName='" + recipientName + "'");

        if (recipientPhone != null) sj.add("recipientPhone='" + recipientPhone + "'");

        if (zipCode != null) sj.add("zipCode='" + zipCode + "'");

        if (address != null) sj.add("address='" + address + "'");

        if (defaultAddress != null) sj.add("defaultAddress=" + defaultAddress);

        if (status != null) sj.add("status=" + status);

        if (sort != null && !sort.isEmpty()) sj.add("sort=[" + String.join(", ", sort) + "]");
        return sj.toString();
    }
}
