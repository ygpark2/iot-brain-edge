package com.ainsoft.brain.edge

import com.ainsoft.brain.core.{Envelope, UploadPolicy}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object DomainAgent {
  sealed trait Command
  final case class Process(envelope: Envelope) extends Command
}

final case class AgentConfig(name: String, uploadPolicy: UploadPolicy)

object HealthAgent {
  def apply(uploader: ActorRef[Uploader.Command]): Behavior[DomainAgent.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case DomainAgent.Process(env) =>
          ctx.log.debug("HealthAgent processing device={} sensor={} bytes={}", env.deviceId, env.sensorType.name, env.payload.length: java.lang.Integer)
          uploader ! Uploader.Enqueue(env)
          Behaviors.same
      }
    }
}

object PowerAgent {
  def apply(uploader: ActorRef[Uploader.Command]): Behavior[DomainAgent.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case DomainAgent.Process(env) =>
          ctx.log.debug("PowerAgent processing device={} sensor={} bytes={}", env.deviceId, env.sensorType.name, env.payload.length: java.lang.Integer)
          uploader ! Uploader.Enqueue(env)
          Behaviors.same
      }
    }
}

object EnvAgent {
  def apply(uploader: ActorRef[Uploader.Command]): Behavior[DomainAgent.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case DomainAgent.Process(env) =>
          ctx.log.debug("EnvAgent processing device={} sensor={} bytes={}", env.deviceId, env.sensorType.name, env.payload.length: java.lang.Integer)
          uploader ! Uploader.Enqueue(env)
          Behaviors.same
      }
    }
}
