package org.monitoring.openmission.monitoring;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.monitoring.openmission.domain.item.dto.request.ItemCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class CreateItemScenarioTest {

    private static final Logger log = LoggerFactory.getLogger(CreateItemScenarioTest.class);
    TestRestTemplate restTemplate = new TestRestTemplate();
    private static final String BASE_URL = "http://localhost:8080";

    private <T> void post(String path, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); // ← 이게 바로 그 setContentType

        HttpEntity<T> entity = new HttpEntity<>(body, headers);
        try {
            restTemplate.postForEntity(BASE_URL + path, entity, Void.class);
        } catch (Exception ignored) {
            // 에러 시나리오용이면 여기서 예외 무시해도 됨
        }
    }

    @Test
    void item_create_rps_scenario() {

        int createFailedCount = 0;
        for (int i = 0; i < 100; i++) {

            int itemNumber = ThreadLocalRandom.current().nextInt(-1, 30);

            if(itemNumber < 0) createFailedCount++;

            ItemCreateRequest req = new ItemCreateRequest(
                    "모니터링-상품-" + i,
                    10_000,
                    itemNumber
            );
            post("/api/items", req);
        }
        log.info("Created {} items failed", createFailedCount);
    }


    @Test
    void slow_item_trace_scenario() {
        for (int i = 0; i < 10; i++) {
            post("/monitoring/slow-item", null);
        }
    }
}
