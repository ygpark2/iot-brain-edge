package com.ainsoft.brain.ingest

import spray.json.*
import java.security.MessageDigest
import java.util.UUID

final case class IngestEvent(
                              eventId: String,
                              source: String,
                              payload: String,

                              schemaVersion: Option[String] = None,
                              eventType: Option[String] = None,
                              deviceId: Option[String] = None,
                              sensorType: Option[String] = None,
                              sessionId: Option[String] = None,
                              sensorTimestampMs: Option[Long] = None,
                              ingestTimestampMs: Option[Long] = None,

                              payloadEncoding: Option[String] = None,
                              payloadContentType: Option[String] = None
                            )

object IngestEvent extends DefaultJsonProtocol {
  def fromPayload(source: String, payload: String): IngestEvent = {
    val eventId = extractEventId(payload).getOrElse(sha256Hex(payload))
    IngestEvent(
      eventId = eventId,
      source = source,
      payload = payload,
      payloadEncoding = Some("utf8"),
      payloadContentType = Some("application/json")
    )
  }

  def randomId(): String = UUID.randomUUID().toString

  private def extractEventId(payload: String): Option[String] =
    try {
      payload.parseJson match {
        case JsObject(fields) =>
          fields.get("event_id") match {
            case Some(JsString(id)) if id.trim.nonEmpty => Some(id.trim)
            case _ => None
          }
        case _ => None
      }
    } catch { case _: Throwable => None }

  private def sha256Hex(s: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}
