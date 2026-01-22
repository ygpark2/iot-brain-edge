package com.ainsoft.brain.flink.jobs.clickhouse

import com.ainsoft.brain.flink.io.JsonDeserializer
import com.ainsoft.brain.core.events.EventJsonProtocol
import com.ainsoft.brain.core.events.{InferenceAlert, InferenceResult, RawEventRecord, SessionFeature}
import com.ainsoft.brain.flink.util.Env
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.api.connector.sink2.{Sink, SinkWriter, WriterInitContext}
import spray.json.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

final class ClickHouseSink[
  T
](
  insertQuery: String,
  toJsonLine: T => String,
  httpUrl: String,
  user: Option[String],
  password: Option[String]
) extends Sink[T] {
  override def createWriter(context: WriterInitContext): SinkWriter[T] =
    new ClickHouseSinkWriter(insertQuery, toJsonLine, httpUrl, user, password)
}

final class ClickHouseSinkWriter[
  T
](
  insertQuery: String,
  toJsonLine: T => String,
  httpUrl: String,
  user: Option[String],
  password: Option[String]
) extends SinkWriter[T] {

  import scala.compiletime.uninitialized

  @transient private var http: java.net.http.HttpClient = uninitialized
  @transient private var endpoint: String = uninitialized

  private def init(): Unit = {
    if (http == null) {
      http = java.net.http.HttpClient.newHttpClient()
      val encoded = URLEncoder.encode(insertQuery, StandardCharsets.UTF_8)
      endpoint = s"${httpUrl}/?query=${encoded}"
    }
  }

  override def write(value: T, context: SinkWriter.Context): Unit = {
    init()
    val line = toJsonLine(value)
    val body = java.net.http.HttpRequest.BodyPublishers.ofString(line + "\n")
    val builder = java.net.http.HttpRequest.newBuilder()
      .uri(java.net.URI.create(endpoint))
      .POST(body)
      .header("Content-Type", "application/json")

    val req = (user, password) match {
      case (Some(u), Some(p)) =>
        val auth = java.util.Base64.getEncoder.encodeToString(s"${u}:${p}".getBytes(StandardCharsets.UTF_8))
        builder.header("Authorization", s"Basic ${auth}").build()
      case _ => builder.build()
    }

    val resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
    if (resp.statusCode() / 100 != 2) {
      throw new RuntimeException(s"ClickHouse HTTP ${resp.statusCode()} body=${resp.body()}")
    }
  }

  override def flush(endOfInput: Boolean): Unit = {}

  override def close(): Unit = {}
}

object ClickHouseIngestJobSpec extends com.ainsoft.brain.flink.jobs.JobSpec {
  override val name: String = "clickhouse-ingest"

