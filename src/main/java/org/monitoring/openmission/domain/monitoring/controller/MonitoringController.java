package org.monitoring.openmission.domain.monitoring.controller;

import lombok.RequiredArgsConstructor;
import org.monitoring.openmission.domain.monitoring.model.IncidentMode;
import org.monitoring.openmission.domain.monitoring.service.MonitoringIncidentService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
@Profile("monitoring-demo")
public class MonitoringController {

    private final MonitoringIncidentService monitoringIncidentService;

    @PostMapping("/incident")
    public ResponseEntity<Void> runIncident(@RequestParam IncidentMode mode) {
        monitoringIncidentService.run(mode);
        return ResponseEntity.noContent().build();
    }
}
