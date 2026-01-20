package com.walterwalker.brain.edge

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

object EdgeGuardian {

  // Root actor command (can be extended later)
  sealed trait Command
  final case class Connect(deviceId: String, sessionId: String) extends Command
  final case class Disconnect(deviceId: String) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      val deviceManager: ActorRef[DeviceManager.Command] =
        ctx.spawn(DeviceManager(), "device-manager")

      Behaviors.receiveMessage {
        case Connect(deviceId, sessionId) =>
          deviceManager ! DeviceManager.Connect(deviceId, sessionId)
          Behaviors.same

        case Disconnect(deviceId) =>
          deviceManager ! DeviceManager.Disconnect(deviceId)
          Behaviors.same
      }
    }
}
