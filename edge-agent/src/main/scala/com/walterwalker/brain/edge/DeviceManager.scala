package com.walterwalker.brain.edge

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.Behavior
import com.walterwalker.brain.core.{DeviceId, SessionId}

object DeviceManager {

  sealed trait Command
  final case class Connect(deviceId: String, sessionId: String) extends Command
  final case class Disconnect(deviceId: String) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      ctx.log.info("DeviceManager online")

      Behaviors.receiveMessage {
        case Connect(deviceId, sessionId) =>
          ctx.log.info("Connect requested deviceId={} sessionId={}", deviceId, sessionId)
          val childName = s"device-$deviceId"
          ctx.child(childName).getOrElse {
            ctx.spawn(DeviceConnection(DeviceId(deviceId), SessionId(sessionId)), childName)
          }
          Behaviors.same

        case Disconnect(deviceId) =>
          ctx.log.info("Disconnect requested deviceId={}", deviceId)
          Behaviors.same
      }
    }
}
