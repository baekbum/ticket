package dev.bum.ticket_service.controller;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.ticket_service.service.area.AreaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
