package com.ainsoft.brain.core

sealed trait AgentType { def name: String }
object AgentType {
  case object Health extends AgentType { val name = "health" }
  case object Power extends AgentType { val name = "power" }
  case object Env extends AgentType { val name = "env" }
}

sealed trait SensorType { def name: String }
object SensorType {
  case object GRF extends SensorType { val name = "GRF" }
  case object PLANTAR extends SensorType { val name = "PLANTAR" }
  case object SCANNER extends SensorType { val name = "SCANNER" }
  case object VOLT extends SensorType { val name = "VOLT" }
  case object AMP extends SensorType { val name = "AMP" }
  case object WATT extends SensorType { val name = "WATT" }
  case object KWH extends SensorType { val name = "KWH" }
  case object TEMP extends SensorType { val name = "TEMP" }
  case object HUMID extends SensorType { val name = "HUMID" }
  case object CO2 extends SensorType { val name = "CO2" }
  case object PM25 extends SensorType { val name = "PM25" }
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
