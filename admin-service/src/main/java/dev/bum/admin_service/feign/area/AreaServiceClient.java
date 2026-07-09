package dev.bum.admin_service.feign.area;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaJsonRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.layout.dto.EventLayoutResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@FeignClient(name = "area-service", url = "${services.ticket-service.url}", path = "/api/v1/area")
public interface AreaServiceClient {

    @PostMapping("/insert")
    AreaResponse insert(@Valid @RequestBody InsertAreaRequest info);

    @PostMapping("/insert/bulk")
    List<AreaResponse> insertBulk(@Valid @RequestBody InsertAreaBulkRequest info);

    @PostMapping("/insert/json")
    List<AreaResponse> insertJson(@Valid @RequestBody InsertAreaJsonRequest info);

    @PostMapping(value = "/insert/svg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    List<AreaResponse> insertSvg(
            @RequestPart("eventId") String eventId,
            @RequestPart("svgFile") MultipartFile svgFile,
            @RequestParam(value = "force", defaultValue = "false") boolean force
    );

    @GetMapping("/layout/event/{eventId}")
    EventLayoutResponse selectLayout(@PathVariable("eventId") Long eventId);

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
