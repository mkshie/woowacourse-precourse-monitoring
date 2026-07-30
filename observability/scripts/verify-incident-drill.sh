#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

readonly PROMETHEUS_URL="http://localhost:9090"
readonly ALERTMANAGER_URL="http://localhost:9093"
readonly LOKI_URL="http://localhost:3100"
readonly JAEGER_URL="http://localhost:16686"
readonly ALLOY_URL="http://localhost:12345"
readonly APPLICATION_URL="http://localhost:18080"

readonly ALERT_QUERY='ALERTS{alertname=~"MonitoringDemoHigh(5xxRate|P95Latency)"}'
readonly ERROR_ALERT="MonitoringDemoHigh5xxRate"
readonly LATENCY_ALERT="MonitoringDemoHighP95Latency"

readonly INCIDENT_TIMEOUT_SECONDS=85
readonly RECOVERY_TIMEOUT_SECONDS=75
readonly INGEST_TIMEOUT_SECONDS=30
readonly POLL_INTERVAL_SECONDS=2

K6_PID=""
K6_LOG=""
RUN_START_NS=""

pass() {
  printf 'PASS: %s\n' "$1"
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  if [[ -n "${K6_LOG}" && -s "${K6_LOG}" ]]; then
    printf '\nLast k6 output:\n' >&2
    tail -n 40 "${K6_LOG}" >&2
  fi
  exit 1
}

cleanup() {
  if [[ -n "${K6_PID}" ]] && kill -0 "${K6_PID}" 2>/dev/null; then
    kill "${K6_PID}" 2>/dev/null || true
    wait "${K6_PID}" 2>/dev/null || true
  fi
  if [[ -n "${K6_LOG}" && -f "${K6_LOG}" ]]; then
    rm -f "${K6_LOG}"
  fi
}

trap cleanup EXIT
trap 'exit 130' INT TERM

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

get_prometheus_alerts() {
  curl -G -fsS "${PROMETHEUS_URL}/api/v1/query" \
    --data-urlencode "query=${ALERT_QUERY}"
}

get_alertmanager_alerts() {
  curl -fsS "${ALERTMANAGER_URL}/api/v2/alerts"
}

has_both_prometheus_firing_alerts() {
  jq -e --arg error_alert "${ERROR_ALERT}" --arg latency_alert "${LATENCY_ALERT}" '
    [.data.result[]?
      | select(.metric.alertstate == "firing")
      | .metric.alertname
    ] as $names
    | (($names | index($error_alert)) != null)
      and (($names | index($latency_alert)) != null)
  ' >/dev/null
}

has_both_alertmanager_alerts() {
  jq -e --arg error_alert "${ERROR_ALERT}" --arg latency_alert "${LATENCY_ALERT}" '
    [.[]?
      | select(.status.state == "active")
      | .labels.alertname
    ] as $names
    | (($names | index($error_alert)) != null)
      and (($names | index($latency_alert)) != null)
  ' >/dev/null
}

has_no_prometheus_alerts() {
  jq -e '.data.result | length == 0' >/dev/null
}

has_no_alertmanager_alerts() {
  jq -e --arg error_alert "${ERROR_ALERT}" --arg latency_alert "${LATENCY_ALERT}" '
    [.[]?
      | select(
          .labels.alertname == $error_alert
          or .labels.alertname == $latency_alert
        )
    ] | length == 0
  ' >/dev/null
}

wait_for_incident_signals() {
  local deadline
  local prometheus_seen=0
  local alertmanager_seen=0
  local prometheus_json
  local alertmanager_json

  deadline=$(( $(date +%s) + INCIDENT_TIMEOUT_SECONDS ))

  while (( $(date +%s) <= deadline )); do
    prometheus_json="$(get_prometheus_alerts 2>/dev/null || true)"
    alertmanager_json="$(get_alertmanager_alerts 2>/dev/null || true)"

    if [[ -n "${prometheus_json}" ]] \
      && has_both_prometheus_firing_alerts <<<"${prometheus_json}"; then
      prometheus_seen=1
    fi

    if [[ -n "${alertmanager_json}" ]] \
      && has_both_alertmanager_alerts <<<"${alertmanager_json}"; then
      alertmanager_seen=1
    fi

    if (( prometheus_seen == 1 && alertmanager_seen == 1 )); then
      return 0
    fi

    if [[ -n "${K6_PID}" ]] && ! kill -0 "${K6_PID}" 2>/dev/null; then
      return 1
    fi

    sleep "${POLL_INTERVAL_SECONDS}"
  done

  return 1
}

