package com.ainsoft.brain.edge.transport

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.stream.scaladsl.{Flow, Keep, RestartFlow, Sink, Source}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

object HttpIngestClient:

  final case class Config(
    url: String = "http://127.0.0.1:8080/v1/ingest",
    deviceId: String,
    sessionId: String,
    sensorType: String = "GRF",
    hz: Int = 5
  )

  /** Create a running stream that periodically emits mock RawFrame envelopes and POSTs them to ingestion-service.
    * Returns a Cancellable you can cancel.
    */
  def runMock(cfg: Config)(using system: ActorSystem[?], ec: ExecutionContext, mat: Materializer) =
    val http = Http()

    val interval: FiniteDuration =
      ((1000.0 / math.max(1, cfg.hz)).toLong).millis

    // POST flow with backoff retry (network errors / server down)
    val postFlow: Flow[String, StatusCode, ?] =
      RestartFlow.withBackoff(
        settings = org.apache.pekko.stream.RestartSettings(
          minBackoff = 200.millis,
          maxBackoff = 3.seconds,
          randomFactor = 0.2
        ).withMaxRestarts(-1, 200.millis)
      ) { () =>
        Flow[String].mapAsync(4) { json =>
          postJson(http, cfg.url, json).recover { case ex =>
            system.log.warn("ingest post failed: {}", ex.getMessage)
            StatusCodes.ServiceUnavailable
          }
        }
      }

    val tick =
      Source
        .tick(0.seconds, interval, ())
        .map { _ =>
          val now = System.currentTimeMillis()
          val eventId = UUID.randomUUID().toString
          val payloadB64 = Base64.encode("edge-demo-payload")
          s"""{"event_id":"$eventId","schema_version":"v1","event_type":"RawFrame","device_id":"${cfg.deviceId}","sensor_type":"${cfg.sensorType}","session_id":"${cfg.sessionId}","sensor_timestamp_ms":$now,"ingest_timestamp_ms":$now,"payload_b64":"$payloadB64"}"""
        }
        .via(postFlow)
        .toMat(Sink.foreach { status =>
          if status.isSuccess() then system.log.debug("ingest ok status={}", status)
          else system.log.warn("ingest bad status={}", status)
        })(Keep.left)
        .run()

    system.log.info("HttpIngestClient started url={} deviceId={} sessionId={} hz={}",
      cfg.url, cfg.deviceId, cfg.sessionId, cfg.hz: java.lang.Integer)

    tick

  private def postJson(http: org.apache.pekko.http.scaladsl.HttpExt, url: String, json: String)
                      (using system: ActorSystem[?], ec: ExecutionContext): Future[StatusCode] =
    val entity = HttpEntity(ContentTypes.`application/json`, ByteString(json))
    val req = HttpRequest(method = HttpMethods.POST, uri = url, entity = entity)
    http.singleRequest(req).map(_.status)

  private object Base64:
    private val encoder = java.util.Base64.getEncoder
    def encode(s: String): String = encoder.encodeToString(s.getBytes("UTF-8"))
