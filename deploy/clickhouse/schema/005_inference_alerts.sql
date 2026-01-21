
CREATE TABLE IF NOT EXISTS brain.inference_alerts
(
  device_id String,
  session_id String,
  sensor_type String,
  model_version String,
  label String,
  score Float64,
  threshold Float64,
  start_ts_ms Int64,
  end_ts_ms Int64,
  created_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY (device_id, session_id, sensor_type, model_version);