wait_for_recovery() {
  local deadline
  local prometheus_json
  local alertmanager_json

  deadline=$(( $(date +%s) + RECOVERY_TIMEOUT_SECONDS ))

  while (( $(date +%s) <= deadline )); do
    prometheus_json="$(get_prometheus_alerts 2>/dev/null || true)"
    alertmanager_json="$(get_alertmanager_alerts 2>/dev/null || true)"

    if [[ -n "${prometheus_json}" && -n "${alertmanager_json}" ]] \
      && has_no_prometheus_alerts <<<"${prometheus_json}" \
      && has_no_alertmanager_alerts <<<"${alertmanager_json}"; then
      return 0
    fi

    sleep "${POLL_INTERVAL_SECONDS}"
  done

  return 1
}

get_loki_logs() {
  local mode="$1"
  local end_ns
  local query

  end_ns="$(printf '%s000000000' "$(( $(date +%s) + 1 ))")"
  query="{service_name=\"open-mission\"} | event=\"monitoring.incident\" | mode=\"${mode}\""

  curl -G -fsS "${LOKI_URL}/loki/api/v1/query_range" \
    --data-urlencode "query=${query}" \
    --data-urlencode "start=${RUN_START_NS}" \
    --data-urlencode "end=${end_ns}" \
    --data-urlencode "direction=backward" \
    --data-urlencode "limit=100"
}

