package org.monitoring.openmission.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monitoring.openmission.domain.monitoring.controller.MonitoringController;
import org.monitoring.openmission.domain.monitoring.service.MonitoringIncidentService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MonitoringControllerTest {

    private static final long SLOW_DELAY_MILLIS = 1_500L;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class, RETURNS_SELF);
        when(tracer.nextSpan()).thenReturn(span);
        when(tracer.withSpan(any(Span.class))).thenReturn(mock(Tracer.SpanInScope.class));

        MonitoringIncidentService monitoringIncidentService = new MonitoringIncidentService(
                tracer,
                new SimpleMeterRegistry(),
                SLOW_DELAY_MILLIS
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MonitoringController(monitoringIncidentService))
                .build();
    }

    @Test
    void normalModeReturnsNoContent() throws Exception {
        mockMvc.perform(post("/monitoring/incident").param("mode", "NORMAL"))
                .andExpect(status().isNoContent());
    }

    @Test
    void errorModeReturnsInternalServerError() throws Exception {
        mockMvc.perform(post("/monitoring/incident").param("mode", "ERROR"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void slowModeWaitsForConfiguredDelayAndReturnsNoContent() throws Exception {
        long startedAt = System.nanoTime();

        mockMvc.perform(post("/monitoring/incident").param("mode", "SLOW"))
                .andExpect(status().isNoContent());

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertThat(elapsedMillis).isBetween(1_400L, 3_000L);
    }
}
