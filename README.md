# IOT-BRAIN-EDGE

Open-source edge-to-cloud pipeline for sensor events, Kafka streaming, ClickHouse storage, and operational dashboards.

## What Is In The Repo

- `proto/`: shared contracts and event schema
- `core/`: shared domain code and utilities
- `edge-agent/`: Pekko-based edge runtime with local spool, HTTP mode, and Kafka mode
- `services/ingestion-service/`: HTTP ingest API and processed-topic ClickHouse writer
- `services/processor-service/`: raw `rawframes` Kafka consumer that persists into ClickHouse
- `services/inference-service/`: Python inference service with Kafka consumer and REST endpoint
- `services/alert-service/`: Kafka alert consumer and webhook sender
- `pipelines/flink-jobs/`: Flink jobs for sessionizing, feature extraction, aggregation, and inference triggering
- `dashboard/`: SvelteKit admin dashboard
- `deploy/`: Swarm stack, ClickHouse schema, and storage config
- `docs/`: architecture and operational notes
- `notebooks/`: example notebook assets

## Current Runtime Shape

The current default Swarm stack in `deploy/swarm/stack.yml` includes:

- Kafka
- Kafka UI
- MinIO
- ClickHouse
- `ingestion-service`
- `processor-service`
- dashboard
- notebook

`inference-service`, `alert-service`, and Flink jobs exist in the repo, but they are not started by `make stack-up` today. Run them separately when needed.

## Dashboard

The admin dashboard is now API-driven.

- Browser pages do not connect directly to Kafka or ClickHouse
- SvelteKit server routes under `dashboard/src/routes/api/` act as the only data access layer
- `Message Inspector` is hybrid:
  - `Live`: Kafka via server-side Kafka client
  - `History`: ClickHouse via server API
- `Settings` updates ClickHouse storage config and TTL policy through server APIs

### Screenshots

**Dashboard**

![Dashboard Overview](docs/images/dashboard.png)

**Devices**

![Devices](docs/images/devices.png)

**Message Inspector**

![Message Inspector](docs/images/message_inspector.png)

**Pipeline Topology**

![Pipeline Topology](docs/images/topology.png)

**Trace Timeline**

![Trace Timeline](docs/images/trace.png)

**Settings**

![Settings](docs/images/settings.png)

## Data Flow

### 1. HTTP ingest path

`edge-agent (HTTP mode)` -> `ingestion-service /v1/ingest` -> Kafka topic `rawframes`

This path is a bridge only. It does not write directly to ClickHouse.

### 2. Raw frame persistence

Kafka topic `rawframes` -> `processor-service` -> ClickHouse table `brain.rawframes`

### 3. Processed-topic persistence

Kafka topics:

- `rawframes-processed`
- `session-features`
- `inference-results`
- `inference-alerts`
- `env-features`
- `power-features`

are consumed by `ingestion-service` and written into the matching ClickHouse tables with retry and DLQ handling.

### 4. Optional analytics path

Flink jobs and `inference-service` produce the derived topics above. Those pieces are present in the repo, but they are optional/manual in the current setup.

## Quick Start

### Prerequisites

- Docker with Swarm enabled
- `sbt`
- Python 3.9+ for `services/inference-service`

### 1. Initialize Swarm

```bash
docker swarm init
```

### 2. Build local service images if needed

If the local Docker daemon does not already have the application images:

```bash
make build-ingest
make build-processor
```

### 3. Start the default stack

```bash
make stack-up
```

This deploys the Swarm stack and runs ClickHouse schema initialization.

### 4. Create Kafka topics

Kafka auto-create is disabled in the stack. Create topics explicitly:

```bash
make create-kafka-topics
```

### 5. Generate data

HTTP mode:

```bash
make run-edge-http
```

Kafka mode:

```bash
make run-edge-kafka
```

### 6. Open the UIs

```bash
make dashboard-ui
make kafka-ui
make clickhouse-ui
make notebook-ui
```

Default URLs:

- Dashboard: `http://localhost:5173`
- Kafka UI: `http://localhost:8089`
- ClickHouse HTTP: `http://localhost:8123`
- Notebook: `http://localhost:8888?token=brain`

## Common Commands

### Stack and schema

```bash
make stack-up
make stack-down
make stack-ps
make ch-init
```

### Kafka topics

```bash
make create-kafka-topics
make topic-list
```

### Local services

```bash
make run-ingest
make run-processor
make run-alert
make run-edge-http
make run-edge-kafka
```

### Logs

```bash
make logs-kafka
make logs-kafka-ui
make logs-clickhouse
make logs-notebook
```

## Verifying The Pipeline

Check ClickHouse row counts:

```bash
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.rawframes" && echo
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.session_features" && echo
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.inference_results" && echo
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.inference_alerts" && echo
```

Tail recent rawframes:

```bash
make ch-tail
```

## Flink And Inference

The repository still contains Flink jobs and the Python inference service.

Run Flink jobs locally:

```bash
sbt "project flinkJobs" test
make run-flink JOB=sessionizer
make run-flink JOB=feature-base
make run-flink JOB=inference-trigger
```

Run the Python inference service:

```bash
cd services/inference-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8090
```

Useful env vars:

```bash
export INFERENCE_KAFKA_BOOTSTRAP_SERVERS=localhost:9094
export INFERENCE_REQUESTS_TOPIC=inference-requests
export INFERENCE_RESULTS_TOPIC=inference-results
export INFERENCE_ALERTS_TOPIC=inference-alerts
export MODEL_VERSION=brain-v1-stable
export INFERENCE_ALERT_THRESHOLD=0.8
```

## ClickHouse Storage And TTL

The dashboard settings page currently manages:

- TTL for `rawframes`, `session_features`, `inference_results`, `inference_alerts`, `env_features`, `power_features`
- S3-compatible cold-storage config written to `deploy/clickhouse/config/storage.xml`

The server applies config changes with `SYSTEM RELOAD CONFIG`.

## More Documentation

- [Architecture](docs/architecture.md)
- [Operational Status And Gaps](docs/issues.md)
