
CREATE DATABASE IF NOT EXISTS brain;

CREATE TABLE IF NOT EXISTS brain.rawframes
(
  event_id String,
  ts DateTime64(3) DEFAULT now64(3),
  source LowCardinality(String) DEFAULT 'ingest',
  payload String
)
ENGINE = ReplacingMergeTree(ts)
ORDER BY (event_id);

-- 조회 성능을 위해 ts 기반 보조 인덱스 느낌으로 쓰려면 projection 고려 가능 (추후)
