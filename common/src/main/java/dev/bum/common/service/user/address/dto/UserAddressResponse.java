package dev.bum.common.service.user.address.dto;

import dev.bum.common.service.user.address.enums.AddressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAddressResponse {
    private Long addressId;
    private Long userPk;
    private String userId;
    private String alias;
    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address;
    private String detailAddress;
    private Boolean defaultAddress;
    private AddressStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
