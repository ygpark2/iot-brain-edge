package com.ainsoft.brain.flink.jobs.sessionizer

import com.ainsoft.brain.flink.io.{EnvelopeDeserializer, FeatureSerializer, RawEventSerializer}
import com.ainsoft.brain.core.events.{FeatureEvent, RawEventRecord}
import com.ainsoft.brain.flink.model.ParsedEnvelope
import com.ainsoft.brain.flink.util.{Env, PayloadStats, SessionAggregate}
import com.ainsoft.brain.flink.jobs.JobSpec
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.{FilterFunction, MapFunction}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.connector.kafka.sink.{KafkaRecordSerializationSchema, KafkaSink}
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema.KafkaSinkContext
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector

import java.time.Duration

final class SessionizerProcessFunction(idleGapMs: Long)
  extends KeyedProcessFunction[String, ParsedEnvelope, FeatureEvent] {

  import scala.compiletime.uninitialized

  @transient private var state: SessionAggregate = uninitialized

  override def processElement(
    value: ParsedEnvelope,
    ctx: KeyedProcessFunction[String, ParsedEnvelope, FeatureEvent]#Context,
    out: Collector[FeatureEvent]
  ): Unit = {
    val eventTs = value.sensorTimestampMs
    val stats = PayloadStats.fromBytes(value.payload)

    if (state == null) {
      state = SessionAggregate(
        deviceId = value.deviceId,
        sessionId = value.sessionId,
        sensorType = value.sensorType,
        startTsMs = eventTs,
        endTsMs = eventTs,
        frameCount = 1L,
        sampleCount = stats.count,
        sum = stats.sum,
        sumSquares = stats.sumSquares,
        peak = stats.peak,
        firstMean = stats.mean,
        firstTsMs = eventTs,
        lastMean = stats.mean,
        lastTsMs = eventTs,
        contactCount = stats.contactCount
      )
    } else {
      state = state.copy(
        endTsMs = math.max(state.endTsMs, eventTs),
        frameCount = state.frameCount + 1,
        sampleCount = state.sampleCount + stats.count,
        sum = state.sum + stats.sum,
        sumSquares = state.sumSquares + stats.sumSquares,
        peak = math.max(state.peak, stats.peak),
        lastMean = stats.mean,
        lastTsMs = eventTs,
        contactCount = state.contactCount + stats.contactCount
      )
    }

    val timerTs = eventTs + idleGapMs
    ctx.timerService().registerEventTimeTimer(timerTs)
  }

  override def onTimer(
    timestamp: Long,
    ctx: KeyedProcessFunction[String, ParsedEnvelope, FeatureEvent]#OnTimerContext,
    out: Collector[FeatureEvent]
  ): Unit = {
    if (state != null) {
      val duration = state.endTsMs - state.startTsMs
      val mean = if (state.sampleCount == 0) 0.0 else state.sum.toDouble / state.sampleCount
      val variance = if (state.sampleCount == 0) 0.0 else (state.sumSquares.toDouble / state.sampleCount) - (mean * mean)
      val std = math.sqrt(math.max(variance, 0.0))
      val contactRatio = if (state.sampleCount == 0) 0.0 else state.contactCount.toDouble / state.sampleCount
      val timeDelta = math.max(1L, state.lastTsMs - state.firstTsMs)
      val loadingRate = (state.lastMean - state.firstMean) / (timeDelta.toDouble / 1000.0)

      val feature = FeatureEvent(
        deviceId = state.deviceId,
        sessionId = state.sessionId,
        sensorType = state.sensorType,
        startTsMs = state.startTsMs,
        endTsMs = state.endTsMs,
        durationMs = duration,
        count = state.frameCount,
        meanPayloadBytes = mean,
        stdPayloadBytes = std,
        peakPayloadBytes = state.peak.toLong,
        impulsePayloadBytes = state.sum.toDouble,
        contactRatio = contactRatio,
        loadingRate = loadingRate,
        schemaVersion = "v1"
      )
      out.collect(feature)
      state = null
    }
  }
}

object SessionizerJobSpec extends JobSpec {
  override val name: String = "sessionizer"

