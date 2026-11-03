package dev.bum.common.service.user.address.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeleteUserAddressBulkRequest {

    // 배송지 식별자 목록.
    @NotEmpty
    private List<Long> addressIds;
}
