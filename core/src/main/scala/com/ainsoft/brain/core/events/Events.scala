package com.ainsoft.brain.core.events

import spray.json.*

final case class FeatureEvent(
  deviceId: String,
  sessionId: String,
  sensorType: String,
  startTsMs: Long,
  endTsMs: Long,
  durationMs: Long,
  count: Long,
  meanPayloadBytes: Double,
  stdPayloadBytes: Double,
  peakPayloadBytes: Long,
  impulsePayloadBytes: Double,
  contactRatio: Double,
  loadingRate: Double,
  schemaVersion: String
)

final case class InferenceRequest(
  deviceId: String,
  sessionId: String,
  sensorType: String,
  startTsMs: Long,
  endTsMs: Long,
  featureSchemaVersion: String,
  features: List[Double]
)

final case class RawEventRecord(
  eventId: String,
  schemaVersion: String,
  eventType: String,
  deviceId: String,
  sensorType: String,
  sessionId: String,
  sensorTimestampMs: Long,
  ingestTimestampMs: Long,
  payloadB64: String
)

final case class SessionFeature(
  deviceId: String,
  sessionId: String,
  sensorType: String,
  startTsMs: Long,
  endTsMs: Long,
  durationMs: Long,
  count: Long,
  meanPayloadBytes: Double,
  stdPayloadBytes: Double,
  peakPayloadBytes: Long,
  impulsePayloadBytes: Double,
  contactRatio: Double,
  loadingRate: Double,
  featureSchemaVersion: String
)

final case class WindowFeature(
  deviceId: String,
  sensorType: String,
  windowStartMs: Long,
  windowEndMs: Long,
  windowSizeMs: Long,
  count: Long,
  meanValue: Double,
  featureSchemaVersion: String
)

final case class InferenceResult(
  deviceId: String,
  sessionId: String,
  sensorType: String,
  modelVersion: String,
  label: String,
  score: Double,
  startTsMs: Long,
  endTsMs: Long
)

final case class InferenceAlert(
  deviceId: String,
  sessionId: String,
  sensorType: String,
  modelVersion: String,
  label: String,
  score: Double,
  threshold: Double,
  startTsMs: Long,
  endTsMs: Long
)

object EventJsonProtocol extends DefaultJsonProtocol {
  implicit val featureFormat: RootJsonFormat[FeatureEvent] = jsonFormat14(FeatureEvent.apply)
  implicit val requestFormat: RootJsonFormat[InferenceRequest] = jsonFormat7(InferenceRequest.apply)
  implicit val rawRecordFormat: RootJsonFormat[RawEventRecord] = jsonFormat9(RawEventRecord.apply)
  implicit val sessionFeatureFormat: RootJsonFormat[SessionFeature] = jsonFormat14(SessionFeature.apply)
  implicit val windowFeatureFormat: RootJsonFormat[WindowFeature] = jsonFormat8(WindowFeature.apply)
  implicit val resultFormat: RootJsonFormat[InferenceResult] = jsonFormat8(InferenceResult.apply)
  implicit val alertFormat: RootJsonFormat[InferenceAlert] = jsonFormat9(InferenceAlert.apply)
}
