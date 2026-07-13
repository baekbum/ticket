package dev.bum.common.service.user.address.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsertUserAddressRequest {
    private String userId;
    private String alias;

    @NotBlank
    private String recipientName;

    @NotBlank
    private String recipientPhone;

    @NotBlank
    private String zipCode;

    @NotBlank
    private String address;

    private String detailAddress;
    private Boolean defaultAddress;
}
