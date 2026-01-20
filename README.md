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

### Run ingestion service

```bash
make run-ingest
```

### Run edge agent (Kafka mode)

```bash
make run-edge-kafka
```

### Run edge agent (HTTP mode)

```bash
make run-edge-http
```

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
