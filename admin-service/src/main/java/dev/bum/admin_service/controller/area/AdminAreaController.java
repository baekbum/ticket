package dev.bum.admin_service.controller.area;

import dev.bum.admin_service.feign.area.AreaServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaJsonRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/area")
@RequiredArgsConstructor
public class AdminAreaController {

    private final AreaServiceClient areaServiceClient;

    @PostMapping("/insert")
    public ResponseEntity<AreaResponse> insert(@Valid @RequestBody InsertAreaRequest info) {
        return ResponseEntity.ok(areaServiceClient.insert(info));
    }

    @PostMapping("/insert/bulk")
    public ResponseEntity<List<AreaResponse>> insertBulk(@Valid @RequestBody InsertAreaBulkRequest info) {
        return ResponseEntity.ok(areaServiceClient.insertBulk(info));
    }

    @PostMapping("/insert/json")
    public ResponseEntity<List<AreaResponse>> insertJson(@Valid @RequestBody InsertAreaJsonRequest info) {
        return ResponseEntity.ok(areaServiceClient.insertJson(info));
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
