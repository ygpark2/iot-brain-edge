CREATE DATABASE IF NOT EXISTS brain;

CREATE TABLE IF NOT EXISTS brain.rawframes
(
  event_id String,
  ts DateTime64(3) DEFAULT now64(3),
  source LowCardinality(String) DEFAULT 'ingest',
  payload String
)
ENGINE = ReplacingMergeTree(ts)
ORDER BY (event_id)
TTL ts + INTERVAL 30 DAY TO VOLUME 's3_tiered'
SETTINGS storage_policy = 'tiered';
