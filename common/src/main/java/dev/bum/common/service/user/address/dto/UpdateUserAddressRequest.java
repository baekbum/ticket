package dev.bum.common.service.user.address.dto;

import dev.bum.common.service.user.address.enums.AddressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserAddressRequest {
    private String alias;
    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address;
    private String detailAddress;
    private Boolean defaultAddress;
    private AddressStatus status;
}
