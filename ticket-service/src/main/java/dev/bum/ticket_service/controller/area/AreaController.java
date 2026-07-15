package dev.bum.ticket_service.controller.area;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaJsonRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.ticket_service.service.area.AreaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RequestMapping("/api/v1/area")
@RestController
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    @PostMapping("/insert")
    public ResponseEntity<AreaResponse> insert(@Valid @RequestBody InsertAreaRequest info) {
        return ResponseEntity.ok(areaService.insert(info));
    }

    @PostMapping("/insert/bulk")
    public ResponseEntity<List<AreaResponse>> insertBulk(@Valid @RequestBody InsertAreaBulkRequest info) {
        return ResponseEntity.ok(areaService.insertBulk(info));
    }

    @PostMapping("/insert/json")
    public ResponseEntity<List<AreaResponse>> insertJson(@Valid @RequestBody InsertAreaJsonRequest info) {
        return ResponseEntity.ok(areaService.insertJson(info));
    }

    @PostMapping(value = "/insert/svg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AreaResponse>> insertSvg(
            @RequestPart("eventId") String eventId,
            @RequestPart("svgFile") MultipartFile svgFile,
            @RequestParam(value = "force", defaultValue = "false") boolean force
    ) {
        return ResponseEntity.ok(areaService.insertSvg(Long.parseLong(eventId), svgFile, force));
    }

    @GetMapping("/layout/event/{eventId}")
    public ResponseEntity<EventLayoutResponse> selectLayout(@PathVariable("eventId") Long eventId) {
        EventLayoutResponse response = areaService.selectLayout(eventId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    @GetMapping("/select/id/{areaId}")
    public ResponseEntity<AreaResponse> selectById(@PathVariable("areaId") Long areaId) {
        return ResponseEntity.ok(areaService.selectById(areaId));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<AreaResponse>> selectByCond(@RequestBody AreaCondRequest cond) {
        return ResponseEntity.ok(areaService.selectByCond(cond));
    }

    @PutMapping("/update/id/{areaId}")
    public ResponseEntity<AreaResponse> update(@PathVariable("areaId") Long areaId, @Valid @RequestBody UpdateAreaRequest info) {
        return ResponseEntity.ok(areaService.update(areaId, info));
    }

    @DeleteMapping("/delete/id/{areaId}")
    public ResponseEntity<AreaResponse> delete(@PathVariable("areaId") Long areaId) {
        return ResponseEntity.ok(areaService.delete(areaId));
    }

    @DeleteMapping("/delete/bulk")
    public ResponseEntity<Void> deleteBulk(@Valid @RequestBody DeleteAreaBulkRequest info) {
        areaService.deleteBulk(info);
        return ResponseEntity.ok().build();
    }
}
