# IOT-BRAIN-EDGE

센서 하드웨어(GRF / 압력 센서 / 3D 스캐너)를 실시간 인사이트로 연결하는 오픈소스 "Brain" 플랫폼입니다.

## 개요

이 프로젝트는 엣지 디바이스에서 수집된 센서 데이터를 실시간으로 처리하고, 머신러닝 모델을 통해 분석하며, 결과를 시계열 데이터베이스에 저장하는 엔드투엔드 데이터 파이프라인을 제공합니다.

## 모듈

- **proto/**: 공통 계약 (Protobuf 스키마 정의)
- **core/**: 공통 도메인 모델 및 유틸리티
- **edge-agent/**: Pekko 기반 엣지 런타임 (디바이스 상태 관리 + 스트림 파이프라인)
- **services/ingestion-service/**: Pekko HTTP 수집 API (HTTP → Kafka)
- **services/inference-service/**: Python 모델 서빙 (FastAPI + Kafka)
- **services/alert-service/**: 알림 처리 서비스 (Kafka → Webhook)
- **ui/dashboard/**: 대시보드 (placeholder)
- **deploy/**: 인프라 및 배포 자산 (Docker Swarm, ClickHouse 스키마 초기화)
- **docs/**: 아키텍처 및 운영 노트
- **pipelines/flink-jobs/**: Apache Flink 데이터 처리 잡

## 시스템 아키텍처

### 전체 구조

```
┌─────────────┐     ┌──────────────┐     ┌─────────┐
│  센서/Mock  │────▶│  edge-agent  │────▶│  Kafka  │
└─────────────┘     └──────────────┘     └─────────┘
                                               │
                                               ▼
                                        ┌─────────────┐
                                        │    Flink    │
                                        │  Processing │
                                        └─────────────┘
                                               │
                    ┌──────────────────────────┼───────────────────────┐
                    ▼                          ▼                       ▼
          ┌──────────────────┐      ┌─────────────────┐     ┌─────────────────┐
          │  inference-      │      │  ClickHouse     │     │  alert-service  │
          │  service         │      │  Writer Job     │     │                 │
          └──────────────────┘      └─────────────────┘     └─────────────────┘
                    │                          │
                    └────────────┬─────────────┘
                                 ▼
                          ClickHouse
```

### 핵심 컴포넌트

#### 1. Edge Agent
- 센서 또는 Mock 소스에서 데이터 수집
- 로컬 디스크에 데이터 스풀링 (연결 복구를 위해)
- Kafka로 데이터 발행 (또는 HTTP로 Ingestion Service에 전송)
- Pekko Actor 기반 분산 처리

#### 2. Kafka
- 분산 메시지 브로커로 데이터 스트림 버퍼링
- 내결함성과 확장성 제공
- 여러 토픽으로 파이프라인 분리

#### 3. Apache Flink
- 실시간 스트림 처리 엔진
- **세셔나이저**: 연속된 프레임을 세션으로 그룹화
- **피처 추출**: 다양한 도메인별 피처 추출 (기본, 건강, 파워, 환경)
- **인퍼런스 트리거**: 추론 요청 생성 및 발행

#### 4. Inference Service
- **Kafka 컨슈머**: `inference-requests` 토픽에서 요청 수신
- **FastAPI REST**: `/v1/infer` 엔드포인트로 직접 추론 지원
- **모델 서빙**: 현재는 데모 모델 (평균 계산)
- **결과 발행**: `inference-results` 토픽
- **알림 발행**: 점수가 임계값(기본 0.7) 초과 시 `inference-alerts` 토픽 발행

#### 5. ClickHouse
- 고성능 시계열 데이터베이스
- 원시 프레임, 세션 피처, 추론 결과, 알림 저장
- ReplacingMergeTree 엔진으로 중복 제거

#### 6. Ingestion Service
- HTTP 수집 엔드포인트 제공
- Kafka 컨슈머로 메시지 처리
- ClickHouse Writer로 데이터 지속
- DLQ(Dead Letter Queue) 지원으로 내결함성 보장

## 데이터 플로우

### 기본 파이프라인 (Kafka 기반)

```
센서/Mock 
  → edge-agent (로컬 스풀) 
  → Kafka (rawframes 토픽)
  → Flink Sessionizer (세션 생성)
  → Flink Feature Extractors (피처 추출)
  → Flink Inference Trigger (추론 요청)
  → inference-service (추론 실행)
  → Kafka (inference-results 토픽)
  → Flink ClickHouse Writer (결과 저장)
  → ClickHouse
```

### HTTP 수집 경로

```
HTTP 요청 
  → ingestion-service (HTTP 수집)
  → ClickHouse (직접 저장)
```

### 알림 파이프라인

```
inference-service 
  → Kafka (inference-alerts 토픽)
  → alert-service 
  → Webhook (외부 시스템 알림)
```

### ClickHouse 스키마

- **rawframes**: 원시 센서 데이터
- **session_features**: 세션별 집계 피처
- **inference_results**: 모델 추론 결과
- **inference_alerts**: 임계값 초과 알림

## 시작하기

### 전제 조건

- Docker (Swarm 모드 활성화)
- sbt (Scala 빌드 툴)
- Python 3.9+ (inference-service용)

### 인프라 구동 (Kafka, ClickHouse, Flink)

```bash
# Docker Swarm 초기화
docker swarm init

# 스택 배포
make stack-up
```

이 명령어는 다음을 포함합니다:
- Kafka (메시지 브로커)
- Kafka UI (http://localhost:8089)
- ClickHouse (데이터베이스)
- Flink JobManager (http://localhost:8081)
- Flink TaskManager

### ClickHouse 스키마 초기화

```bash
make ch-init
```

이 단계는 idempotent하므로 안전하게 재실행할 수 있습니다.

### Kafka 토픽 생성

```bash
# 기본 토픽
make kafka-init

# 전체 파이프라인 토픽 (Flink, Inference 포함)
make topics-pipeline
```

### 서비스 실행

#### 1) Ingestion Service (HTTP → Kafka)

```bash
make run-ingest
```

#### 2) Alert Service (Kafka → Webhook)

```bash
make run-alert
```

#### 3) Edge Agent (Kafka 모드)

```bash
make run-edge-kafka
```

#### 4) Edge Agent (HTTP 모드)

```bash
make run-edge-http
```

## Flink 잡 실행

Flink 잡은 개별적으로 실행되며, 파이프라인 구성에 따라 순서대로 실행해야 합니다.

### 세셔나이저

```bash
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner sessionizer"
```

YAML 설정 파일 사용:
```bash
export FLINK_CONFIG_YAML=./deploy/flink/flink.yml
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner sessionizer"
```

### 피처 추출기

```bash
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-base"
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-health"
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-power"
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-env"
```

### 인퍼런스 트리거

```bash
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner inference-trigger"
```

### ClickHouse Writer

```bash
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner clickhouse-ingest"
```

## Inference Service 실행

### 의존성 설치

```bash
cd services/inference-service
pip install -r requirements.txt
```

### 서비스 시작

```bash
uvicorn main:app --reload --port 8090
```

환경 변수 설정 (선택사항):
```bash
export INFERENCE_KAFKA_BOOTSTRAP_SERVERS=localhost:9094
export INFERENCE_REQUESTS_TOPIC=inference-requests
export INFERENCE_RESULTS_TOPIC=inference-results
export INFERENCE_ALERTS_TOPIC=inference-alerts
export MODEL_VERSION=demo-v1
export INFERENCE_ALERT_THRESHOLD=0.7
```

### API 사용 예시

```bash
curl -X POST "http://localhost:8090/v1/infer" \
  -H "Content-Type: application/json" \
  -d '{
    "device_id": "device-001",
    "session_id": "session-123",
    "sensor_type": "GRF",
    "start_ts_ms": 1737540000000,
    "end_ts_ms": 1737540050000,
    "feature_schema_version": "v1",
    "features": [0.1, 0.2, 0.3, 0.4, 0.5]
  }'
```

## 엔드투엔드 테스트

### 1) 인프라 시작 및 스키마 초기화

```bash
make stack-up
make ch-init
make kafka-init
make topics-pipeline
```

### 2) Ingestion Service 실행

```bash
make run-ingest
```

### 3) Alert Service 실행

```bash
make run-alert
```

### 4) Flink 잡 실행

별도 터미널에서 실행:

```bash
# 세셔나이저
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner sessionizer"

# 피처 추출기 (각각 별도 터미널)
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-base"
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-health"
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner inference-trigger"

# ClickHouse Writer
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner clickhouse-ingest"
```

### 5) Inference Service 실행

```bash
cd services/inference-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8090
```

### 6) Edge Agent 실행

```bash
make run-edge-kafka
```

### 7) 데이터 검증

ClickHouse에서 데이터 확인:

```bash
# 원시 프레임 수
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.rawframes" && echo

# 세션 피처 수
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.session_features" && echo

# 추론 결과 수
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.inference_results" && echo

# 알림 수
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.inference_alerts" && echo
```

카운트가 증가하면 파이프라인이 정상 작동 중입니다.

## 모니터링 및 UI

### UI 접속

```bash
# Kafka UI
make kafka-ui
# 접속: http://localhost:8089

# ClickHouse UI
make clickhouse-ui
# 접속: http://localhost:8123

# Flink UI
make flink-ui
# 접속: http://localhost:8081
```

### 로그 확인

```bash
make logs-kafka
make logs-kafka-ui
make logs-clickhouse
make logs-flink
```

## ClickHouse 스키마 관리

### 스키마 파일 구조

```
deploy/clickhouse/schema/
├── 001_init.sql           # 데이터베이스 및 기본 테이블
├── 002_add_envelope_columns.sql
├── 003_session_features.sql
├── 004_inference_results.sql
└── 005_inference_alerts.sql
```

### 권장 네이밍 컨벤션

0으로 채워진 숫자 접두사를 사용하여 파일이 올바른 순서로 적용되도록 합니다:

```
001_init.sql
002_add_columns.sql
003_indexes.sql
010_materialized_views.sql
```

### 스키마 초기화

```bash
make ch-init
```

모든 스키마 파일은 idempotent해야 합니다 (`IF NOT EXISTS` 사용).

### 특정 스키마만 적용

```bash
export CLICKHOUSE_SCHEMA_GLOB="001_*.sql"
make ch-init
```

## 문제 해결

### Kafka 연결 실패

```bash
# Kafka 상태 확인
docker service ls | grep kafka

# 로그 확인
make logs-kafka
```

### ClickHouse 연결 실패

```bash
# ClickHouse 로그 확인
make logs-clickhouse

# 스키마 재초기화
make ch-init
```

### Flink 잡 실패

```bash
# Flink JobManager 로그
make logs-flink

# Flink UI에서 잭 상태 확인
make flink-ui
```

## 추가 문서

- [아키텍처 상세](./docs/architecture.md)
- [운영 하드닝 체크리스트](./docs/issues.md)
- [AGENTS 사용 가이드](./AGENTS.md)

## 라이선스

[라이선스 정보가 여기에 포함됩니다]
