# Architecture

## Overview

This repository implements a sensor-event pipeline with three distinct layers:

1. Edge collection and forwarding
2. Kafka-backed transport and optional stream processing
3. ClickHouse persistence and operational dashboards

The current codebase supports both direct Kafka publishing and HTTP ingest from the edge. ClickHouse persistence is split between two services:

- `processor-service` writes raw `rawframes`
- `ingestion-service` writes processed and derived topics

The dashboard is API-driven and does not expose direct browser connections to Kafka or ClickHouse.

## Repository Map

- `proto/`: shared event contracts
- `core/`: shared runtime/domain utilities
- `edge-agent/`: edge runtime and local spool
- `services/ingestion-service/`: HTTP ingest + processed-topic ClickHouse writer
- `services/processor-service/`: rawframe ClickHouse writer
- `services/inference-service/`: Kafka consumer + REST inference service
- `services/alert-service/`: alert webhook delivery
- `pipelines/flink-jobs/`: optional stream processing jobs
- `dashboard/`: SvelteKit admin UI and server APIs
- `deploy/`: Swarm stack and ClickHouse initialization assets
- `docs/`: architecture and operational status

## Runtime Components

### Edge Agent

`edge-agent` can run in two modes:

- HTTP mode: sends to `ingestion-service /v1/ingest`
- Kafka mode: publishes directly to Kafka

Both modes use local disk spool buffering before forwarding.

### Kafka

Kafka is the transport backbone. Current topic families include:

- `rawframes`
- `rawframes-processed`
- `session-features`
- `features-base`
- `features-health`
- `features-power`
- `features-env`
- `inference-requests`
- `inference-results`
- `inference-alerts`
- DLQ topics such as `rawframes-processed-dlq`, `session-features-dlq`, `inference-results-dlq`, `inference-alerts-dlq`

Topic auto-creation is disabled in the Swarm stack. Topics must be created explicitly with `make create-kafka-topics`.

### Processor Service

`services/processor-service` consumes Kafka topic `rawframes` and writes JSON rows into ClickHouse table `brain.rawframes`.

This is the raw event persistence path.

### Ingestion Service

`services/ingestion-service` has two responsibilities:

1. Expose `/v1/ingest` and publish HTTP payloads into Kafka topic `rawframes`
2. Consume processed/derived Kafka topics and write them into ClickHouse

Current Kafka-to-ClickHouse topic mappings in `KafkaIngestConsumer.scala`:

- `rawframes-processed` -> `brain.rawframes`
- `session-features` -> `brain.session_features`
- `inference-results` -> `brain.inference_results`
- `inference-alerts` -> `brain.inference_alerts`
- `env-features` -> `brain.env_features`
- `power-features` -> `brain.power_features`

This consumer includes retry and DLQ forwarding on permanent write failure.

### Flink Jobs

Flink jobs are present in the repo and can be run manually via `make run-flink` or `make run-flink-cluster`, but they are not part of the current default Swarm stack.

The Flink project contains jobs for:

- sessionizing
- feature extraction
- env/power aggregation
- inference triggering
- ClickHouse ingest

### Inference Service

`services/inference-service`:

- consumes `inference-requests`
- publishes `inference-results`
- publishes `inference-alerts` when score exceeds threshold
- exposes `/v1/infer`

It supports a local fallback model and optional Triton-backed inference mode.

### Alert Service

`services/alert-service` consumes `inference-alerts` and forwards them to a webhook destination.

### ClickHouse

ClickHouse stores:

- raw frames
- session features
- inference results
- inference alerts
- env features
- power features
- device metadata

Cold-storage settings are driven by XML config in:

- `deploy/clickhouse/config/storage.xml`
- `deploy/swarm/clickhouse/config/storage.xml`

### Dashboard

The dashboard uses SvelteKit server APIs as the only backend integration layer.

Current behavior:

- metrics come from ClickHouse via `/api/dashboard/metrics`
- devices are read/written via `/api/devices`
- settings are read/written via `/api/settings`, `/api/settings/ttl`, `/api/settings/s3`
- live inspector reads Kafka on the server
- history inspector reads ClickHouse on the server

The browser does not directly connect to Kafka or ClickHouse.

## Data Flows

### HTTP ingest path

`edge-agent (HTTP mode)` -> `ingestion-service /v1/ingest` -> Kafka `rawframes`

### Direct Kafka path

`edge-agent (Kafka mode)` -> Kafka `rawframes`

### Raw persistence path

Kafka `rawframes` -> `processor-service` -> ClickHouse `brain.rawframes`

### Processed persistence path

Kafka processed topics -> `ingestion-service` consumer -> ClickHouse target tables

### Optional analytics path

Kafka `rawframes` -> Flink jobs -> derived topics -> `inference-service` and/or `ingestion-service` consumer -> ClickHouse

### Dashboard read path

Browser -> SvelteKit route/API -> Kafka or ClickHouse

## Deployment Shape

The current default Swarm stack in `deploy/swarm/stack.yml` includes:

- Kafka
- Kafka UI
- MinIO
- ClickHouse
- `processor-service`
- `ingestion-service`
- dashboard
- notebook

It does not currently include:

- Flink JobManager/TaskManager
- `inference-service`
- `alert-service`

Those components are manual or optional in the current repo state.

## Operational Notes

- `make stack-up` deploys the stack and runs ClickHouse schema init
- `make create-kafka-topics` is required because Kafka auto-create is disabled
- dashboard settings mutate `deploy/clickhouse/config/storage.xml` and call `SYSTEM RELOAD CONFIG`
- dashboard live inspector uses server-side Kafka access
- dashboard history inspector uses ClickHouse through server APIs
