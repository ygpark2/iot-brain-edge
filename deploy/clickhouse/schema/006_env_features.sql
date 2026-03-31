CREATE TABLE IF NOT EXISTS brain.env_features
(
  event_id String DEFAULT hex(sipHash64(concat(device_id, sensor_type, toString(inserted_at)))),
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
ORDER BY (event_id)
TTL inserted_at + INTERVAL 30 DAY TO VOLUME 's3_tiered'
SETTINGS storage_policy = 'tiered';
