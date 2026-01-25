package com.ainsoft.brain.flink.model

import com.ainsoft.brain.core.events.*

export com.ainsoft.brain.core.events.{
  FeatureEvent,
  InferenceRequest,
  RawEventRecord,
  SessionFeature,
  WindowFeature,
  InferenceResult,
  InferenceAlert
}

final case class ParsedEnvelope(
  eventId: String,
  schemaVersion: String,
  eventType: String,
  deviceId: String,
  sensorType: String,
  sessionId: String,
  sensorTimestampMs: Long,
  ingestTimestampMs: Long,
  payload: Array[Byte]
)

final case class RawFrameJson(
  event_id: String,
  schema_version: String,
  event_type: String,
  device_id: String,
  sensor_type: String,
  session_id: String,
  sensor_timestamp_ms: Long,
  ingest_timestamp_ms: Long,
  payload_b64: String
)
