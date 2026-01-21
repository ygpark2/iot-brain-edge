# Architecture

## Overview

This repository implements an edge-to-cloud data pipeline for sensor events. Data is collected at the edge, buffered/spooled locally, forwarded through Kafka, and persisted in ClickHouse via the ingestion service. HTTP ingest is also supported by the ingestion service and is intended to share the same writer pipeline.

## Repository Structure

- proto/ : shared contracts (protobuf)
- core/ : shared domain + utilities
- edge-agent/ : Pekko-based edge runtime (device state + stream pipeline)
- services/ingestion-service/ : Pekko HTTP ingest API
- services/inference-service/ : Python model serving skeleton
- ui/dashboard/ : dashboard placeholder
- deploy/ : infrastructure and deployment assets (Swarm, Docker, ClickHouse init)
- docs/ : architecture and operational notes

## Components

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
- Provides HTTP ingest endpoint
- Processes and persists data into ClickHouse

### ClickHouse
- Stores and manages time-series data
- Supports fast analytical queries

### Inference Service (skeleton)
- Placeholder for model serving

### Dashboard (placeholder)
- Placeholder UI for visualization

## Data Flow

Sensors/Mock → edge-agent (spool) → Kafka → ingestion-service → ClickHouse

HTTP ingest → ingestion-service → ClickHouse (intended to share the same writer pipeline)

ClickHouse does NOT consume Kafka directly in the chosen (A) architecture. The ingestion-service is the single Kafka consumer responsible for persistence.

## Shared Contracts

- proto/brain_events.proto defines the event schema
- core/ provides shared domain models and utilities

## Deployment & Tooling

- Docker Swarm stack: deploy/swarm/stack.yml
- Local Docker compose: deploy/docker/docker-compose.yml
- ClickHouse schema initialization: deploy/clickhouse/init.sh and deploy/clickhouse/schema/*.sql

## Notes

- Operational hardening and design notes live in docs/issues.md
- For reliability guarantees, DLQ, and consumer behavior, follow the checklist in docs/issues.md
