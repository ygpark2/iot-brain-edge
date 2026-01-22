package com.ainsoft.brain.edge

import com.ainsoft.brain.core.{AgentType, Envelope}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object EdgeSupervisor {
  sealed trait Command
  final case class Ingest(envelope: Envelope) extends Command
  case object Start extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      val registry = ctx.spawn(DeviceRegistry(), "device-registry")
      val uploader = ctx.spawn(Uploader(), "uploader")
      val healthAgent = ctx.spawn(HealthAgent(uploader), "health-agent")
      val powerAgent = ctx.spawn(PowerAgent(uploader), "power-agent")
      val envAgent = ctx.spawn(EnvAgent(uploader), "env-agent")

      val router = ctx.spawn(Router(registry, healthAgent, powerAgent, envAgent), "router")
      val ingest = ctx.spawn(IngestGateway(router), "ingest-gateway")

      Behaviors.receiveMessage {
        case Start =>
          ingest ! IngestGateway.Start
          Behaviors.same
        case Ingest(envelope) =>
          router ! Router.Route(envelope)
          Behaviors.same
      }
    }
}
