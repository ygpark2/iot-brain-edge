package com.ainsoft.brain.edge

import com.ainsoft.brain.core.Envelope
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object Uploader {
  sealed trait Command
  final case class Enqueue(envelope: Envelope) extends Command

  def apply(): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Enqueue(envelope) =>
          // TODO: replace with disk spool + backoff uploader
          ctx.log.debug("Uploader enqueue device={} bytes={}", envelope.deviceId, envelope.payload.length: java.lang.Integer)
          Behaviors.same
      }
    }
}
