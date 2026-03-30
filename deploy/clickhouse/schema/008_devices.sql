-- Device metadata table for dashboard management
CREATE TABLE IF NOT EXISTS brain.devices
(
    id String,
    location String,
    sensors String,
    status String,
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY id;
