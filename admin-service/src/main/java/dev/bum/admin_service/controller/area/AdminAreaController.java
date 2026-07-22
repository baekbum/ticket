package dev.bum.admin_service.controller.area;

import dev.bum.admin_service.feign.area.AreaServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/area")
@RequiredArgsConstructor
public class AdminAreaController {

    private final AreaServiceClient areaServiceClient;

    @PostMapping(value = "/insert/svg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> insertSvg(
            @RequestPart("eventId") String eventId,
            @RequestPart("svgFile") MultipartFile svgFile,
            @RequestParam(value = "force", defaultValue = "false") boolean force
    ) {
        try {
            return ResponseEntity.ok(areaServiceClient.insertSvg(eventId, svgFile, force));
        } catch (FeignException.Conflict e) {
            return ResponseEntity.status(e.status()).body(e.contentUTF8());
        }
    }

    @GetMapping("/layout/event/{eventId}")
    public ResponseEntity<EventLayoutResponse> selectLayout(@PathVariable("eventId") Long eventId) {
        EventLayoutResponse response = areaServiceClient.selectLayout(eventId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    @GetMapping("/select/id/{areaId}")
    public ResponseEntity<AreaResponse> selectById(@PathVariable("areaId") Long areaId) {
        return ResponseEntity.ok(areaServiceClient.selectById(areaId));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<AreaResponse>> selectByCond(@RequestBody AreaCondRequest cond) {
        return ResponseEntity.ok(areaServiceClient.selectByCond(cond));
    }

    @PutMapping("/update/id/{areaId}")
    public ResponseEntity<AreaResponse> update(@PathVariable("areaId") Long areaId, @Valid @RequestBody UpdateAreaRequest info) {
        return ResponseEntity.ok(areaServiceClient.update(areaId, info));
    }

    @DeleteMapping("/delete/id/{areaId}")
    public ResponseEntity<AreaResponse> delete(@PathVariable("areaId") Long areaId) {
        return ResponseEntity.ok(areaServiceClient.delete(areaId));
    }

    @DeleteMapping("/delete/bulk")
    public ResponseEntity<Void> deleteBulk(@Valid @RequestBody DeleteAreaBulkRequest info) {
        areaServiceClient.deleteBulk(info);
        return ResponseEntity.ok().build();
    }
}
