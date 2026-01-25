package com.ainsoft.brain.flink.jobs.aggregate

import com.ainsoft.brain.flink.io.{EnvelopeDeserializer, WindowFeatureSerializer}
import com.ainsoft.brain.flink.model.{ParsedEnvelope, WindowFeature}
import com.ainsoft.brain.flink.jobs.JobSpec
import com.ainsoft.brain.flink.util.Env
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.{AggregateFunction, FlatMapFunction, FilterFunction}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.connector.kafka.sink.{KafkaRecordSerializationSchema, KafkaSink}
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema.KafkaSinkContext
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import spray.json.*

import java.nio.charset.StandardCharsets
import java.time.Duration
import scala.util.Try

final case class SensorReading(deviceId: String, sensorType: String, timestampMs: Long, value: Double)
final case class SensorKey(deviceId: String, sensorType: String)
final case class SumCount(sum: Double, count: Long)

object PayloadValueParser {
  private val candidateFields = List("value", "reading", "sensor_value", "v")

  private def asDouble(value: JsValue): Option[Double] = value match {
    case JsNumber(num) => Some(num.toDouble)
    case JsString(s) => Try(s.toDouble).toOption
    case _ => None
  }

  def parse(payload: Array[Byte]): Option[Double] = {
    val text = new String(payload, StandardCharsets.UTF_8).trim
    if (text.isEmpty) return None

    if (text.head == '{' || text.head == '[' || text.head == '"') {
      Try(text.parseJson).toOption.flatMap {
        case JsObject(fields) =>
          candidateFields.view.flatMap(key => fields.get(key).flatMap(asDouble)).headOption
        case JsArray(values) if values.nonEmpty =>
          asDouble(values.head)
        case JsNumber(num) => Some(num.toDouble)
        case JsString(s) => Try(s.toDouble).toOption
        case _ => None
      }
    } else {
      Try(text.toDouble).toOption
    }
  }
}

final class SumCountAggregate extends AggregateFunction[SensorReading, SumCount, SumCount] {
  override def createAccumulator(): SumCount = SumCount(0.0, 0L)
  override def add(value: SensorReading, accumulator: SumCount): SumCount =
    SumCount(accumulator.sum + value.value, accumulator.count + 1)
  override def getResult(accumulator: SumCount): SumCount = accumulator
  override def merge(a: SumCount, b: SumCount): SumCount = SumCount(a.sum + b.sum, a.count + b.count)
}

trait WindowAggregatorJobSpec extends JobSpec {
  protected def jobName: String
  protected def inputTopicEnv: String
  protected def outputTopicEnv: String
  protected def groupIdEnv: String
  protected def defaultGroupId: String

  override val name: String = jobName

  override def register(env: StreamExecutionEnvironment): Unit = {
    val bootstrapServers = Env.get("FLINK_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val inputTopic = Env.get(inputTopicEnv, "rawframes")
    val outputTopic = Env.get(outputTopicEnv, "env-features")
    val groupId = Env.get(groupIdEnv, defaultGroupId)
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

    val readings = env
      .fromSource(source, watermark, "kafka-source")
      .name("kafka-source")
      .filter(new FilterFunction[ParsedEnvelope] {
        override def filter(value: ParsedEnvelope): Boolean = value.eventType == "RawFrame"
      })
      .flatMap(new FlatMapFunction[ParsedEnvelope, SensorReading] {
        override def flatMap(value: ParsedEnvelope, out: Collector[SensorReading]): Unit = {
          PayloadValueParser.parse(value.payload).foreach { parsed =>
            val ts = if (value.sensorTimestampMs > 0) value.sensorTimestampMs else value.ingestTimestampMs
            out.collect(SensorReading(value.deviceId, value.sensorType, ts, parsed))
          }
        }
      })
      .returns(TypeInformation.of(classOf[SensorReading]))

    val keyed = readings.keyBy(new KeySelector[SensorReading, SensorKey] {
      override def getKey(value: SensorReading): SensorKey = SensorKey(value.deviceId, value.sensorType)
    })

    val windows = List(1, 5)
    windows.foreach { minutes =>
      val windowSizeMs = minutes.toLong * 60L * 1000L
      val aggregated = keyed
        .window(TumblingEventTimeWindows.of(Duration.ofMinutes(minutes.toLong)))
        .aggregate(new SumCountAggregate, new WindowFeatureProcessFunction(windowSizeMs))
        .name(s"window-avg-${minutes}m")

      val sink = KafkaSink.builder[WindowFeature]()
        .setBootstrapServers(bootstrapServers)
        .setRecordSerializer(
          new KafkaRecordSerializationSchema[WindowFeature] {
            override def serialize(
              element: WindowFeature,
              context: KafkaSinkContext,
              timestamp: java.lang.Long
            ): org.apache.kafka.clients.producer.ProducerRecord[Array[Byte], Array[Byte]] =
              new org.apache.kafka.clients.producer.ProducerRecord(
                outputTopic,
                s"${element.deviceId}:${element.sensorType}:${element.windowStartMs}".getBytes(StandardCharsets.UTF_8),
                new WindowFeatureSerializer().serialize(element)
              )
          }
        )
        .build()

      aggregated.sinkTo(sink).name(s"window-avg-${minutes}m-sink")
    }
  }
}

object EnvAggregatorJobSpec extends WindowAggregatorJobSpec {
  override protected val jobName: String = "env-aggregator"
  override protected val inputTopicEnv: String = "FLINK_ENV_INPUT_TOPIC"
  override protected val outputTopicEnv: String = "FLINK_ENV_FEATURES_TOPIC"
  override protected val groupIdEnv: String = "FLINK_ENV_GROUP_ID"
  override protected val defaultGroupId: String = "flink-env-aggregator"
}

object PowerAggregatorJobSpec extends WindowAggregatorJobSpec {
  override protected val jobName: String = "power-aggregator"
  override protected val inputTopicEnv: String = "FLINK_POWER_INPUT_TOPIC"
  override protected val outputTopicEnv: String = "FLINK_POWER_FEATURES_TOPIC"
  override protected val groupIdEnv: String = "FLINK_POWER_GROUP_ID"
  override protected val defaultGroupId: String = "flink-power-aggregator"
}