extract_trace_id() {
  jq -r '
    first(
      .data.result[]?.values[]?
      | (.[1] | fromjson? // {}) as $line
      | (.[2] // {}) as $metadata
      | ($line.traceId // $metadata.trace_id // empty)
      | select(test("^[0-9a-fA-F]{16,32}$"))
    ) // empty
  '
}

wait_for_trace_id() {
  local mode="$1"
  local deadline
  local logs_json
  local trace_id

  deadline=$(( $(date +%s) + INGEST_TIMEOUT_SECONDS ))

  while (( $(date +%s) <= deadline )); do
    logs_json="$(get_loki_logs "${mode}" 2>/dev/null || true)"
    if [[ -n "${logs_json}" ]]; then
      trace_id="$(extract_trace_id <<<"${logs_json}")"
      if [[ -n "${trace_id}" ]]; then
        printf '%s\n' "${trace_id}"
        return 0
      fi
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done

  return 1
}

wait_for_jaeger_trace() {
  local trace_id="$1"
  local incident_operation="$2"
  local deadline
  local trace_json

  deadline=$(( $(date +%s) + INGEST_TIMEOUT_SECONDS ))

  while (( $(date +%s) <= deadline )); do
    trace_json="$(curl -fsS "${JAEGER_URL}/api/traces/${trace_id}" 2>/dev/null || true)"
    if [[ -n "${trace_json}" ]] && jq -e --arg incident_operation "${incident_operation}" '
      (.data[0].spans // []) as $spans
      | ($spans | length) >= 3
        and any($spans[]?; .operationName == "http post /monitoring/incident")
        and any($spans[]?; .operationName == $incident_operation)
        and any($spans[]?; .operationName == "monitoring.demo.downstream-call")
    ' >/dev/null <<<"${trace_json}"; then
      printf '%s\n' "${trace_json}"
      return 0
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done

  return 1
}

check_prerequisites() {
  local health_json
  local target_json

  require_command curl
  require_command jq
  require_command docker

  docker compose version >/dev/null 2>&1 \
    || fail "Docker Compose v2 is required."

  curl -fsS "${PROMETHEUS_URL}/-/ready" >/dev/null \
    || fail "Prometheus is not ready at ${PROMETHEUS_URL}."
  curl -fsS "${ALERTMANAGER_URL}/-/ready" >/dev/null \
    || fail "Alertmanager is not ready at ${ALERTMANAGER_URL}."
  curl -fsS "${LOKI_URL}/ready" >/dev/null \
    || fail "Loki is not ready at ${LOKI_URL}."
  curl -fsS "${ALLOY_URL}/-/ready" >/dev/null \
    || fail "Alloy is not ready at ${ALLOY_URL}."
  curl -fsS "${JAEGER_URL}/api/services" >/dev/null \
    || fail "Jaeger query API is not ready at ${JAEGER_URL}."

  health_json="$(curl -fsS "${APPLICATION_URL}/actuator/health")" \
    || fail "The monitoring-demo application is not ready at ${APPLICATION_URL}."
  jq -e '.status == "UP"' >/dev/null <<<"${health_json}" \
    || fail "The monitoring-demo application health is not UP."

  target_json="$(curl -G -fsS "${PROMETHEUS_URL}/api/v1/query" \
    --data-urlencode 'query=up{job="spring-boot"} == 1')" \
    || fail "Could not query the Prometheus spring-boot target."
  jq -e '.data.result | length > 0' >/dev/null <<<"${target_json}" \
    || fail "Prometheus spring-boot target is not UP."

  pass "Monitoring stack and application are ready"
}

main() {
  local error_trace_id
  local slow_trace_id
  local error_trace_json
  local slow_trace_json
  local error_span_count
  local slow_span_count
  local slow_child_duration_us
  local k6_status

  cd "${PROJECT_ROOT}"
  check_prerequisites

  if ! wait_for_recovery; then
    fail "Existing incident alerts did not clear before the drill."
  fi
  pass "No stale incident alerts are active"

  K6_LOG="$(mktemp "${TMPDIR:-/tmp}/open-mission-k6.XXXXXX")"
  RUN_START_NS="$(printf '%s000000000' "$(date +%s)")"

  printf 'Starting the 2m30s incident drill...\n'
  docker compose --profile drill run --rm k6 >"${K6_LOG}" 2>&1 &
  K6_PID=$!

  if ! wait_for_incident_signals; then
    fail "Both Prometheus firing alerts and Alertmanager active alerts were not observed."
  fi
  pass "Prometheus evaluated both incident rules as firing"
  pass "Alertmanager received both alerts through the local route"

  if wait "${K6_PID}"; then
    k6_status=0
  else
    k6_status=$?
  fi
  K6_PID=""

  if (( k6_status != 0 )); then
    fail "k6 failed with exit code ${k6_status}."
  fi
  pass "k6 HTTP checks and latency thresholds passed"

  if ! wait_for_recovery; then
    fail "Prometheus or Alertmanager still has an active incident alert after recovery."
  fi
  pass "Prometheus and Alertmanager both cleared the incident alerts"

  error_trace_id="$(wait_for_trace_id "error")" \
    || fail "No ERROR log with a traceId was found in Loki for this drill."
  slow_trace_id="$(wait_for_trace_id "slow")" \
    || fail "No SLOW log with a traceId was found in Loki for this drill."
  pass "Loki contains ERROR and SLOW logs with traceId values"

  error_trace_json="$(wait_for_jaeger_trace "${error_trace_id}" "monitoring.incident.error")" \
    || fail "The ERROR trace does not contain the expected Jaeger spans."
  slow_trace_json="$(wait_for_jaeger_trace "${slow_trace_id}" "monitoring.incident.slow")" \
    || fail "The SLOW trace does not contain the expected Jaeger spans."

  error_span_count="$(jq -r '.data[0].spans | length' <<<"${error_trace_json}")"
  slow_span_count="$(jq -r '.data[0].spans | length' <<<"${slow_trace_json}")"
  slow_child_duration_us="$(jq -r '
    [.data[0].spans[]?
      | select(.operationName == "monitoring.demo.downstream-call")
      | .duration
    ] | max // 0
  ' <<<"${slow_trace_json}")"

  if (( slow_child_duration_us < 1000000 || slow_child_duration_us > 2500000 )); then
    fail "SLOW child span duration ${slow_child_duration_us}us is outside 1.0-2.5s."
  fi

  pass "Jaeger correlated both logs to traces with the expected spans"
  pass "SLOW downstream child span stayed within 1.0-2.5 seconds"

  printf '\nVerification summary\n'
  printf '  ERROR traceId: %s (%s spans)\n' "${error_trace_id}" "${error_span_count}"
  printf '  SLOW traceId:  %s (%s spans)\n' "${slow_trace_id}" "${slow_span_count}"
  printf '  SLOW child:    %.3f seconds\n' "$(( slow_child_duration_us / 1000 ))e-3"
}

main "$@"
