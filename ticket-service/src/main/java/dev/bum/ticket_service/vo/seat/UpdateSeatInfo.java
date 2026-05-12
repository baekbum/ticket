package dev.bum.ticket_service.vo.seat;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UpdateSeatInfo {
    private List<UpdateSeatAreaConfig> updateSeatAreaConfigs;
}
