package org.monitoring.openmission.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.monitoring.openmission.domain.monitoring.controller.MonitoringController;
import org.monitoring.openmission.domain.monitoring.service.MonitoringIncidentService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class MonitoringProfileTest {

    @Test
    void incidentComponentsAreDisabledWithoutMonitoringDemoProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(MonitoringController.class, MonitoringIncidentService.class);
            context.refresh();

            assertThat(context.getBeansOfType(MonitoringController.class)).isEmpty();
            assertThat(context.getBeansOfType(MonitoringIncidentService.class)).isEmpty();
        }
    }
}
