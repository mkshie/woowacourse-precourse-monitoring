# Monitoring Guide

이 문서는 Open Mission의 정상→장애→복구 실험을 같은 조건으로 재현하고, 메트릭·경보·로그·트레이스의 연결을 확인하는 절차입니다.

## 1. 사전 준비

- Docker와 Docker Compose
- JDK 17
- `curl`, `jq`
- 로컬 포트 `3000`, `3100`, `4317`, `4318`, `5433`, `9090`, `9093`, `12345`, `16686`, `18080`

Compose로 실행하는 관측 스택과 DB의 host 포트는 로컬 실험 범위를 벗어나 노출되지 않도록 `127.0.0.1`에만 바인딩합니다.
`3000` 포트를 이미 사용 중이면 `GRAFANA_HOST_PORT=3001 docker compose up -d`처럼 Grafana host 포트만 바꿀 수 있습니다.

## 2. 모니터링 스택 실행

프로젝트 루트에서 다음 명령을 실행합니다.

```bash
docker compose up -d
```

| 구성 요소 | 주소 | 역할 |
|---|---|---|
| Grafana | <http://localhost:3000> | 메트릭·로그·트레이스 통합 탐색 |
| Loki | <http://localhost:3100> | 구조화 로그 저장·검색 |
| Prometheus | <http://localhost:9090> | 메트릭 수집·경보 규칙 평가 |
| Alertmanager | <http://localhost:9093> | Prometheus가 보낸 `Firing` 경보의 로컬 유입 확인 |
| Alloy | <http://localhost:12345> | JSON 로그 수집·가공·전송 |
| Jaeger | <http://localhost:16686> | 트레이스 저장·조회 |
| PostgreSQL | `localhost:5433` | 애플리케이션 데이터 저장 |

준비 상태를 확인합니다.

```bash
curl -fsS http://localhost:9090/-/ready
curl -fsS http://localhost:9093/-/ready
curl -fsS http://localhost:3100/ready
curl -fsS http://localhost:12345/-/ready
```

## 3. 애플리케이션 실행

장애 주입 API는 `monitoring-demo` 프로필에서만 활성화됩니다. 이 프로필은 포트 `18080`과 JSON 파일 로그 `logs/open-mission.json`을 사용합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=monitoring-demo'
```

```bash
curl -fsS http://localhost:18080/actuator/health
```

Prometheus의 <http://localhost:9090/targets>에서 `spring-boot` target이 `UP`인지 확인합니다. 컨테이너에서 호스트 애플리케이션으로 접근하므로 target 주소는 `host.docker.internal:18080`입니다.

## 4. 장애 주입 API 단독 확인

```bash
curl -i -X POST 'http://localhost:18080/monitoring/incident?mode=NORMAL'
curl -i -X POST 'http://localhost:18080/monitoring/incident?mode=ERROR'
time curl -i -X POST 'http://localhost:18080/monitoring/incident?mode=SLOW'
```

기대 결과는 각각 204, 500, 약 1.5초 뒤 204입니다.

## 5. 정상→장애→복구 시나리오

```bash
observability/scripts/verify-incident-drill.sh
```

이 스크립트가 k6를 실행하고 관측 API를 함께 조회합니다. 어느 조건이라도 충족하지 못하면 non-zero로 종료합니다.

`incident-drill.js`는 총 2분 30초 동안 다음 요청률을 유지합니다.

| 구간 | NORMAL | ERROR | SLOW | 목적 |
|---|---:|---:|---:|---|
| 0~30초 | 5 RPS | 0 | 0 | 정상 기준선 |
| 30~90초 | 5 RPS | 1 RPS | 1 RPS | 5xx와 지연 동시 주입 |
| 90~150초 | 5 RPS | 0 | 0 | 지표 정상화와 경보 해제 |

장애 구간의 이론상 5xx 비율은 `1 / 7`, 약 14.3%입니다. 요청 수가 많았다는 주장이 아니라, 10% 경보 임계치를 반복해서 넘기기 위한 통제된 비율입니다.

k6는 요청 자체에 대해 다음을 검증합니다.

- `NORMAL=204`, `ERROR=500`, `SLOW=204` 상태 코드 check가 모두 통과하는지
- 의도된 500을 포함한 전체 HTTP 실패율이 20% 미만인지
- SLOW 시나리오 p95가 1초보다 크고 2.5초보다 작은지

검증 스크립트는 여기에 관측 체인 판정을 더합니다.

- 장애 구간에 Prometheus의 5xx·p95 두 규칙이 모두 `Firing`인지
- 같은 두 경보가 Alertmanager의 local route에 active로 유입됐는지
- 이번 실행 이후의 Loki ERROR·SLOW 로그에서 각각 `traceId`를 찾을 수 있는지
- Jaeger의 같은 trace에 HTTP root, `monitoring.incident.*`, `monitoring.demo.downstream-call` span이 있는지
- SLOW downstream child span이 1.0~2.5초 범위인지
- 복구 후 Prometheus의 `Pending`·`Firing`과 Alertmanager active 경보가 모두 0건인지

k6만 다시 실행하려면 다음 명령을 사용할 수 있습니다.

```bash
docker compose --profile drill run --rm k6
```

## 6. 메트릭과 경보 확인

Grafana에 `admin / admin`으로 접속한 뒤 `Open Mission / Incident Drill` 대시보드를 엽니다.

관찰 순서는 다음과 같습니다.

1. `Incident traffic by mode (stacked)`에서 normal·error·slow 요청률을 확인합니다.
2. `HTTP 5xx Ratio`가 장애 구간에 10%를 넘는지 확인합니다.
3. `Incident p95`가 1초를 넘는지 확인합니다.
4. `Firing Alerts`에서 두 경보가 발생한 뒤 복구 구간에 사라지는지 확인합니다.

경보 규칙은 로컬 실험용으로 다음과 같이 설정했습니다.

| 경보 | 조건 | 지속 조건 |
|---|---|---|
| `MonitoringDemoHigh5xxRate` | 최근 30초 5xx 비율 > 10% | 15초 |
| `MonitoringDemoHighP95Latency` | 최근 30초 커스텀 timer p95 > 1초 | 15초 |

Prometheus API로도 확인할 수 있습니다.

```bash
curl -G -fsS http://localhost:9090/api/v1/query \
  --data-urlencode 'query=ALERTS{alertname=~"MonitoringDemo.*"}'

