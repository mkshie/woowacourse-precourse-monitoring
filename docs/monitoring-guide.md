# 🧪 Monitoring Guide – 실행 & 시나리오 실험 방법

이 문서는 Open Mission 프로젝트에서 구축한  
**Prometheus · Grafana · Jaeger 기반 모니터링 환경을 재현하고,
준비해 둔 시나리오 테스트를 통해 그래프/트레이스를 관찰하는 방법** 을 정리한 가이드입니다.

---

## 1. 요구 사항

- Docker, Docker Compose
- JDK 17

---

## 2. 모니터링 스택 기동 (Docker)

### 2.1 Docker Compose 실행

프로젝트 루트에서:

```bash
docker compose up -d
```
이 명령으로 다음과 같은 컨테이너들이 기동됩니다(구성에 따라 다를 수 있음).

- Prometheus : http://localhost:9090
- Grafana : http://localhost:3000
- Jaeger : http://localhost:16686
- PostgreSQL

- ### 2.2 Prometheus Target 확인

브라우저에서 <http://localhost:9090/targets> 접속 후:

- Spring Boot 애플리케이션이 등록된 Job이 `UP` 상태인지 확인합니다.
- 상태가 `DOWN` 이라면:
  - Spring Boot 애플리케이션이 실제로 띄워져 있는지
  - `prometheus.yml` 의 `targets:` 설정이 올바른지 확인해야 합니다.

---

## 3. Spring Boot 애플리케이션 실행

### 3.1 애플리케이션 기동

프로젝트 루트에서:

```bash
./gradlew bootRun or 직접 실행
```
정상적으로 기동되면 `http://localhost:8080` 에서 API를 사용할 수 있습니다.

### Actuator Prometheus endpoint

- `http://localhost:8080/actuator/prometheus`

### Swagger 사용

- `http://localhost:8080/swagger-ui/index.html`

`/actuator/prometheus`를 열었을 때,

- `http_server_requests_seconds_*`
- `jvm_memory_*`
- `item_create_*`

등의 메트릭이 노출되는지 확인합니다.

---

## 4. Grafana / Jaeger 접속

### 4.1 Grafana

- URL: `http://localhost:3000`
- 기본 계정은 도커 이미지 설정에 따르며, 보통 `admin / admin` (처음 로그인 시 비밀번호 변경 요구)입니다.
- **Data Sources** 메뉴에서 Prometheus가 `http://prometheus:9090` 로 등록되어 있어야 합니다.
- 대시보드를 Import하거나(resource 파일안에 json 형태로 저장해뒀습니다. open-mission-dashboard.json), 미리 만들어 둔 기술 + 도메인 메트릭 대시보드를 선택하여 사용합니다.

관찰할 대표 패널:

- HTTP Requests per Minute  
- HTTP 5xx Error Rate  
- HTTP p95 Latency  
- Process CPU Usage  
- JVM Heap Usage  
- 최근 5분 Item 생성 시도 수 / 성공·실패 / 성공률  

### 4.2 Jaeger

- URL: `http://localhost:16686`
- 좌측 상단 **Service** 드롭다운에서 Spring 애플리케이션 이름 선택  
  (예: `open-mission` 또는 `open-mission-api` 등 설정에 따라 다름)
- Operation / 태그 / 시간 범위로 필터링하여 트레이스를 조회할 수 있습니다.

---

## 5. 시나리오 테스트 실행 방법

⚠️ 아래 테스트들은 서버가 이미 **8080 포트에 떠 있는 상태**를 전제로 합니다.  
먼저 애플리케이션을 띄워두고,  
그 다음에 IDE나 테스트를 실행하세요.

### 5.1 CreateItemScenarioTest

```java
public class CreateItemScenarioTest {

    @Test
    void item_create_rps_scenario() {
        // 성공/실패가 섞인 Item 생성 요청 100건
    }

    @Test
    void slow_item_trace_scenario() {
        // /monitoring/slow-item 을 10번 호출 (Jaeger span 실험용)
    }
}
```

#### 5.1.1 실행 방법

```bash
./gradlew test --tests "org.monitoring.openmission.monitoring.CreateItemScenarioTest.item_create_rps_scenario"
./gradlew test --tests "org.monitoring.openmission.monitoring.CreateItemScenarioTest.slow_item_trace_scenario"
```
또는 IDE에서 해당 메서드를 직접 실행해도 됩니다.

#### 5.1.2 관찰 포인트

**item_create_rps_scenario**

- Grafana에서:
  - HTTP RPS / 에러율 패널이 짧은 구간 동안 상승하는지
  - Item 생성 도메인 메트릭(최근 5분 시도 수 / 성공·실패 / 성공률)이 변화하는지

**slow_item_trace_scenario**

- Jaeger에서:
  - Service 선택 → Operation을 `/monitoring/slow-item` 으로 필터
  - 트레이스 하나를 열어 아래를 확인:
    - `item.validation` span (짧은 구간)
    - `item.slow-part` span (의도적으로 1.5초 지연) 이 분리되어 보이는지

---

### 5.2 MonitoringScenarioTest

```java
public class MonitoringScenarioTest {

    @Test
    void item_create_rps_scenario() {
        // /api/items 생성 요청 100건
    }

    @Test
    void order_create_slow_scenario() {
        // /api/orders/{itemId}?quantity=... 30건 (느린 구간 실험용)
    }

    @Test
    void error_rate_scenario() {
        // 존재하지 않는 주문 ID + 정상 조회 API를 섞어서 에러율 패턴 생성
    }
}
```

#### 5.1.2 실행 방법

```bash
./gradlew test --tests "org.monitoring.openmission.monitoring.MonitoringScenarioTest.item_create_rps_scenario"
./gradlew test --tests "org.monitoring.openmission.monitoring.MonitoringScenarioTest.order_create_slow_scenario"
./gradlew test --tests "org.monitoring.openmission.monitoring.MonitoringScenarioTest.error_rate_scenario"
```
또는 IDE에서 해당 메서드를 직접 실행해도 됩니다.

#### 5.2.2 관찰 포인트

**item_create_rps_scenario**

- Grafana:
  - Item 생성 관련 패널에서 짧은 시간 동안 그래프가 “툭” 튀는지

**order_create_slow_scenario**

- Grafana:
  - HTTP p95 Latency가 평소보다 높게 나타나는 구간이 생기는지
- Jaeger:
  - `/api/orders/...` 트레이스를 열어서, 주문 처리 로직 중 어느 구간이 가장 오래 걸리는지 확인

**error_rate_scenario**

- Grafana:
  - HTTP 4xx/5xx Error Rate 패널에서 의도적으로 에러를 섞은 구간이 튀는지
  - 정상 요청(`/api/items`)을 추가로 보내면서 에러율이 점점 내려가는 모습 확인

---

### 6. 자주 헷갈릴 수 있는 포인트 (요약 체크리스트)

**서버 실행 여부**

- `./gradlew bootRun` 이 먼저인지 항상 확인

**Time Range**

- Grafana/Prometheus에서 `Last 5 minutes`, `Last 15 minutes` 등  
  시나리오 테스트 시점이 포함되도록 설정

**Prometheus Targets**

- `Status > Targets` 에서 Spring 앱이 `UP`인지 확인

**레이블 필터**

- 메트릭이 안 보이면, 우선 필터 없는 상태로 전체 모양을 확인 후  
  조건을 하나씩 추가하면서 좁혀가기

**Docker vs localhost**

- 브라우저/테스트에서 사용하는 `localhost:포트` 와  
  컨테이너 내부에서 사용하는 `서비스이름:포트` 를 헷갈리지 않기