  override def register(env: StreamExecutionEnvironment): Unit = {
    val bootstrapServers = Env.get("FLINK_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val inputTopic = Env.get("FLINK_INPUT_TOPIC", "rawframes")
    val outputFeaturesTopic = Env.get("FLINK_FEATURES_TOPIC", "session-features")
    val outputRawTopic = Env.get("FLINK_RAW_TOPIC", "rawframes-processed")
    val groupId = Env.get("FLINK_GROUP_ID", "flink-sessionizer")
    val idleGapMs = Env.get("FLINK_SESSION_GAP_MS", "5000").toLong
    val watermarkLagMs = Env.get("FLINK_WATERMARK_LAG_MS", "5000").toLong

    val source = KafkaSource.builder[ParsedEnvelope]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(inputTopic)
      .setGroupId(groupId)
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new EnvelopeDeserializer)
      .build()

    val watermark = WatermarkStrategy
      .forBoundedOutOfOrderness[ParsedEnvelope](Duration.ofMillis(watermarkLagMs))
      .withTimestampAssigner(new SerializableTimestampAssigner[ParsedEnvelope] {
        override def extractTimestamp(element: ParsedEnvelope, recordTimestamp: Long): Long =
          if (element.sensorTimestampMs > 0) element.sensorTimestampMs else element.ingestTimestampMs
      })

    val parsed = env
      .fromSource(source, watermark, "kafka-source")
      .name("kafka-source")
      .filter(new FilterFunction[ParsedEnvelope] {
        override def filter(value: ParsedEnvelope): Boolean = value.eventType == "RawFrame"
      })

    val rawRecords = parsed
      .map(new MapFunction[ParsedEnvelope, RawEventRecord] {
        override def map(value: ParsedEnvelope): RawEventRecord = {
          val payloadB64 = java.util.Base64.getEncoder.encodeToString(value.payload)
          RawEventRecord(
            eventId = value.eventId,
            schemaVersion = value.schemaVersion,
            eventType = value.eventType,
            deviceId = value.deviceId,
            sensorType = value.sensorType,
            sessionId = value.sessionId,
            sensorTimestampMs = value.sensorTimestampMs,
            ingestTimestampMs = value.ingestTimestampMs,
            payloadB64 = payloadB64
          )
        }
      })
      .returns(TypeInformation.of(classOf[RawEventRecord]))

    val features = parsed
      .keyBy(new KeySelector[ParsedEnvelope, String] {
        override def getKey(value: ParsedEnvelope): String = s"${value.deviceId}:${value.sessionId}:${value.sensorType}"
      })
      .process(new SessionizerProcessFunction(idleGapMs))
      .name("sessionizer")

    val featureSink = KafkaSink.builder[FeatureEvent]()
      .setBootstrapServers(bootstrapServers)
      .setRecordSerializer(
        new KafkaRecordSerializationSchema[FeatureEvent] {
          override def serialize(
            element: FeatureEvent,
            context: KafkaSinkContext,
            timestamp: java.lang.Long
          ): org.apache.kafka.clients.producer.ProducerRecord[Array[Byte], Array[Byte]] = {
            new org.apache.kafka.clients.producer.ProducerRecord(
              outputFeaturesTopic,
              s"${element.deviceId}:${element.sessionId}:${element.sensorType}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
              new FeatureSerializer().serialize(element)
            )
          }
        }
      )
      .build()

    val rawSink = KafkaSink.builder[RawEventRecord]()
      .setBootstrapServers(bootstrapServers)
      .setRecordSerializer(
        new KafkaRecordSerializationSchema[RawEventRecord] {
          override def serialize(
            element: RawEventRecord,
            context: KafkaSinkContext,
            timestamp: java.lang.Long
          ): org.apache.kafka.clients.producer.ProducerRecord[Array[Byte], Array[Byte]] = {
            new org.apache.kafka.clients.producer.ProducerRecord(
              outputRawTopic,
              s"${element.deviceId}:${element.sessionId}:${element.sensorType}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
              new RawEventSerializer().serialize(element)
            )
          }
        }
      )
      .build()

    rawRecords.sinkTo(rawSink).name("raw-sink")
    features.sinkTo(featureSink).name("features-sink")
  }
}