curl -fsS http://localhost:9093/api/v2/alerts
```

`Pending`·`Firing`·`Inactive` 상태는 Prometheus가 평가합니다. Grafana의 `Firing Alerts` 패널도 Alertmanager가 아니라 Prometheus의 `ALERTS`를 조회합니다. Alertmanager에서는 Prometheus가 보낸 `Firing` 경보의 local route 유입만 확인했으며 외부 receiver는 연결하지 않았습니다.

`for: 15s` 동안 Prometheus에서 `Pending`을 거친 뒤 `Firing`이 되고, 그 시점부터 Alertmanager에 전달됩니다. 복구 후 Prometheus에 `Pending`·`Firing`이 없고 Alertmanager active 목록도 비면 이번 실험의 해제 조건을 충족한 것입니다.

## 7. 로그에서 트레이스로 이동

애플리케이션은 Spring Boot의 Logstash JSON 형식으로 다음 필드를 기록합니다.

- `event`, `endpoint`, `mode`
- 오류의 `errorCode` 또는 지연의 `delayMs`
- MDC의 `traceId`, `spanId`
- `service`, `environment`

Alloy는 `service_name`, `environment`, `level`처럼 값의 종류가 제한된 필드만 Loki label로 사용합니다. 요청마다 달라지는 `traceId`와 `spanId`는 stream 수가 폭증하지 않도록 structured metadata로 전달합니다.

Grafana의 로그 패널 또는 Explore에서 다음 LogQL을 실행합니다.

```logql
{service_name="open-mission"} | event="monitoring.incident"
```

Incident Drill 대시보드의 로그 패널은 조사 대상이 정상 로그에 묻히지 않도록 여기에 `| mode=~"error|slow"` 조건을 더해 오류·지연 로그만 보여줍니다.

ERROR와 SLOW는 하나의 원인처럼 섞지 않고 각각 확인합니다.

| 흐름 | Loki에서 확인할 값 | Jaeger에서 확인할 span |
|---|---|---|
| ERROR | `mode=error`, `errorCode`, `traceId` | HTTP root → `monitoring.incident.error` → 오류가 기록된 `monitoring.demo.downstream-call` |
| SLOW | `mode=slow`, `delayMs=1500`, `traceId` | HTTP root → `monitoring.incident.slow` → 약 1.5초가 걸린 `monitoring.demo.downstream-call` |

로그의 `TraceID` 링크를 누르면 provisioned Jaeger 데이터 소스의 같은 trace로 이동합니다. Jaeger에는 같은 traceId의 Loki 로그를 역조회하는 trace-to-logs도 설정했습니다.

![SLOW 요청의 3개 span과 약 1.5초 downstream 구간](./images/observability/06-jaeger-slow-trace-public.png)

이번 실험에서 원인을 새로 발견했다고 주장하지 않습니다. 이미 알고 주입한 오류와 지연이 메트릭·로그·trace에서 끊기지 않고 이어지는지를 검증했습니다.

## 8. 설정 정적 검증

```bash
./gradlew test
docker compose config --quiet
bash -n observability/scripts/verify-incident-drill.sh
```

```bash
docker run --rm --entrypoint promtool \
  -v "$PWD/observability/prometheus:/etc/prometheus:ro" \
  prom/prometheus:v3.13.1 \
  check config /etc/prometheus/prometheus.yml

