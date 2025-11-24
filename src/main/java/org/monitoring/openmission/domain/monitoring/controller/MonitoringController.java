package org.monitoring.openmission.domain.monitoring.controller;


import lombok.RequiredArgsConstructor;
import org.monitoring.openmission.domain.monitoring.service.MonitoringItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringItemService monitoringItemService;

    @PostMapping("/slow-item")
    public ResponseEntity<Void> createSlowItem() {
        monitoringItemService.createSlowItem();
        return ResponseEntity.ok().build();
    }
}
