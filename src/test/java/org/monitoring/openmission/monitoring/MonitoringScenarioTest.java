package org.monitoring.openmission.monitoring;


import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.monitoring.openmission.domain.item.dto.request.ItemCreateRequest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class MonitoringScenarioTest {

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


    private void get(String pathWithQuery) {
        try {
            restTemplate.getForEntity(BASE_URL + pathWithQuery, Void.class);
        } catch (Exception ignored) {
        }
    }


    @Test
    void item_create_rps_scenario() {
        for (int i = 0; i < 100; i++) {
            ItemCreateRequest req = new ItemCreateRequest(
                    "모니터링-상품-" + i,
                    10_000,
                    10
            );
            post("/api/items", req);
        }
    }

    @Test
    void order_create_slow_scenario() {
        for (int i = 0; i < 30; i++) {

            int itemNumber = ThreadLocalRandom.current().nextInt(1, 5);
            String itemId = Integer.toString(itemNumber);
            String requestParam = "quantity=" + i;
            post("/api/orders/" + itemId + "?" + requestParam, null);
        }
    }

    @Test
    void error_rate_scenario() {
        for (int i = 0; i < 10; i++) {
            get("/api/orders/999999"); // 없는 ID
        }
        for (int i = 0; i < 10; i++) {
            get("/api/items?page=0&size=20");
        }
    }
}
