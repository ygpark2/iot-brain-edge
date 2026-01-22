package com.ainsoft.brain.core

sealed trait AgentType { def name: String }
object AgentType {
  case object Health extends AgentType { val name = "health" }
  case object Power extends AgentType { val name = "power" }
  case object Env extends AgentType { val name = "env" }
}

final case class Envelope(
  tenantId: String,
  siteId: String,
  deviceId: String,
  sensorType: SensorType,
  tsMillis: Long,
  payload: Array[Byte],
  contentType: String,
  schemaVersion: String
)

final case class UploadPolicy(
  minBatchSize: Int = 100,
  maxBatchSize: Int = 1000,
  flushIntervalMs: Long = 1000
)

final case class DeviceProfile(
  deviceId: String,
  agentType: AgentType,
  allowedSensorTypes: Set[SensorType],
  uploadPolicy: UploadPolicy
)
