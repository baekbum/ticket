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
    private Long addressId;
    private String userId;
    private String alias;
    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address;
    private Boolean defaultAddress;
    private AddressStatus status;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    private List<String> sort;

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
