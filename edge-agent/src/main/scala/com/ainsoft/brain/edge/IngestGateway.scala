package com.ainsoft.brain.edge

import com.ainsoft.brain.core.{Envelope, SensorType}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object IngestGateway {
  sealed trait Command
  case object Start extends Command
  final case class FromHttp(envelope: Envelope) extends Command
  final case class FromMqtt(envelope: Envelope) extends Command
  final case class FromSerial(envelope: Envelope) extends Command

  def apply(router: ActorRef[Router.Command]): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Start =>
          ctx.log.info("IngestGateway started")
          Behaviors.same
        case FromHttp(env) =>
          router ! Router.Route(env)
          Behaviors.same
        case FromMqtt(env) =>
          router ! Router.Route(env)
          Behaviors.same
        case FromSerial(env) =>
          router ! Router.Route(env)
          Behaviors.same
      }
    }
}
