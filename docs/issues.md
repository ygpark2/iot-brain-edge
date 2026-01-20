

# Operational Hardening Checklist

## Data Flow & Architecture
- [ ] Kafka → ingestion-service → ClickHouse
- [ ] No direct ClickHouse Kafka Engine usage (A-plan)
- [ ] HTTP ingest path shares same writer pipeline

### Background
Kafka에서 수집된 데이터를 ingestion-service가 처리하여 ClickHouse로 저장하는 구조는 데이터 파이프라인의 견고함과 확장성을 보장하기 위해 필수적입니다. Kafka Engine을 ClickHouse에서 직접 사용하는 대신 ingestion-service를 두는 이유는 유연한 에러 처리, 재처리, DLQ, 그리고 비동기 처리가 가능하기 때문입니다. HTTP ingest 경로도 동일한 writer 파이프라인을 공유함으로써, 운영상의 일관성과 중복 방지를 달성합니다.

### Design Notes
- ingestion-service가 Kafka 메시지와 HTTP ingest 모두를 동일한 ClickHouseWriter로 처리합니다.
- ClickHouse의 Kafka Engine은 운영 환경에서 장애 처리와 재처리가 어려워 배제(A-plan).
- 데이터 흐름: Kafka → ingestion-service → ClickHouse, 중간에 DLQ 및 재시도 로직 포함.

### Failure Modes & Mitigations
- Kafka 컨슈머 장애: RestartSource 및 backoff 전략으로 자동 복구.
- ClickHouse 연결 실패: 재시도 및 DLQ로 전환.
- HTTP ingest와 Kafka ingest 간 race condition: 동일 writer 파이프라인 사용으로 방지.

## ClickHouseWriter (Persistence Layer)
- [ ] Batch insert via JSONEachRow
- [ ] In-memory buffer + periodic flush
- [ ] `enqueueAck` semantics (ack only after successful insert)
- [ ] Backpressure via `maxBuffer`
- [ ] Retry on transient failure
- [ ] Stable `flushIfNeeded` without `return` inside synchronized block
- [ ] Graceful shutdown: flush all pending

### Background
ClickHouseWriter는 대량의 이벤트를 효율적으로 저장하기 위해 메모리 버퍼와 배치 insert(JSONEachRow)를 사용합니다. 데이터 유실 방지와 처리량 극대화를 위해, ack는 insert 성공 후에만 반환(ack-after-write)합니다. Scala 3에서 `flushIfNeeded` 내에서 synchronized block 안에 `return`이 들어가면 타입 안정성 및 제어 흐름 문제가 발생할 수 있으므로, Option 기반으로 리팩토링합니다.

### Design Notes
- `enqueueAck`는 insert 성공 후에만 ack를 반환하여 at-least-once 보장.
- `maxBuffer` 이상 버퍼가 쌓이면 enqueue 실패(Backpressure), Kafka 오프셋 커밋을 막음.
- `flushIfNeeded`는 synchronized 블록 내부에서 return을 피하고, Option을 활용해 제어 흐름을 명확히 함.
- shutdown 시 KillSwitch → writer flush → HTTP unbind 순서로 graceful하게 종료.

### Failure Modes & Mitigations
- ClickHouse insert 실패(5xx, 네트워크): 재시도 및 버퍼 유지.
- ClickHouse insert 영구 실패(4xx): DLQ로 전환.
- Buffer overflow: enqueueAck 실패로 Kafka offset 커밋 방지, 데이터 유실 방지.
- shutdown 중 데이터 유실: flush all pending으로 완화.

## Kafka Consumer
- [ ] Commit only after ClickHouse insert success
- [ ] DLQ on permanent failure
- [ ] RestartSource with backoff for transient failures
- [ ] Parallelism control
- [ ] Graceful shutdown via KillSwitch
- [ ] Optional disable via env (`KAFKA_CONSUMER_ENABLED`)

