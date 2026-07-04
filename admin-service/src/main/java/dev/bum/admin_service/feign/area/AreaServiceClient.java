package dev.bum.admin_service.feign.area;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaJsonRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "area-service", url = "${services.ticket-service.url}", path = "/api/v1/area")
public interface AreaServiceClient {

    @PostMapping("/insert")
    AreaResponse insert(@Valid @RequestBody InsertAreaRequest info);

    @PostMapping("/insert/bulk")
    List<AreaResponse> insertBulk(@Valid @RequestBody InsertAreaBulkRequest info);

    @PostMapping("/insert/json")
    List<AreaResponse> insertJson(@Valid @RequestBody InsertAreaJsonRequest info);

    @GetMapping("/select/id/{areaId}")
    AreaResponse selectById(@PathVariable("areaId") Long areaId);

    @PostMapping("/select")
    CustomPageResponse<AreaResponse> selectByCond(@RequestBody AreaCondRequest cond);

    @PutMapping("/update/id/{areaId}")
    AreaResponse update(@PathVariable("areaId") Long areaId, @Valid @RequestBody UpdateAreaRequest info);

    @DeleteMapping("/delete/id/{areaId}")
    AreaResponse delete(@PathVariable("areaId") Long areaId);

    @DeleteMapping("/delete/bulk")
    void deleteBulk(@Valid @RequestBody DeleteAreaBulkRequest info);
}
