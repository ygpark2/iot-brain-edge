
CREATE TABLE IF NOT EXISTS brain.power_features
(
  device_id String,
  sensor_type String,
  window_start_ms Int64,
  window_end_ms Int64,
  window_size_ms UInt64,
  count UInt64,
  mean_value Float64,
  feature_schema_version String,
  inserted_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(inserted_at)
ORDER BY (device_id, sensor_type, window_start_ms, feature_schema_version);