  override def register(env: org.apache.flink.streaming.api.environment.StreamExecutionEnvironment): Unit = {
    val bootstrapServers = Env.get("CH_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val rawTopic = Env.get("CH_RAW_TOPIC", "rawframes-processed")
    val featureTopic = Env.get("CH_FEATURE_TOPIC", "session-features")
    val featureHealthTopic = Env.get("CH_FEATURE_HEALTH_TOPIC", "features-health")
    val featurePowerTopic = Env.get("CH_FEATURE_POWER_TOPIC", "features-power")
    val featureEnvTopic = Env.get("CH_FEATURE_ENV_TOPIC", "features-env")
    val featureBaseTopic = Env.get("CH_FEATURE_BASE_TOPIC", "features-base")
    val resultTopic = Env.get("CH_RESULT_TOPIC", "inference-results")
    val alertTopic = Env.get("CH_ALERT_TOPIC", "inference-alerts")
    val groupId = Env.get("CH_GROUP_ID", "flink-clickhouse-writer")

    val httpUrl = Env.get("CH_HTTP_URL", "http://localhost:8123")
    val db = Env.get("CH_DB", "brain")
    val rawTable = Env.get("CH_RAW_TABLE", "rawframes")
    val featureTable = Env.get("CH_FEATURE_TABLE", "session_features")
    val resultTable = Env.get("CH_RESULT_TABLE", "inference_results")
    val alertTable = Env.get("CH_ALERT_TABLE", "inference_alerts")
    val user = Env.getOpt("CH_USER")
    val password = Env.getOpt("CH_PASSWORD")

    val rawSource = KafkaSource.builder[RawEventRecord]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(rawTopic)
      .setGroupId(groupId + "-raw")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[RawEventRecord](using EventJsonProtocol.rawRecordFormat, TypeInformation.of(classOf[RawEventRecord])))
      .build()

    val featureSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featureTopic)
      .setGroupId(groupId + "-features")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val featureHealthSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featureHealthTopic)
      .setGroupId(groupId + "-features-health")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val featurePowerSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featurePowerTopic)
      .setGroupId(groupId + "-features-power")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val featureEnvSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featureEnvTopic)
      .setGroupId(groupId + "-features-env")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val featureBaseSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featureBaseTopic)
      .setGroupId(groupId + "-features-base")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val resultSource = KafkaSource.builder[InferenceResult]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(resultTopic)
      .setGroupId(groupId + "-results")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[InferenceResult](using EventJsonProtocol.resultFormat, TypeInformation.of(classOf[InferenceResult])))
      .build()

    val alertSource = KafkaSource.builder[InferenceAlert]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(alertTopic)
      .setGroupId(groupId + "-alerts")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[InferenceAlert](using EventJsonProtocol.alertFormat, TypeInformation.of(classOf[InferenceAlert])))
      .build()

    val rawSink = new ClickHouseSink[RawEventRecord](
      s"INSERT INTO ${db}.${rawTable} (event_id, source, payload, schema_version, event_type, device_id, sensor_type, session_id, sensor_timestamp_ms, ingest_timestamp_ms, payload_encoding, payload_content_type) FORMAT JSONEachRow",
      r => JsObject(
        "event_id" -> JsString(r.eventId),
        "source" -> JsString("flink"),
        "payload" -> JsString(r.payloadB64),
        "schema_version" -> JsString(r.schemaVersion),
        "event_type" -> JsString(r.eventType),
        "device_id" -> JsString(r.deviceId),
        "sensor_type" -> JsString(r.sensorType),
        "session_id" -> JsString(r.sessionId),
        "sensor_timestamp_ms" -> JsNumber(r.sensorTimestampMs),
        "ingest_timestamp_ms" -> JsNumber(r.ingestTimestampMs),
        "payload_encoding" -> JsString("base64"),
        "payload_content_type" -> JsString("application/json")
      ).compactPrint,
      httpUrl,
      user,
      password
    )

    val featureSink = new ClickHouseSink[SessionFeature](
      s"INSERT INTO ${db}.${featureTable} (device_id, session_id, sensor_type, start_ts_ms, end_ts_ms, duration_ms, count, mean_payload_bytes, std_payload_bytes, peak_payload_bytes, impulse_payload_bytes, contact_ratio, loading_rate, feature_schema_version) FORMAT JSONEachRow",
      f => JsObject(
        "device_id" -> JsString(f.deviceId),
        "session_id" -> JsString(f.sessionId),
        "sensor_type" -> JsString(f.sensorType),
        "start_ts_ms" -> JsNumber(f.startTsMs),
        "end_ts_ms" -> JsNumber(f.endTsMs),
        "duration_ms" -> JsNumber(f.durationMs),
        "count" -> JsNumber(f.count),
        "mean_payload_bytes" -> JsNumber(f.meanPayloadBytes),
        "std_payload_bytes" -> JsNumber(f.stdPayloadBytes),
        "peak_payload_bytes" -> JsNumber(f.peakPayloadBytes),
        "impulse_payload_bytes" -> JsNumber(f.impulsePayloadBytes),
        "contact_ratio" -> JsNumber(f.contactRatio),
        "loading_rate" -> JsNumber(f.loadingRate),
        "feature_schema_version" -> JsString(f.featureSchemaVersion)
      ).compactPrint,
      httpUrl,
      user,
      password
    )

    val resultSink = new ClickHouseSink[InferenceResult](
      s"INSERT INTO ${db}.${resultTable} (device_id, session_id, sensor_type, model_version, label, score, start_ts_ms, end_ts_ms) FORMAT JSONEachRow",
      r => JsObject(
        "device_id" -> JsString(r.deviceId),
        "session_id" -> JsString(r.sessionId),
        "sensor_type" -> JsString(r.sensorType),
        "model_version" -> JsString(r.modelVersion),
        "label" -> JsString(r.label),
        "score" -> JsNumber(r.score),
        "start_ts_ms" -> JsNumber(r.startTsMs),
        "end_ts_ms" -> JsNumber(r.endTsMs)
      ).compactPrint,
      httpUrl,
      user,
      password
    )

    val alertSink = new ClickHouseSink[InferenceAlert](
      s"INSERT INTO ${db}.${alertTable} (device_id, session_id, sensor_type, model_version, label, score, threshold, start_ts_ms, end_ts_ms) FORMAT JSONEachRow",
      a => JsObject(
        "device_id" -> JsString(a.deviceId),
        "session_id" -> JsString(a.sessionId),
        "sensor_type" -> JsString(a.sensorType),
        "model_version" -> JsString(a.modelVersion),
        "label" -> JsString(a.label),
        "score" -> JsNumber(a.score),
        "threshold" -> JsNumber(a.threshold),
        "start_ts_ms" -> JsNumber(a.startTsMs),
        "end_ts_ms" -> JsNumber(a.endTsMs)
      ).compactPrint,
      httpUrl,
      user,
      password
    )

    env.fromSource(rawSource, WatermarkStrategy.noWatermarks(), "raw-source").sinkTo(rawSink).name("raw-sink")
    env.fromSource(featureSource, WatermarkStrategy.noWatermarks(), "feature-source").sinkTo(featureSink).name("feature-sink")
    env.fromSource(featureHealthSource, WatermarkStrategy.noWatermarks(), "feature-health-source").sinkTo(featureSink).name("feature-health-sink")
    env.fromSource(featurePowerSource, WatermarkStrategy.noWatermarks(), "feature-power-source").sinkTo(featureSink).name("feature-power-sink")
    env.fromSource(featureEnvSource, WatermarkStrategy.noWatermarks(), "feature-env-source").sinkTo(featureSink).name("feature-env-sink")
    env.fromSource(featureBaseSource, WatermarkStrategy.noWatermarks(), "feature-base-source").sinkTo(featureSink).name("feature-base-sink")
    env.fromSource(resultSource, WatermarkStrategy.noWatermarks(), "result-source").sinkTo(resultSink).name("result-sink")
    env.fromSource(alertSource, WatermarkStrategy.noWatermarks(), "alert-source").sinkTo(alertSink).name("alert-sink")
  }

  private def env(name: String, default: String): String =
    Env.get(name, default)

  private def envOpt(name: String): Option[String] =
    Env.getOpt(name)

  def main(args: Array[String]): Unit = {
    val bootstrapServers = Env.get("CH_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val rawTopic = Env.get("CH_RAW_TOPIC", "rawframes-processed")
    val featureTopic = Env.get("CH_FEATURE_TOPIC", "session-features")
    val featureHealthTopic = Env.get("CH_FEATURE_HEALTH_TOPIC", "features-health")
    val featurePowerTopic = Env.get("CH_FEATURE_POWER_TOPIC", "features-power")
    val featureEnvTopic = Env.get("CH_FEATURE_ENV_TOPIC", "features-env")
    val featureBaseTopic = Env.get("CH_FEATURE_BASE_TOPIC", "features-base")
    val resultTopic = Env.get("CH_RESULT_TOPIC", "inference-results")
    val alertTopic = Env.get("CH_ALERT_TOPIC", "inference-alerts")
    val groupId = Env.get("CH_GROUP_ID", "flink-clickhouse-writer")

    val httpUrl = Env.get("CH_HTTP_URL", "http://localhost:8123")
    val db = Env.get("CH_DB", "brain")
    val rawTable = Env.get("CH_RAW_TABLE", "rawframes")
    val featureTable = Env.get("CH_FEATURE_TABLE", "session_features")
    val resultTable = Env.get("CH_RESULT_TABLE", "inference_results")
    val alertTable = Env.get("CH_ALERT_TABLE", "inference_alerts")
    val user = Env.getOpt("CH_USER")
    val password = Env.getOpt("CH_PASSWORD")

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val rawSource = KafkaSource.builder[RawEventRecord]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(rawTopic)
      .setGroupId(groupId + "-raw")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[RawEventRecord](using EventJsonProtocol.rawRecordFormat, TypeInformation.of(classOf[RawEventRecord])))
      .build()

    val featureSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featureTopic)
      .setGroupId(groupId + "-features")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val featureHealthSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featureHealthTopic)
      .setGroupId(groupId + "-features-health")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val featurePowerSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featurePowerTopic)
      .setGroupId(groupId + "-features-power")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val featureEnvSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featureEnvTopic)
      .setGroupId(groupId + "-features-env")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val featureBaseSource = KafkaSource.builder[SessionFeature]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(featureBaseTopic)
      .setGroupId(groupId + "-features-base")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[SessionFeature](using EventJsonProtocol.sessionFeatureFormat, TypeInformation.of(classOf[SessionFeature])))
      .build()

    val resultSource = KafkaSource.builder[InferenceResult]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(resultTopic)
      .setGroupId(groupId + "-results")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[InferenceResult](using EventJsonProtocol.resultFormat, TypeInformation.of(classOf[InferenceResult])))
      .build()

    val alertSource = KafkaSource.builder[InferenceAlert]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(alertTopic)
      .setGroupId(groupId + "-alerts")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new JsonDeserializer[InferenceAlert](using EventJsonProtocol.alertFormat, TypeInformation.of(classOf[InferenceAlert])))
      .build()

    val rawSink = new ClickHouseSink[RawEventRecord](
      s"INSERT INTO ${db}.${rawTable} (event_id, source, payload, schema_version, event_type, device_id, sensor_type, session_id, sensor_timestamp_ms, ingest_timestamp_ms, payload_encoding, payload_content_type) FORMAT JSONEachRow",
      r => JsObject(
        "event_id" -> JsString(r.eventId),
        "source" -> JsString("flink"),
        "payload" -> JsString(r.payloadB64),
        "schema_version" -> JsString(r.schemaVersion),
        "event_type" -> JsString(r.eventType),
        "device_id" -> JsString(r.deviceId),
        "sensor_type" -> JsString(r.sensorType),
        "session_id" -> JsString(r.sessionId),
        "sensor_timestamp_ms" -> JsNumber(r.sensorTimestampMs),
        "ingest_timestamp_ms" -> JsNumber(r.ingestTimestampMs),
        "payload_encoding" -> JsString("base64"),
        "payload_content_type" -> JsString("application/json")
      ).compactPrint,
      httpUrl,
      user,
      password
    )

    val featureSink = new ClickHouseSink[SessionFeature](
      s"INSERT INTO ${db}.${featureTable} (device_id, session_id, sensor_type, start_ts_ms, end_ts_ms, duration_ms, count, mean_payload_bytes, std_payload_bytes, peak_payload_bytes, impulse_payload_bytes, contact_ratio, loading_rate, feature_schema_version) FORMAT JSONEachRow",
      f => JsObject(
        "device_id" -> JsString(f.deviceId),
        "session_id" -> JsString(f.sessionId),
        "sensor_type" -> JsString(f.sensorType),
        "start_ts_ms" -> JsNumber(f.startTsMs),
        "end_ts_ms" -> JsNumber(f.endTsMs),
        "duration_ms" -> JsNumber(f.durationMs),
        "count" -> JsNumber(f.count),
        "mean_payload_bytes" -> JsNumber(f.meanPayloadBytes),
        "std_payload_bytes" -> JsNumber(f.stdPayloadBytes),
        "peak_payload_bytes" -> JsNumber(f.peakPayloadBytes),
        "impulse_payload_bytes" -> JsNumber(f.impulsePayloadBytes),
        "contact_ratio" -> JsNumber(f.contactRatio),
        "loading_rate" -> JsNumber(f.loadingRate),
        "feature_schema_version" -> JsString(f.featureSchemaVersion)
      ).compactPrint,
      httpUrl,
      user,
      password
    )

    val resultSink = new ClickHouseSink[InferenceResult](
      s"INSERT INTO ${db}.${resultTable} (device_id, session_id, sensor_type, model_version, label, score, start_ts_ms, end_ts_ms) FORMAT JSONEachRow",
      r => JsObject(
        "device_id" -> JsString(r.deviceId),
        "session_id" -> JsString(r.sessionId),
        "sensor_type" -> JsString(r.sensorType),
        "model_version" -> JsString(r.modelVersion),
        "label" -> JsString(r.label),
        "score" -> JsNumber(r.score),
        "start_ts_ms" -> JsNumber(r.startTsMs),
        "end_ts_ms" -> JsNumber(r.endTsMs)
      ).compactPrint,
      httpUrl,
      user,
      password
    )

    val alertSink = new ClickHouseSink[InferenceAlert](
      s"INSERT INTO ${db}.${alertTable} (device_id, session_id, sensor_type, model_version, label, score, threshold, start_ts_ms, end_ts_ms) FORMAT JSONEachRow",
      a => JsObject(
        "device_id" -> JsString(a.deviceId),
        "session_id" -> JsString(a.sessionId),
        "sensor_type" -> JsString(a.sensorType),
        "model_version" -> JsString(a.modelVersion),
        "label" -> JsString(a.label),
        "score" -> JsNumber(a.score),
        "threshold" -> JsNumber(a.threshold),
        "start_ts_ms" -> JsNumber(a.startTsMs),
        "end_ts_ms" -> JsNumber(a.endTsMs)
      ).compactPrint,
      httpUrl,
      user,
      password
    )

    env.fromSource(rawSource, WatermarkStrategy.noWatermarks(), "raw-source").sinkTo(rawSink).name("raw-sink")
    env.fromSource(featureSource, WatermarkStrategy.noWatermarks(), "feature-source").sinkTo(featureSink).name("feature-sink")
    env.fromSource(featureHealthSource, WatermarkStrategy.noWatermarks(), "feature-health-source").sinkTo(featureSink).name("feature-health-sink")
    env.fromSource(featurePowerSource, WatermarkStrategy.noWatermarks(), "feature-power-source").sinkTo(featureSink).name("feature-power-sink")
    env.fromSource(featureEnvSource, WatermarkStrategy.noWatermarks(), "feature-env-source").sinkTo(featureSink).name("feature-env-sink")
    env.fromSource(featureBaseSource, WatermarkStrategy.noWatermarks(), "feature-base-source").sinkTo(featureSink).name("feature-base-sink")
    env.fromSource(resultSource, WatermarkStrategy.noWatermarks(), "result-source").sinkTo(resultSink).name("result-sink")
    env.fromSource(alertSource, WatermarkStrategy.noWatermarks(), "alert-source").sinkTo(alertSink).name("alert-sink")
  }
}
