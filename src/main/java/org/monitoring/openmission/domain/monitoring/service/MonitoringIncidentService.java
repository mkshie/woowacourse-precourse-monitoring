package org.monitoring.openmission.domain.monitoring.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.monitoring.openmission.domain.monitoring.model.IncidentMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("monitoring-demo")
@Slf4j
public class MonitoringIncidentService {

    private static final String ENDPOINT = "/monitoring/incident";
    private static final String DEMO_ERROR_CODE = "MONITORING_DEMO_DOWNSTREAM_ERROR";

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final long slowDelayMillis;

    public MonitoringIncidentService(
            Tracer tracer,
            MeterRegistry meterRegistry,
            @Value("${monitoring.demo.slow-delay-ms:1500}") long slowDelayMillis
    ) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.slowDelayMillis = slowDelayMillis;
    }

    public void run(IncidentMode mode) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        String outcome = "success";
        String modeTag = mode.name().toLowerCase(Locale.ROOT);

        Span incidentSpan = tracer.nextSpan()
                .name("monitoring.incident." + modeTag)
                .tag("incident.mode", modeTag)
                .tag("experiment.type", "controlled")
                .start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(incidentSpan)) {
            switch (mode) {
                case NORMAL -> logCompletion(modeTag);
                case ERROR -> injectError();
                case SLOW -> injectLatency(modeTag);
            }
        } catch (RuntimeException exception) {
            outcome = "error";
            incidentSpan.error(exception);
            throw exception;
        } finally {
            incidentSpan.end();
            recordMetrics(modeTag, outcome, timerSample);
        }
    }

    private void injectLatency(String modeTag) {
        Span slowSpan = tracer.nextSpan()
                .name("monitoring.demo.downstream-call")
                .tag("component", "simulated-inventory-service")
                .tag("delay.type", "controlled")
                .start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(slowSpan)) {
            sleep(slowDelayMillis);
            log.atWarn()
                    .addKeyValue("event", "monitoring.incident")
                    .addKeyValue("endpoint", ENDPOINT)
                    .addKeyValue("mode", modeTag)
                    .addKeyValue("delayMs", slowDelayMillis)
                    .log("Injected monitoring demo latency");
        } catch (RuntimeException exception) {
            slowSpan.error(exception);
            throw exception;
        } finally {
            slowSpan.end();
        }
    }

    private void injectError() {
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Controlled monitoring demo failure"
        );

        Span errorSpan = tracer.nextSpan()
                .name("monitoring.demo.downstream-call")
                .tag("component", "simulated-inventory-service")
                .tag("error.code", DEMO_ERROR_CODE)
                .start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(errorSpan)) {
            errorSpan.error(exception);
            log.atError()
                    .setCause(exception)
                    .addKeyValue("event", "monitoring.incident")
                    .addKeyValue("endpoint", ENDPOINT)
                    .addKeyValue("mode", "error")
                    .addKeyValue("errorCode", DEMO_ERROR_CODE)
                    .log("Injected monitoring demo error");
            throw exception;
        } finally {
            errorSpan.end();
        }
    }

    private void logCompletion(String modeTag) {
        log.atInfo()
                .addKeyValue("event", "monitoring.incident")
                .addKeyValue("endpoint", ENDPOINT)
                .addKeyValue("mode", modeTag)
                .log("Completed monitoring demo request");
    }

    private void recordMetrics(String mode, String outcome, Timer.Sample timerSample) {
        Counter.builder("monitoring.incident.requests")
                .description("Number of controlled monitoring incident requests")
                .tag("mode", mode)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();

        timerSample.stop(Timer.builder("monitoring.incident.duration")
                .description("Duration of controlled monitoring incident requests")
                .tag("mode", mode)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Monitoring demo delay was interrupted", exception);
        }
    }
}
