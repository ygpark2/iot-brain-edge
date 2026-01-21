# IOT-BRAIN-EDGE

Open-source "Brain" platform that connects sensor hardware (GRF / plantar pressure / 3D scanner) to actionable insights.

## Modules
- proto/ : shared contracts (protobuf)
- core/ : shared domain + utilities
- edge-agent/ : Pekko-based edge runtime (device state + stream pipeline)
- services/ingestion-service/ : Pekko HTTP ingest API
- services/inference-service/ : Python model serving skeleton
- ui/dashboard/ : dashboard placeholder

## Quick start

### Prerequisites
- Docker (with Swarm mode enabled)
- sbt (Scala build tool)

### Start infrastructure (Kafka, ClickHouse, etc.)

```bash
make stack-up
```

### Initialize ClickHouse schema

```bash
make ch-init
```

> This step is idempotent and can be safely re-run.

### Run ingestion service (HTTP → Kafka)

```bash
make run-ingest
```

### Run alert service (Kafka → webhook)

```bash
make run-alert
```

### Run edge agent (Kafka mode)

```bash
make run-edge-kafka
```

### Run edge agent (HTTP mode)

```bash
make run-edge-http
```

## End-to-end test (Kafka → Flink → Kafka → ClickHouse)

> Note: ClickHouse ingestion currently happens via `ingestion-service`.

### 1) Start infra and init schemas

```bash
make stack-up
make ch-init
make kafka-init
make topics-pipeline
```

### 2) Run ingestion-service (HTTP ingest only)

```bash
make run-ingest
```

### 2b) Run alert-service (alerts → webhook)

```bash
make run-alert
```

### 3) Run Flink jobs (JobRunner + spec)

Sessionizer:
```bash
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner sessionizer"
```

YAML config example:
```bash
export FLINK_CONFIG_YAML=./deploy/flink/flink.yml
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner sessionizer"
```

Feature splitters:
```bash
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-base"
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-health"
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-power"
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner feature-env"
```

Inference trigger:
```bash
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner inference-trigger"
```

ClickHouse writer:
```bash
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner clickhouse-ingest"
```

### 4) Run inference-service (Kafka consumer + results/alerts)

```bash
cd services/inference-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8090
```

### 5) Run edge-agent (Kafka producer)

```bash
make run-edge-kafka
```

### 6) Verify data in ClickHouse

```bash
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.rawframes" && echo
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.session_features" && echo
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.inference_results" && echo
curl -s "http://localhost:8123/?query=SELECT%20count()%20FROM%20brain.inference_alerts" && echo
```

If counts increase, the flow is working end-to-end.

### View logs

```bash
make logs-ingest
make logs-ch
```

### Access UIs

```bash
make kafka-ui
make clickhouse-ui
make flink-ui
```

## ClickHouse schema initialization tips

The ClickHouse schema initializer (`deploy/clickhouse/init.sh`) supports applying **multiple schema files in order** using filename sorting.

### Recommended naming convention

Use zero-padded numeric prefixes so files are applied in the correct order:

```
001_init.sql
002_add_columns.sql
003_indexes.sql
010_materialized_views.sql
```

They will be executed in lexicographical order (which matches numeric order if zero-padded).

### Limiting which schemas are applied

You can control which schema files are applied by setting the glob pattern via environment variables:

```bash
export CLICKHOUSE_SCHEMA_GLOB="00*.sql"
make ch-init
```

Or in `.env`:

```env
CLICKHOUSE_SCHEMA_GLOB=001_*.sql
```

### Re-applying schemas

All schema files should be **idempotent** (use `IF NOT EXISTS` where possible), so that you can safely re-run:

```bash
make ch-init
```

This allows easy local resets, CI bootstrapping, and reproducible deployments.
