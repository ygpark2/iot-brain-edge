package com.ainsoft.brain.flink.io

import com.ainsoft.brain.v1.brain_events.EventEnvelope
import com.ainsoft.brain.flink.model.*
import com.ainsoft.brain.core.events.EventJsonProtocol
import org.apache.flink.api.common.serialization.{DeserializationSchema, SerializationSchema}
import org.apache.flink.api.common.typeinfo.TypeInformation
import spray.json.*
import spray.json.DefaultJsonProtocol.*

import java.nio.charset.StandardCharsets
import scala.util.Try

final class EnvelopeDeserializer extends DeserializationSchema[ParsedEnvelope] {
  import EventJsonProtocol.*
  private given RootJsonFormat[RawFrameJson] = jsonFormat9(RawFrameJson.apply)

  override def deserialize(message: Array[Byte]): ParsedEnvelope = {
    val asProto = Try(EventEnvelope.parseFrom(message)).toOption
    asProto match {
      case Some(envelope) =>
        ParsedEnvelope(
          eventId = envelope.eventId,
          schemaVersion = envelope.schemaVersion,
          eventType = envelope.eventType,
          deviceId = envelope.deviceId,
          sensorType = envelope.sensorType,
          sessionId = envelope.sessionId,
          sensorTimestampMs = envelope.sensorTimestampMs,
          ingestTimestampMs = envelope.ingestTimestampMs,
          payload = envelope.payload.toByteArray
        )
      case None =>
        val json = new String(message, StandardCharsets.UTF_8)
        val raw = json.parseJson.convertTo[RawFrameJson]
        val payload = java.util.Base64.getDecoder.decode(raw.payload_b64)
        ParsedEnvelope(
          eventId = raw.event_id,
          schemaVersion = raw.schema_version,
          eventType = raw.event_type,
          deviceId = raw.device_id,
          sensorType = raw.sensor_type,
          sessionId = raw.session_id,
          sensorTimestampMs = raw.sensor_timestamp_ms,
          ingestTimestampMs = raw.ingest_timestamp_ms,
          payload = payload
        )
    }
  }

  override def isEndOfStream(nextElement: ParsedEnvelope): Boolean = false

  override def getProducedType: TypeInformation[ParsedEnvelope] =
    TypeInformation.of(classOf[ParsedEnvelope])
}

final class FeatureSerializer extends SerializationSchema[FeatureEvent] {
  import EventJsonProtocol.*
  override def serialize(element: FeatureEvent): Array[Byte] =
    element.toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
}

final class FeatureDeserializer extends DeserializationSchema[FeatureEvent] {
  import EventJsonProtocol.*
  override def deserialize(message: Array[Byte]): FeatureEvent =
    new String(message, StandardCharsets.UTF_8).parseJson.convertTo[FeatureEvent]

  override def isEndOfStream(nextElement: FeatureEvent): Boolean = false

  override def getProducedType: TypeInformation[FeatureEvent] =
    TypeInformation.of(classOf[FeatureEvent])
}

final class RawEventSerializer extends SerializationSchema[RawEventRecord] {
  import EventJsonProtocol.*
  override def serialize(element: RawEventRecord): Array[Byte] =
    element.toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
}

final class InferenceRequestSerializer extends SerializationSchema[InferenceRequest] {
  import EventJsonProtocol.*
  override def serialize(element: InferenceRequest): Array[Byte] =
    element.toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
}

final class JsonDeserializer[T](using fmt: RootJsonFormat[T], ti: TypeInformation[T]) extends DeserializationSchema[T] {
  override def deserialize(message: Array[Byte]): T =
    new String(message, StandardCharsets.UTF_8).parseJson.convertTo[T]

  override def isEndOfStream(nextElement: T): Boolean = false

  override def getProducedType: TypeInformation[T] = ti
}
