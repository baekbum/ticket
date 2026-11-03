package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.seat.vo.UpdateSeatAreaConfig;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateSeatRequest {

    // 좌석 수정에 사용할 구역별 설정.
    private List<UpdateSeatAreaConfig> updateSeatAreaConfigs;

    // 객체의 문자열 표현을 반환한다.
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("UpdateSeatInfo{");
        if (updateSeatAreaConfigs == null) {
            sb.append("updateSeatAreaConfigs=null");
        } else {
            sb.append("updateSeatAreaConfigs=[\n");
            for (int i = 0; i < updateSeatAreaConfigs.size(); i++) {
                sb.append("    ").append(updateSeatAreaConfigs.get(i).toString());
                if (i < updateSeatAreaConfigs.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("\n  ]");
        }
        sb.append('}');
        return sb.toString();
    }
}
