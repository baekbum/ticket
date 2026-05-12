package dev.bum.ticket_service.vo.seat;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class InsertSeatInfo {

    @NotNull
    private Long eventId;

    @NotNull
    private List<InsertSeatAreaConfig> insertSeatAreaConfigs;

    /*
    {
      "userId": "bum123",
      "eventId": 1,
      "seatConfigs": [
        {
          "grade": "VIP",
          "zone": "FLOOR-A",
          "rows": 20,
          "cols": 30,
          "price": 165000
        },
        {
          "grade": "R",
          "zone": "28구역",
          "rows": 20,
          "cols": 30,
          "price": 145000
        },
        {
          "grade": "R",
          "zone": "30구역",
          "rows": 20,
          "cols": 30,
          "price": 145000
        }
      ]
    }
     */
}
