# Architecture

## Overview

This repository implements an edge-to-cloud stream processing pipeline for sensor events. Data is collected at the edge, buffered/spooled locally, published to Kafka, processed by Apache Flink jobs, and persisted into ClickHouse. Inference is handled by a Python service that consumes inference requests from Kafka and publishes results/alerts back to Kafka. Alerts are delivered by a dedicated alert-service (Kafka → webhook).

The HTTP ingest path is supported via `services/ingestion-service`, which acts as an HTTP → Kafka bridge (no direct ClickHouse writes).

## Repository Structure

- proto/ : shared contracts (protobuf)
- core/ : shared domain + utilities
- edge-agent/ : Pekko-based edge runtime (device state + stream pipeline)
- services/ingestion-service/ : Pekko HTTP ingest API (HTTP → Kafka)
- services/inference-service/ : Python model serving (Kafka consumer + REST)
- services/alert-service/ : Kafka consumer → webhook
- pipelines/flink-jobs/ : Apache Flink stream processing jobs
- ui/dashboard/ : dashboard placeholder
- deploy/ : infrastructure and deployment assets (Swarm, Docker, ClickHouse init)
- docs/ : architecture and operational notes

## Components

### Edge Agent
- Collects data from sensors or mock sources
- Spools data locally before forwarding
- Publishes events to Kafka directly, or via HTTP to the ingestion-service

### Ingestion Service (HTTP → Kafka)
- Exposes `/v1/ingest`
- Accepts raw JSON payloads and publishes to Kafka (`rawframes`)
- Used when the edge cannot connect directly to Kafka

### Kafka
- Central event backbone
- Topics include:
  - rawframes (edge/ingest → raw events)
  - rawframes-env / rawframes-power (env/power split ingest)
  - rawframes-processed (Flink sessionizer output)
  - session-features (Flink sessionizer output)
  - features-base / features-health
  - env-features / power-features (window aggregates)
  - inference-requests (Flink inference-trigger output)
  - inference-results / inference-alerts (inference-service output)
  - inference-alerts-dlq (alert-service DLQ for permanent webhook errors)

### Apache Flink Jobs
- **Sessionizer**: `rawframes` → `session-features` + `rawframes-processed`
- **Feature Extractors**: `session-features` → `features-*`
  - base, health
- **Env Aggregator**: `rawframes-env` → `env-features` (1m/5m window averages)
- **Power Aggregator**: `rawframes-power` → `power-features` (1m/5m window averages)
- **Inference Trigger**: `features-health` → `inference-requests`
- **ClickHouse Ingest**: consumes `rawframes-processed`, `session-features`, `features-*`, `inference-results`, `inference-alerts` and writes to ClickHouse over HTTP

### Inference Service
- Kafka consumer for `inference-requests`
- Produces `inference-results` and `inference-alerts`
- Also exposes `/v1/infer` for direct HTTP inference
- Uses NVIDIA Triton (gRPC) for health-sensor inference when enabled

### Alert Service
- Kafka consumer for `inference-alerts`
- Sends webhooks to external systems
- On permanent HTTP errors, publishes to `inference-alerts-dlq`

### ClickHouse
- Time-series storage for raw frames, features, inference results, and alerts

### Dashboard (placeholder)
- UI placeholder for visualization

## Data Flow

### Kafka-based pipeline

Sensors/Mock → edge-agent (spool) → Kafka (`rawframes`)
→ Flink Sessionizer → Kafka (`session-features`, `rawframes-processed`)
→ Flink Feature Extractors → Kafka (`features-*`)
→ Flink Inference Trigger → Kafka (`inference-requests`)
→ inference-service → Kafka (`inference-results`, `inference-alerts`)
→ Flink ClickHouse Ingest → ClickHouse

Env/Power sensors → Kafka (`rawframes-env` / `rawframes-power`)
→ Flink Env/Power Aggregator → Kafka (`env-features` / `power-features`)
→ Flink ClickHouse Ingest → ClickHouse

## Triton Inference (health sensors)

The inference-service can send health sensor feature vectors to NVIDIA Triton over gRPC. Triton returns motion/biomechanics inference used to assist user movement prediction/diagnosis. The service emits Kafka `inference-results` and `inference-alerts` from Triton outputs.

### Key settings (env)
- `TRITON_URL` (host:port) enables Triton mode
- `TRITON_MODEL_NAME` / `TRITON_MODEL_VERSION`
- `TRITON_INPUT_NAME` (default: `features`)
- `TRITON_OUTPUT_SCORES` (default: `scores`)
- `TRITON_OUTPUT_LABELS` (optional; if omitted, argmax over scores)
- `TRITON_LABELS` (comma-separated label map)
- `INFERENCE_SENSOR_TYPES` (default: `GRF,PLANTAR,SCANNER`)

### HTTP ingest path

HTTP → ingestion-service → Kafka (`rawframes`) → same Flink pipeline as above

### Alerts

inference-service → Kafka (`inference-alerts`) → alert-service → Webhook

## Shared Contracts

- proto/brain_events.proto defines the event schema
- core/ provides shared domain models and utilities

## Deployment & Tooling

- Docker Swarm stack: deploy/swarm/stack.yml
- Local Docker compose: deploy/docker/docker-compose.yml
- ClickHouse schema initialization: deploy/clickhouse/init.sh and deploy/clickhouse/schema/*.sql
- Flink job config: deploy/flink/flink.yml

## Notes

- Operational hardening and design notes live in docs/issues.md
- For reliability guarantees and DLQ handling, follow the checklist in docs/issues.md