### Background
Kafka 컨슈머는 메시지를 읽고 ClickHouse에 성공적으로 저장된 후에만 offset을 커밋해야 at-least-once가 보장됩니다. 영구 장애는 DLQ로, 일시 장애는 backoff 후 재시도로 처리합니다. 병렬성을 조절해 ClickHouse에 과부하를 주지 않으며, KillSwitch로 graceful shutdown이 가능합니다.

### Design Notes
- ClickHouse insert 성공 후에만 offset commit.
- DLQ는 ClickHouse 4xx와 같은 영구 장애 시에만 사용.
- RestartSource(backoff)로 일시 장애 자동 복구.
- `KAFKA_CONSUMER_ENABLED` env로 컨슈머 비활성화 가능.

### Failure Modes & Mitigations
- ClickHouse 장애(네트워크/5xx): backoff 후 재시도.
- ClickHouse 4xx: DLQ로 전환, 재처리 방지.
- 컨슈머 병렬성 과도: 설정값으로 제한.
- shutdown 중 offset 미커밋: flush 후 commit으로 완화.

## DLQ (Dead Letter Queue)
- [ ] Dedicated DLQ topic
- [ ] Structured error payload
- [ ] Persistent producer stream (not per-message)

### Background
DLQ는 영구 장애(예: ClickHouse 4xx, 파싱 불가 등) 발생 시 이벤트를 별도 토픽에 저장해 원인 분석 및 재처리가 가능하게 합니다. per-message로 producer를 생성하면 오버헤드와 성능 저하가 심각하므로, persistent한 producer stream/queue가 필수입니다.

### Design Notes
- DLQ topic은 별도 생성, structured JSON payload로 에러 원인 포함.
- DLQ producer는 stream/queue로 지속적으로 운영, per-message 생성 금지.
- ClickHouse HTTP 4xx는 영구 장애로 DLQ, 5xx/네트워크는 재시도.

### Failure Modes & Mitigations
- DLQ producer 장애: persistent stream으로 재생성 및 복구.
- DLQ topic 미생성: 초기화 스크립트(`make kafka-init`)로 보장.
- DLQ 과부하: 모니터링 및 alerting 필요.

## Swarm / Infra
- [ ] docker stack.yml (not docker-compose)
- [ ] Kafka + ClickHouse services
- [ ] Overlay network
- [ ] Health checks

### Background
운영 환경에서는 docker-compose 대신 stack.yml을 사용하는 것이 서비스 디스커버리, 롤링 업데이트, 네트워크 분리 등에서 유리합니다. Kafka와 ClickHouse를 overlay 네트워크로 연결하고, health check로 서비스 상태를 지속적으로 감시합니다.

### Design Notes
- docker stack.yml 기반 배포.
- Kafka, ClickHouse, ingestion-service가 overlay network로 연결.
- healthcheck: readiness/liveness probe 적용.

### Failure Modes & Mitigations
- 서비스 간 네트워크 단절: overlay network로 완화.
- 프로세스 다운: healthcheck로 자동 재시작.
- compose 환경과의 차이: stack.yml에서 서비스 의존성 명시.

## Initialization & Tooling
- [ ] ClickHouse schema init via shell + Makefile
- [ ] Glob-based schema migration (001, 002, ...)
- [ ] `make kafka-init` for topic creation
- [ ] `make stack-up`, `make stack-down`

### Background
운영 환경에서는 Kafka 토픽 자동 생성이 꺼져있는 경우가 많으므로, 명시적으로 `make kafka-init`으로 토픽을 생성해야 합니다. ClickHouse 스키마는 shell+Makefile로 관리하며, glob 기반(001, 002, ...) 마이그레이션으로 버전 관리를 합니다.

### Design Notes
- ClickHouse 테이블/인덱스/뷰 생성 스크립트는 glob(001, 002, ...)로 관리.
- `make kafka-init`은 모든 필요한 토픽을 생성.
- `make stack-up`, `make stack-down`으로 전체 스택 컨트롤.

### Failure Modes & Mitigations
- Kafka topic 미생성: `make kafka-init`으로 방지.
- 스키마 마이그레이션 누락: glob 기반으로 자동 적용.
- stack 불일치: Makefile로 일관성 유지.

