# 📊 Open Mission — Spring Boot + Monitoring

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
    - 삭제
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
    - 삭제
    - 수정
  - 서비스
  - DTO
    - CreateRequest
    - CreateResponse (공통)
    - OrderResponse