docker run --rm --entrypoint promtool \
  -v "$PWD/observability/prometheus:/etc/prometheus:ro" \
  prom/prometheus:v3.13.1 \
  check rules /etc/prometheus/alert-rules.yml

docker run --rm --entrypoint amtool \
  -v "$PWD/observability/alertmanager:/etc/alertmanager:ro" \
  prom/alertmanager:v0.33.1 \
  check-config /etc/alertmanager/alertmanager.yml
```

```bash
docker run --rm \
  -v "$PWD/observability/loki:/etc/loki:ro" \
  grafana/loki:3.7.0 \
  -config.file=/etc/loki/loki.yml \
  -verify-config=true

docker compose run --rm alloy validate /etc/alloy/config.alloy
```

## 9. 도구 선택 근거와 대안

각 도구는 “유명해서”가 아니라 이번 실험에서 맡길 책임을 기준으로 선택했습니다.

| 핵심 판단 | 구현 | 선택 근거 |
|---|---|---|
| 경보 평가와 전달을 구분 | Micrometer·Prometheus + Alertmanager | Spring Actuator 지표와 PromQL rule을 한 시계열 모델에서 평가하고, Alertmanager에서는 `Firing` 경보가 local route에 들어오는 경계까지만 확인했습니다. 외부 전달을 구성하지 않았으므로 그 이상을 성과로 쓰지 않았습니다. |
| 요청 식별자의 카디널리티 제어 | Alloy·Loki + OpenTelemetry·Jaeger + Grafana data link | 값의 종류가 제한된 필드만 Loki label로 두고 요청마다 바뀌는 `traceId`·`spanId`는 structured metadata로 보존했습니다. 로그 검색성과 stream 폭증 방지를 함께 고려하면서 같은 trace로 이동할 수 있습니다. |
| 느린 응답에도 흔들리지 않는 재현과 판정 | k6 `constant-arrival-rate` + provisioning + 검증 스크립트 | 응답 완료 속도와 무관하게 요청률을 유지하고, 설정·대시보드를 자동 등록했습니다. JUnit은 세 분기를 빠르게 확인하고, 별도 스크립트가 경보·로그·trace·복구의 통합 흐름을 판정하도록 책임을 나눴습니다. |

직접 검증하지 않은 저장소나 수집기 대안을 나열하는 대신, 이번 코드와 실행 결과로 설명할 수 있는 세 판단만 남겼습니다.

참고한 공식 문서:

- [Spring Boot structured logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Grafana Alloy file log source](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.source.file/)
- [Loki structured metadata](https://grafana.com/docs/loki/latest/get-started/labels/structured-metadata/)
- [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [k6 scenarios](https://grafana.com/docs/k6/latest/using-k6/scenarios/)
- [Prometheus alerting rules](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/)
- [Jaeger v2 all-in-one](https://www.jaegertracing.io/docs/2.20/getting-started/)

## 10. 로컬 실험에서 운영으로 확장할 때

현재 구성은 관측 흐름을 증명하기 위한 로컬 환경입니다.

- 실제 외부 시스템이 아니라 `simulated-inventory-service` child span 안에서 예외와 `Thread.sleep`으로 오류·지연을 통제 주입
- Alertmanager 외부 receiver와 실제 알림 전달 실패 대응
- 운영 트래픽을 기준으로 한 SLI/SLO와 burn-rate alert
- 로그·메트릭·트레이스 retention 및 저장 비용
- 인증, TLS, secret 관리, 접근 권한
- Jaeger 영속 저장소와 collector 확장
- 개인정보·민감정보의 로그 마스킹

이 항목들은 실제 운영 요구와 규모가 정해진 뒤 설계해야 하며, 현재 실험 결과만으로 운영 적합성을 주장하지 않습니다.