## Reliability Guarantees
- [ ] At-least-once delivery
- [ ] Idempotency via event_id
- [ ] Reprocessing-safe ClickHouse schema

### Background
at-least-once는 메시지가 최소 한 번은 처리됨을 의미하며, 중복이 발생할 수 있습니다. 이를 보완하기 위해 idempotency(event_id 기반)가 필요합니다. ClickHouse는 ReplacingMergeTree, FINAL, append-only 등 다양한 중복 제거 전략을 지원합니다.

### Design Notes
- at-least-once: ClickHouse insert 성공 후만 offset commit.
- event_id: payload에 event_id 필드가 있으면 추출, 없으면 sha256(payload)로 생성하여 idempotency 보장.
- ClickHouse dedup: ReplacingMergeTree(이상적이지만 FINAL 필요, 성능 tradeoff), 또는 raw append-only 후 쿼리/집계 레이어에서 dedup.

### Failure Modes & Mitigations
- 중복 insert: event_id 및 ClickHouse dedup으로 방지.
- event_id 누락: sha256(payload)로 fallback.
- ReplacingMergeTree FINAL 성능 저하: 쿼리/집계 레이어에서 dedup 활용.

## Observability (Planned)
- [ ] Buffer size metrics
- [ ] DLQ counters
- [ ] Insert latency
- [ ] Kafka lag

### Background
운영 환경에서 각종 지표(버퍼 크기, DLQ 발생 건수, insert latency, Kafka lag 등)를 모니터링해야 장애를 빠르게 감지하고 대응할 수 있습니다.

### Design Notes
- buffer size, DLQ count, insert latency, Kafka lag 등 Prometheus metric으로 노출 예정.
- Grafana 대시보드 및 alerting rule 설계 필요.

### Failure Modes & Mitigations
- 지표 누락: metric exporter 및 alert rule로 방지.
- DLQ 급증: 알림 및 대시보드로 조기 감지.

## TODO Before Production
- [ ] Prometheus / Grafana
- [ ] Alerting rules
- [ ] Canary deploy
- [ ] Schema migration tests

### Background
프로덕션 진입 전에는 모니터링/알림 체계, 카나리아 배포, 스키마 마이그레이션 테스트 등 운영 안정성을 위한 준비가 필수입니다.

### Design Notes
- Prometheus/Grafana 연동 및 대시보드 구축.
- 주요 장애 지표에 대한 alerting rule 정의.
- Canary deploy로 점진적 배포 및 롤백 전략 확보.
- 스키마 마이그레이션 자동화 및 테스트.

### Failure Modes & Mitigations
- 모니터링 미구축: 장애 조기 발견 불가 → Prometheus/Grafana 필수.
- 마이그레이션 실패: 테스트 및 rollback 전략 필수.

---

## Glossary
- **committable offset**: Kafka에서 메시지 처리가 끝난 후 커밋할 수 있는 오프셋.
- **at-least-once**: 메시지가 최소 한 번 이상 처리됨을 보장(중복 가능성 있음).
- **DLQ (Dead Letter Queue)**: 영구 장애가 발생한 메시지를 별도로 저장하는 Kafka 토픽.
- **backoff**: 장애 발생 시 재시도 간 점진적으로 대기 시간을 늘리는 전략.
- **idempotency**: 동일 이벤트가 여러 번 저장되어도 결과가 변하지 않음(event_id 기반).
- **ReplacingMergeTree**: ClickHouse의 중복 제거 테이블 엔진, event_id 기준으로 최신 row만 유지.
- **JSONEachRow**: ClickHouse에서 JSON 라인별로 batch insert하는 포맷.

## Suggested Study Path
1. Data Flow & Architecture 전체 흐름 복습
2. ClickHouseWriter의 ack-after-write, backpressure, flush 로직
3. Kafka Consumer의 at-least-once, DLQ 처리 방식
4. event_id, ClickHouse dedup 전략 및 idempotency 설계
5. Swarm/Infra 및 초기화/마이그레이션 툴링
6. Reliability Guarantees와 Observability 항목
7. TODO Before Production 체크리스트