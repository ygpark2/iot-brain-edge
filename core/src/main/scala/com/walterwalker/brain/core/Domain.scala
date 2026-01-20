package com.walterwalker.brain.core

final case class DeviceId(value: String) extends AnyVal
final case class SessionId(value: String) extends AnyVal

sealed trait SensorType { def name: String }
object SensorType {
  case object GRF extends SensorType { val name = "GRF" }
  case object PLANTAR extends SensorType { val name = "PLANTAR" }
  case object SCANNER extends SensorType { val name = "SCANNER" }
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
