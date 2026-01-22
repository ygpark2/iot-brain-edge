package com.ainsoft.brain.core

final case class DeviceId(value: String) extends AnyVal
final case class SessionId(value: String) extends AnyVal

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

sealed trait BrainEvent { def deviceId: DeviceId; def sessionId: SessionId; def atMs: Long }

final case class DeviceConnected(deviceId: DeviceId, sessionId: SessionId, atMs: Long) extends BrainEvent
final case class DeviceDisconnected(deviceId: DeviceId, sessionId: SessionId, atMs: Long) extends BrainEvent

final case class RawFrame(
  deviceId: DeviceId,
  sessionId: SessionId,
  atMs: Long,
  bytes: Array[Byte]
) extends BrainEvent
