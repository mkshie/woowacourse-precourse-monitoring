# 📊 Open Mission — Spring Boot + Monitoring

## 노션 정리 url
https://www.notion.so/2b5f0e538e0b80cd8549cf92dc0b7990

## 🧭 프로젝트 개요
> “보이지 않던 시스템의 흐름을 **보이게** 만들어보자.”

이번 우테코 **오픈 미션**의 목표는 “나에게 낯선 도전”을 주제로 내가 직접 해보지 않았던 기술들을 사용하여
기간내에 프로젝트를 보여주는것 따라서 평소 운영까지 프로젝트를 가지 않아서 모니터링 작업을 경험해본적이 없기 때문에 좋은 경험이라 생각하고
**운영 관점에서 시스템을 관찰·분석할 수 있는 백엔드 모니터링 환경**을 다양한 모니터링 시스템을 활용하여 직접 구축하는 것으로 정했습니다.

단순히 동작하는 API를 만드는 것에서 그치지 않고,  
**트레이스(Trace)**·**메트릭(Metrics)**·**로그(Log)** 가 서로 연결되는 구조를 직접 설계하고 구현하는게 목표입니다.

---

## 🎯 목표

| 구분 | 구체적 목표 | 측정 기준 |
|------|--------------|------------|
| 1 | OpenTelemetry 기반 분산 추적(Trace) 도입 | Jaeger UI에서 Controller → DB까지 스팬 확인 |
| 2 | Prometheus + Grafana로 메트릭 수집 및 시각화 | p50/p95, RPS, 에러율 실시간 변화 확인 |
| 3 | 로그와 트레이스를 traceId로 연계 | 로그에서 traceId 검색 → Jaeger 트레이스로 이동 가능 |
| 4 | DB, 캐시, 외부호출 등 다양한 시나리오 계측 | 캐시 미스→히트, 에러 재현, 지연 트랜잭션 |
| 5 | “작게 동작하는” 완전한 환경 구축 | `docker compose up` + `./gradlew bootRun`으로 재현 가능 |

---

## ⚙️ 기술 스택 (목표치)

| 영역             | 사용 기술                                      | 목적                                  |
| -------------- |--------------------------------------------| ----------------------------------- |
| Language       | Java 17                                    | 메인 백엔드                              |
| Framework      | Spring Boot 3.5.7                          | REST API / JPA / Cache / Validation |
| DB             | PostgreSQL (Docker)                        | 데이터 저장 및 재현성                        |
| ORM            | Spring Data JPA                            | 엔티티 기반 ORM                          |
| Migration      | Flyway                                     | DB 스키마 버전관리                         |
| Monitoring     | OpenTelemetry / Jaeger / Prometheus / Grafana | 추적·지표·시각화                           |
| Logging        | SLF4J + MDC(traceId)                       | 로그-트레이스 상관관계                        |
| Build & Deploy | Gradle, Docker Compose                     | 실행 자동화                              |


## 구현할 기능 목록

Item 컨트롤러
-  Item 
  - 컨트롤러
    - 생성
    - 조회
    - 수정
  - 서비스
  - DTO
    - CreateRequest
    - CreateResponse (공통)
    - ItemResponse
-  Order
  - 컨트롤러
    - 생성
    - 조회
  - 서비스
  - DTO
    - CreateRequest
    - CreateResponse (공통)
    - OrderResponse
   
  ## 🚀 빠른 실행 방법 (Quick Start)

자세한 내용은 [`docs/monitoring-guide.md`](./docs/monitoring-guide.md)를 참고하세요.  
여기서는 “최소 단계”만 정리합니다.

### 1) 사전 준비

- Docker, Docker Compose 설치
- JDK 17, Gradle(Wrapper 사용 가능)

### 2) 모니터링 스택(Docker) 기동

프로젝트 루트에서:

```bash
docker compose up -d
```
이 명령으로 Prometheus / Grafana / Jaeger / (PostgreSQL 등) 모니터링 관련 컨테이너가 기동됩니다.

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000
- Jaeger: http://localhost:16686

> 🔎 Prometheus Status > Targets 메뉴에서 Spring Boot 애플리케이션이 UP 상태인지 꼭 확인합니다.

### 3) Spring Boot 애플리케이션 실행

```bash
./gradlew bootRun or IDE 에서 직접 실행
```

기본 포트: http://localhost:8080

OpenAPI/Swagger: http://localhost:8080/swagger-ui/index.html

Actuator Prometheus endpoint: http://localhost:8080/actuator/prometheus

### 4) 모니터링 확인

- Grafana 접속 → Prometheus 데이터 소스 사용 → 대시보드 선택  
- Jaeger 접속 → Service에 애플리케이션 이름 선택 → 트레이스 조회

---

## 🧪 모니터링 시나리오 테스트

`org.monitoring.openmission.monitoring` 패키지에  
**운영 시나리오를 재현하기 위한 전용 테스트 코드**를 분리해두었습니다.

### CreateItemScenarioTest

- `item_create_rps_scenario()`  
  → Item 생성 100건 + 실패 섞인 시나리오
- `slow_item_trace_scenario()`  
  → `/monitoring/slow-item`을 여러 번 호출해 Jaeger span 확인

### MonitoringScenarioTest

- `item_create_rps_scenario()`  
  → Item 생성 RPS 확인용
- `order_create_slow_scenario()`  
  → 주문 생성 시 느린 구간이 p95 / Jaeger에 어떻게 보이는지 실험
- `error_rate_scenario()`  
  → 일부러 에러를 섞어 HTTP 에러율 패턴 확인

> ⚠️ 이 테스트들은 **이미 8080 포트에 떠 있는 서버**를 대상으로  
> `TestRestTemplate`로 요청을 보내는 형태입니다.  
> 실행 전 반드시 `./gradlew bootRun`으로 애플리케이션을 먼저 띄워두어야 합니다.

자세한 실행 단계와,  
각 시나리오에서 **어떤 패널/트레이스를 보면 좋은지**는  
[`docs/monitoring-guide.md`](./docs/monitoring-guide.md)에 정리했습니다.

