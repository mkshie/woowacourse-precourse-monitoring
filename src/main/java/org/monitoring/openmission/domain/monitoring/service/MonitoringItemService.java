package org.monitoring.openmission.domain.monitoring.service;


import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MonitoringItemService {

    private final Tracer tracer;


    public void createSlowItem() {
        // 1) 빠른 구간
        Span validationSpan = tracer.nextSpan().name("item.validation");
        try (Tracer.SpanInScope ws = tracer.withSpan(validationSpan.start())) {
            fakeValidation();
        } finally {
            validationSpan.end();
        }

        // 2) 느린 구간
        Span slowSpan = tracer.nextSpan().name("item.slow-part");
        try (Tracer.SpanInScope ws = tracer.withSpan(slowSpan.start())) {
            slowBusinessLogic();
        } finally {
            slowSpan.end();
        }
    }

    private void fakeValidation() {
        // 검증 로직 가정
        Span current = tracer.currentSpan();
        if (current != null) {
            current.tag("phase", "validation");
        }
        //짧게 시간 지연 걸어주기
        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void slowBusinessLogic() {
        Span current = tracer.currentSpan();
        if (current != null) {
            current.tag("phase", "slow-part");
            current.tag("reason", "sleep-test");
        }
        try {
            Thread.sleep(1500L);
            // 실제라면: 무거운 DB 조회, 외부 API 호출 등
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
