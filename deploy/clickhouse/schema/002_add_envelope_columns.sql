
ALTER TABLE brain.rawframes
    ADD COLUMN IF NOT EXISTS schema_version Nullable(String) AFTER event_id,
    ADD COLUMN IF NOT EXISTS event_type Nullable(String) AFTER schema_version,
    ADD COLUMN IF NOT EXISTS device_id Nullable(String) AFTER event_type,
    ADD COLUMN IF NOT EXISTS sensor_type Nullable(String) AFTER device_id,
    ADD COLUMN IF NOT EXISTS session_id Nullable(String) AFTER sensor_type,
    ADD COLUMN IF NOT EXISTS sensor_timestamp_ms Nullable(Int64) AFTER session_id,
    ADD COLUMN IF NOT EXISTS ingest_timestamp_ms Nullable(Int64) AFTER sensor_timestamp_ms,
    ADD COLUMN IF NOT EXISTS payload_encoding Nullable(String) AFTER ingest_timestamp_ms,
    ADD COLUMN IF NOT EXISTS payload_content_type Nullable(String) AFTER payload_encoding;
