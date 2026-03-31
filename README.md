# IOT-BRAIN-EDGE

Open-source "Brain" platform that connects sensor hardware (GRF / plantar pressure / 3D scanner) to actionable insights.

## Modules
- **proto/**: **Single Source of Truth** for shared contracts (Protobuf definitions)
- **core/**: shared domain + utilities
- **edge-agent/**: Pekko-based edge runtime (device state + stream pipeline)
- **services/ingestion-service/**: Pekko HTTP ingest API
- **services/inference-service/**: Python model serving skeleton
- **dashboard/**: SvelteKit-based admin dashboard
  - **Real-time Monitoring**: System throughput and service health visualization.
  - **Device Management (CRUD)**: Register and configure edge devices.
  - **Pipeline Topology**: Interactive map of service connectivity and data flow.
  - **Message Inspector**: Real-time Kafka message sampling and Protobuf-to-JSON decoding.
  - **Trace Timeline**: End-to-end journey tracking for specific session IDs.
  - **pipelines/flink-jobs/**: Apache Flink data processing jobs (**Pure Scala**)

  ## Dashboard & Analytics

  The Brain Edge Dashboard provides a comprehensive interface for system monitoring, data lifecycle management, and advanced analytics.

  ### 1. Pipeline Topology
  Real-time visualization of the entire data pipeline, mapping services to specific Kafka topics.
  - **Visual Flow**: Animated particles show active data streams.
  - **Stage Mapping**: Clear separation between Ingestion, Processing (Flink), and Consumption (Inference/ClickHouse).
  - **Back-loops**: Precise routing for feedback loops like inference alerts.

  > ![Pipeline Topology Placeholder](docs/images/topology.png)
  > *Note: End-to-end data flow from Edge Agent to ClickHouse via Kafka topics.*

  ### 2. Data Archiving (S3 & TTL)
  Manage your data retention policy and cold storage settings directly from the UI.
  - **Tiered Storage**: Move aged data (e.g., > 30 days) from local hot storage to AWS S3 / MinIO.
  - **Dynamic Config**: Update S3 endpoints and credentials without restarting ClickHouse.
  - **Retention Control**: Set per-table TTL (Time-To-Live) for automatic archiving.

  ### 3. Spark Notebook Integration
  Integrated Jupyter environment for deep-dive analysis using PySpark.
  - **Direct Access**: One-click jump to Spark Notebooks with pre-configured ClickHouse connectors.
  - **Pre-built Templates**: Sample notebooks for querying raw frames and session features.

  ### 4. Admin Dashboard Screenshots
  The dashboard includes dedicated views for operations, debugging, and system control.

  **Overview Dashboard**

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

  ## System Architecture
  ...
### Overview

```
┌─────────────┐     ┌──────────────┐     ┌─────────┐
│ Sensors/Mock│────▶│  edge-agent  │────▶│  Kafka  │ (Protobuf: EventEnvelope)
└─────────────┘     └──────────────┘     └─────────┘
                                               │
                                               ▼
                                        ┌─────────────┐
                                        │    Flink    │ (Protobuf Deserialize)
                                        │  Processing │
                                        └─────────────┘
                                               │
                    ┌──────────────────────────┼───────────────────────┐
                    ▼                          ▼                       ▼
          ┌──────────────────┐      ┌─────────────────┐     ┌─────────────────┐
          │  inference-      │      │  ClickHouse     │     │  alert-service  │
          │  service         │      │  Writer Job     │     │                 │
          └──────────────────┘      └─────────────────┘     └─────────────────┘
           (Protobuf Req/Res)       (Final Data Storage)    (JSON/Webhook Out)
                    │                          │
                    └────────────┬─────────────┘
                                 ▼
                          ClickHouse
```

### Data Schema & Serialization

This platform uses **Protocol Buffers (Protobuf)** across all stages for high-performance serialization.

- **EventEnvelope (proto/brain_events.proto)**: The common wrapper for all data. It includes `event_id`, `device_id`, `sensor_type`, timestamps, and a `payload` byte field for the actual data.
- **Key Data Types (Payload contents)**:
  - **RawFrame**: Original sensor data frame from devices.
  - **WindowFeature**: Summary data aggregated over time windows (mean, count, etc.).
  - **SessionFeature**: Feature values extracted by grouping data into sessions.
  - **InferenceRequest/Result**: Request and result data for ML model inference.

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

### Unit Testing

```bash
sbt "project flinkJobs" test
```

Submit to Flink cluster (shows in Flink UI):
```bash
make run-all-flink
```

Submit a single job to the cluster:
```bash
make run-flink-cluster JOB=sessionizer
```

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

### Window Aggregators

```bash
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner env-aggregator"
sbt "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner power-aggregator"
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
