package com.ainsoft.brain.flink.jobs.inference

import com.ainsoft.brain.flink.io.{FeatureDeserializer, InferenceRequestSerializer}
import com.ainsoft.brain.flink.model.{FeatureEvent, InferenceRequest}
import com.ainsoft.brain.flink.jobs.JobSpec
import com.ainsoft.brain.flink.util.Env
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.connector.kafka.sink.{KafkaRecordSerializationSchema, KafkaSink}
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema.KafkaSinkContext
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import java.nio.charset.StandardCharsets

object InferenceTriggerJobSpec extends JobSpec {
  override val name: String = "inference-trigger"

  override def register(env: StreamExecutionEnvironment): Unit = {
    val bootstrapServers = Env.get("FLINK_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val inputTopic = Env.get("FLINK_FEATURES_HEALTH_TOPIC", "features-health")
    val outputInferenceTopic = Env.get("FLINK_INFERENCE_TOPIC", "inference-requests")
    val groupId = Env.get("FLINK_INFERENCE_GROUP_ID", "flink-inference-trigger")

    val source = KafkaSource.builder[FeatureEvent]()
      .setBootstrapServers(bootstrapServers)
      .setTopics(inputTopic)
      .setGroupId(groupId)
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new FeatureDeserializer)
      .build()

    val inferenceSink = KafkaSink.builder[InferenceRequest]()
      .setBootstrapServers(bootstrapServers)
      .setRecordSerializer(
        new KafkaRecordSerializationSchema[InferenceRequest] {
          override def serialize(
            element: InferenceRequest,
            context: KafkaSinkContext,
            timestamp: java.lang.Long
          ): org.apache.kafka.clients.producer.ProducerRecord[Array[Byte], Array[Byte]] =
            new org.apache.kafka.clients.producer.ProducerRecord(
              outputInferenceTopic,
              s"${element.deviceId}:${element.sessionId}:${element.sensorType}".getBytes(StandardCharsets.UTF_8),
              new InferenceRequestSerializer().serialize(element)
            )
        }
      )
      .build()

    val inferenceRequests = env
      .fromSource(source, org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "health-feature-source")
      .map { feature =>
        InferenceRequest(
          deviceId = feature.deviceId,
          sessionId = feature.sessionId,
          sensorType = feature.sensorType,
          startTsMs = feature.startTsMs,
          endTsMs = feature.endTsMs,
          featureSchemaVersion = feature.schemaVersion,
          features = List(
            feature.durationMs.toDouble,
            feature.peakPayloadBytes.toDouble,
            feature.meanPayloadBytes,
            feature.stdPayloadBytes,
            feature.impulsePayloadBytes,
            feature.count.toDouble,
            feature.contactRatio,
            feature.loadingRate
          )
        )
      }

    inferenceRequests.sinkTo(inferenceSink).name("inference-sink")
  }
}
