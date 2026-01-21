package com.ainsoft.brain.flink.jobs.features

import com.ainsoft.brain.flink.io.{FeatureDeserializer, FeatureSerializer}
import com.ainsoft.brain.flink.model.FeatureEvent
import com.ainsoft.brain.flink.jobs.JobSpec
import com.ainsoft.brain.flink.util.Env
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.connector.kafka.sink.{KafkaRecordSerializationSchema, KafkaSink}
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema.KafkaSinkContext
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import java.nio.charset.StandardCharsets

object FeatureBaseJobSpec extends JobSpec {
  override val name: String = "feature-base"

  override def register(env: StreamExecutionEnvironment): Unit = {
    val bootstrapServers = Env.get("FLINK_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val inputTopic = Env.get("FLINK_FEATURES_TOPIC", "session-features")
    val outputTopic = Env.get("FLINK_FEATURES_BASE_TOPIC", "features-base")
    val groupId = Env.get("FLINK_FEATURES_GROUP_ID", "flink-feature-base")

    val source = KafkaSource.builder[FeatureEvent]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(inputTopic)
      .setGroupId(groupId)
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new FeatureDeserializer)
      .build()

    val sink = KafkaSink.builder[FeatureEvent]()
      .setBootstrapServers(bootstrapServers)
      .setRecordSerializer(
        new KafkaRecordSerializationSchema[FeatureEvent] {
          override def serialize(
            element: FeatureEvent,
            context: KafkaSinkContext,
            timestamp: java.lang.Long
          ): org.apache.kafka.clients.producer.ProducerRecord[Array[Byte], Array[Byte]] =
            new org.apache.kafka.clients.producer.ProducerRecord(
              outputTopic,
              s"${element.deviceId}:${element.sessionId}:${element.sensorType}".getBytes(StandardCharsets.UTF_8),
              new FeatureSerializer().serialize(element)
            )
        }
      )
      .build()

    env.fromSource(source, org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "feature-source")
      .sinkTo(sink)
      .name("feature-base-sink")
  }
}
