package com.walterwalker.brain.edge

import com.walterwalker.brain.core.{DeviceId, SessionId, RawFrame}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.stream.Materializer

import scala.concurrent.duration._

object DeviceConnection {

  // If you later want device-specific messages, put them here
  sealed trait Command

  def apply(deviceId: DeviceId, sessionId: SessionId): Behavior[Command] =
    Behaviors.setup { ctx =>
      implicit val mat: Materializer = Materializer(ctx.system)

      ctx.log.info("DeviceConnection started deviceId={} sessionId={}", deviceId.value, sessionId.value)

      Source
        .tick(0.seconds, 200.millis, ())
        .map(_ => RawFrame(deviceId, sessionId, System.currentTimeMillis(), "demo".getBytes("UTF-8")))
        .to(Sink.foreach(frame => ctx.log.debug("RawFrame bytes={} atMs={}", frame.bytes.length, frame.atMs)))
        .run()

      Behaviors.empty
    }
}
