# Architecture (draft)

## Components

- Edge Agent
- Mock Sender
- Kafka
- Ingestion Service
- ClickHouse

## Data Flow

Sensors/Mock → edge-agent (spool) → Kafka → ingestion-service → ClickHouse

ClickHouse does NOT consume Kafka directly in the chosen (A) architecture. The ingestion-service is the single Kafka consumer responsible for persistence.

## Service Responsibilities

### Edge Agent
- Collects data from sensors or mock sources
- Spools data locally before forwarding
- Publishes data to Kafka

### Mock Sender
- Generates synthetic sensor data for testing
- Sends data to the edge-agent

### Kafka
- Acts as a distributed message broker
- Buffers and distributes data streams

### Ingestion Service
- Consumes data from Kafka
- Processes and persists data into ClickHouse
- Serves as the single Kafka consumer for persistence

### ClickHouse
- Stores and manages time-series data
- Supports fast analytical queries

## Notes

The HTTP ingest endpoint currently acknowledges requests and logs data. It will be integrated with the same ClickHouse writer (and optionally Kafka) in the future.
