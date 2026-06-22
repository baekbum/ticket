package dev.bum.common.service.ticket.seat.dto;

import lombok.*;

import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeleteSeatRequest {
    List<Long> seatIdList;

    @Override
    public String toString() {
        if (seatIdList == null) {
            return "DeleteSeatInfo{seatIdList=[]}";
        }

        // List 내부의 Long ID들을 대시(-)나 콤마(,)로 연결하여 한 줄로 정렬
        String ids = seatIdList.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ")); // 쉼표와 공백으로 구분 (예: 1, 2, 3)

        return "DeleteSeatInfo{seatIdList=[" + ids + "]}";
    }
}
