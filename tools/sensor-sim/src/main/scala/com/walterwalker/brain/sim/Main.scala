package com.walterwalker.brain.sim

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn
import java.util.UUID

object Main:

  final case class Config(
    url: String = "http://localhost:8080/v1/ingest",
    deviceId: String = "mock-1",
    sessionId: String = "session-1",
    hz: Int = 10
  )

  def main(args: Array[String]): Unit =
    val cfg = parseArgs(args.toList)

    given system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "sensor-sim")
    given ec: ExecutionContext = system.executionContext
    given mat: Materializer = Materializer(system)

    val interval: FiniteDuration =
      ((1000.0 / math.max(1, cfg.hz)).toLong).millis

    val http = Http()

    system.log.info(
      "sensor-sim started: url={} deviceId={} sessionId={} hz={} intervalMs={}",
      cfg.url, cfg.deviceId, cfg.sessionId, cfg.hz: java.lang.Integer, interval.toMillis: java.lang.Long
    )

    // IMPORTANT: keep the Cancellable from Source.tick (Keep.left), not Future[Done]
    val tickCancellable =
      Source
        .tick(0.seconds, interval, ())
        .map: _ =>
          val now = System.currentTimeMillis()
          val eventId = UUID.randomUUID().toString
          val payload = Base64.encode("demo-payload")
          s"""{"event_id":"$eventId","schema_version":"v1","event_type":"RawFrame","device_id":"${cfg.deviceId}","sensor_type":"GRF","session_id":"${cfg.sessionId}","sensor_timestamp_ms":$now,"ingest_timestamp_ms":$now,"payload_b64":"$payload"}"""
        .mapAsync(4): json =>
          postJson(http, cfg.url, json).map(_.isSuccess())
        .toMat(Sink.ignore)(Keep.left)
        .run()

    system.log.info("Press ENTER to stop sensor-sim...")
    StdIn.readLine()

    tickCancellable.cancel()
    system.terminate()

  private def postJson(http: org.apache.pekko.http.scaladsl.HttpExt, url: String, json: String)
                      (using system: ActorSystem[?], ec: ExecutionContext): Future[StatusCode] =
    val entity = HttpEntity(ContentTypes.`application/json`, ByteString(json))
    val req = HttpRequest(method = HttpMethods.POST, uri = url, entity = entity)
    http.singleRequest(req).map(_.status)

  private def parseArgs(args: List[String]): Config =
    def nextValue(flag: String, default: String): String =
      args.dropWhile(_ != flag).drop(1).headOption.getOrElse(default)

    val url = nextValue("--url", "http://localhost:8080/v1/ingest")
    val device = nextValue("--device", "mock-1")
    val session = nextValue("--session", "session-1")
    val hzStr = nextValue("--hz", "10")
    val hz = hzStr.toIntOption.getOrElse(10)

    Config(url = url, deviceId = device, sessionId = session, hz = hz)

  private object Base64:
    private val encoder = java.util.Base64.getEncoder
    def encode(s: String): String = encoder.encodeToString(s.getBytes("UTF-8"))
