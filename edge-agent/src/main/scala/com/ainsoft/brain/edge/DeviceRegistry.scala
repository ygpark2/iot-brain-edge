package com.ainsoft.brain.edge

import com.ainsoft.brain.core.{AgentType, DeviceProfile, Envelope, SensorType, UploadPolicy}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object DeviceRegistry {
  sealed trait Command
  final case class GetProfile(deviceId: String, replyTo: ActorRef[ProfileResponse]) extends Command

  sealed trait ProfileResponse
  final case class ProfileFound(profile: DeviceProfile) extends ProfileResponse
  final case class ProfileMissing(deviceId: String) extends ProfileResponse

  private val defaultProfile = DeviceProfile(
    deviceId = "default",
    agentType = AgentType.Health,
    allowedSensorTypes = Set(SensorType.GRF, SensorType.PLANTAR, SensorType.SCANNER),
    uploadPolicy = UploadPolicy()
  )

  def apply(): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetProfile(deviceId, replyTo) =>
        // TODO: replace with real cache/DB lookup
        replyTo ! ProfileFound(defaultProfile.copy(deviceId = deviceId))
        Behaviors.same
    }
}
