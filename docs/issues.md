# Operational Status And Gaps

This document tracks the current implemented shape of the repo and the remaining operational gaps.

## Implemented

### Kafka and topic lifecycle

- Kafka runs in Swarm with KRaft single-node configuration
- External host access is exposed on `localhost:9094`
- Internal service-to-service access uses `kafka:9092`
- Topic auto-creation is disabled
- `make create-kafka-topics` creates the required application topics

### ClickHouse storage path

- ClickHouse schema is initialized via `deploy/clickhouse/init.sh`
- Schema files are applied in sorted order from `deploy/clickhouse/schema/`
- `processor-service` persists `rawframes` into `brain.rawframes`
- `ingestion-service` persists processed topics into ClickHouse
- Both services use batch-oriented ClickHouse writers

### HTTP ingest

- `ingestion-service` exposes `/health`
- `ingestion-service` exposes `/v1/ingest`
- HTTP ingest publishes to Kafka topic `rawframes`
- HTTP ingest is currently a Kafka bridge, not a direct ClickHouse write path

### Retry and DLQ behavior

- `ingestion-service` retries ClickHouse writes for processed topics
- permanent failures are forwarded to a DLQ producer
- rawframe persistence path is separated into `processor-service`

### Dashboard architecture

- the dashboard is API-driven
- browser pages do not connect directly to Kafka or ClickHouse
- `Message Inspector` supports:
  - Kafka live sampling on the server
  - ClickHouse history via server API
- `Settings` can update TTL and S3-compatible storage configuration

### ClickHouse cold-storage configuration

- storage config is stored in:
  - `deploy/clickhouse/config/storage.xml`
  - `deploy/swarm/clickhouse/config/storage.xml`
- dashboard settings rewrite the local config file and trigger `SYSTEM RELOAD CONFIG`

## Current Gaps

### Default Swarm stack is partial

The current `make stack-up` / `deploy/swarm/stack.yml` does not start:

- Flink JobManager / TaskManager
- `inference-service`
- `alert-service`

These components still exist in the repo, but the default operational path is only partially end-to-end.

### Ingestion-service replica layout

`ingestion-service` is configured with `replicas: 2` and host-mode port publishing on `8082`.

On a single-node environment this can leave one task pending because only one task can bind the host port. This is a deployment-shape issue, not an application-code issue.

### Flink helper targets assume services that are not in the current stack

`Makefile` still contains `run-flink-cluster`, `run-all-flink`, and Flink log targets, but the current default Swarm stack file does not define Flink services. Those commands require a separate Flink deployment shape.

### Dashboard service is still development-shaped

The dashboard service currently runs:

- `npm install`
- `vite dev`

inside the container. This is convenient for local iteration, but not a production deployment model.

### Secrets and credentials are dev defaults

Current stack defaults include:

- `brain / brain` for ClickHouse
- `minio / minio12345` for MinIO
- notebook token `brain`

These should be replaced before any non-local deployment.

## Recommended Next Steps

### Deployment and runtime

- add a production dashboard image/build path
- decide whether `ingestion-service` should run as a single replica on host port `8082`
- either restore Flink services to the stack or remove stack-level Flink assumptions from commands

### Pipeline completeness

- document or automate the exact minimal path for:
  - HTTP ingest only
  - rawframe persistence only
  - full Flink + inference + alerts flow
- make topic creation part of bootstrap if desired

### Observability

- expose service metrics for ClickHouse write failures, retry count, DLQ count, and Kafka lag
- add a small health/status summary for optional services not started by the stack

### Security and configuration

- move default credentials to environment-managed secrets
- separate local-dev storage config from deployment-time storage config

## Quick Reality Check

As of the current repo state:

- default stack: Kafka + ClickHouse + ingestion-service + processor-service + dashboard + notebook
- rawframe persistence: working through `processor-service`
- processed-topic persistence: handled by `ingestion-service`
- dashboard: API-driven, hybrid inspector implemented
- full analytics pipeline: possible from repo contents, but not fully bootstrapped by `make stack-up` alone
