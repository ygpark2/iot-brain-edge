package com.ainsoft.brain.ingest

import com.ainsoft.brain.v1.brain_events.EventEnvelope
import java.util.Base64
import scala.util.Try

object EventDecoder {

  /** 헤더 기반 우선 → 실패하면 protobuf parse 시도 → 실패하면 UTF-8 문자열로 fallback */
  def decode(
              sourceTag: String,
              valueBytes: Array[Byte],
              contentTypeHeader: Option[String]
            ): IngestEvent = {

    val ct = contentTypeHeader.map(_.toLowerCase)

    // 1) content-type이 protobuf면 protobuf 우선
    if (ct.exists(_.contains("protobuf"))) {
      return fromProtobufOrThrow(sourceTag, valueBytes)
    }

    // 2) content-type이 json이면 string 처리
    if (ct.exists(_.contains("json"))) {
      val s = new String(valueBytes, "UTF-8")
      return IngestEvent.fromPayload(sourceTag, s)
    }

    // 3) 헤더 없으면: protobuf 먼저 시도 → 실패 시 UTF-8
    Try(EventEnvelope.parseFrom(valueBytes)).toOption match {
      case Some(env) =>
        fromEnvelope(sourceTag, env)
      case None =>
        val s = new String(valueBytes, "UTF-8")
        IngestEvent.fromPayload(sourceTag, s)
    }
  }

  private def fromProtobufOrThrow(sourceTag: String, bytes: Array[Byte]): IngestEvent = {
    val env = EventEnvelope.parseFrom(bytes)
    fromEnvelope(sourceTag, env)
  }

  private def fromEnvelope(sourceTag: String, env: EventEnvelope): IngestEvent = {
    val payloadB64 = Base64.getEncoder.encodeToString(env.payload.toByteArray)

    // ✅ 여기서 “IngestEvent를 운영급 메타데이터 포함 형태로 확장”하는 걸 권장
    IngestEvent(
      eventId = if (env.eventId.nonEmpty) env.eventId else IngestEvent.randomId(),
      source  = sourceTag,
      payload = payloadB64,

      schemaVersion = opt(env.schemaVersion),
      eventType = opt(env.eventType),
      deviceId = opt(env.deviceId),
      sensorType = opt(env.sensorType),
      sessionId = opt(env.sessionId),
      sensorTimestampMs = if (env.sensorTimestampMs != 0) Some(env.sensorTimestampMs) else None,
      ingestTimestampMs = if (env.ingestTimestampMs != 0) Some(env.ingestTimestampMs) else None,

      payloadEncoding = Some("base64"),
      payloadContentType = Some("application/x-protobuf; message=EventEnvelope")
    )
  }

  private def opt(s: String): Option[String] = Option(s).map(_.trim).filter(_.nonEmpty)
}
