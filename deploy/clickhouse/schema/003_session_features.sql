
CREATE TABLE IF NOT EXISTS brain.session_features
(
  device_id String,
  session_id String,
  sensor_type String,
  start_ts_ms Int64,
  end_ts_ms Int64,
  duration_ms UInt64,
  count UInt64,
  mean_payload_bytes Float64,
  std_payload_bytes Float64,
  peak_payload_bytes UInt64,
  impulse_payload_bytes Float64,
  contact_ratio Float64,
  loading_rate Float64,
  feature_schema_version String,
  inserted_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(inserted_at)
ORDER BY (device_id, session_id, sensor_type, feature_schema_version);
