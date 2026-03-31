CREATE TABLE IF NOT EXISTS brain.inference_results
(
  event_id String DEFAULT hex(sipHash64(concat(device_id, session_id, sensor_type, model_version, toString(created_at)))),
  device_id String,
  session_id String,
  sensor_type String,
  model_version String,
  label String,
  score Float64,
  start_ts_ms Int64,
  end_ts_ms Int64,
  created_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY (event_id)
TTL created_at + INTERVAL 30 DAY TO VOLUME 's3_tiered'
SETTINGS storage_policy = 'tiered';
