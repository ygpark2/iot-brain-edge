package com.ainsoft.brain.edge

import com.ainsoft.brain.core.{AgentType, Envelope}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object Router {
  sealed trait Command
  final case class Route(envelope: Envelope) extends Command

  private final case class WrappedProfile(response: DeviceRegistry.ProfileResponse, envelope: Envelope) extends Command

  def apply(
    registry: ActorRef[DeviceRegistry.Command],
    healthAgent: ActorRef[DomainAgent.Command],
    powerAgent: ActorRef[DomainAgent.Command],
    envAgent: ActorRef[DomainAgent.Command]
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Route(envelope) =>
          val replyTo = ctx.messageAdapter[DeviceRegistry.ProfileResponse](resp => WrappedProfile(resp, envelope))
          registry ! DeviceRegistry.GetProfile(envelope.deviceId, replyTo)
          Behaviors.same

        case WrappedProfile(response, envelope) =>
          response match {
            case DeviceRegistry.ProfileFound(profile) =>
              profile.agentType match {
                case AgentType.Health => healthAgent ! DomainAgent.Process(envelope)
                case AgentType.Power => powerAgent ! DomainAgent.Process(envelope)
                case AgentType.Env => envAgent ! DomainAgent.Process(envelope)
              }
              Behaviors.same
            case DeviceRegistry.ProfileMissing(_) =>
              ctx.log.warn("profile missing for device {}", envelope.deviceId)
              Behaviors.same
          }
      }
    }
}
