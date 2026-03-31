package com.ainsoft.brain.ingest

import org.apache.pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.scaladsl.{Committer, Consumer}
import org.apache.pekko.kafka.{CommitterSettings, ConsumerSettings, Subscriptions}
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.pekko.stream.{KillSwitches, UniqueKillSwitch}
import org.apache.pekko.Done

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * Kafka Consumer that reads processed events and writes to ClickHouse.
 * Implements at-least-once delivery with DLQ.
 */
class KafkaIngestConsumer(
    bootstrapServers: String,
    groupId: String,
    topics: Set[String],
    writer: ClickHouseWriter,
    dlqProducer: DLQProducer,
    dlqTopic: String
)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext

  private val consumerSettings = ConsumerSettings(system.toClassic, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServers)
    .withGroupId(groupId)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")

  private val committerSettings = CommitterSettings(system.toClassic)

  def run(): UniqueKillSwitch = {
    val (killSwitch, control) = Consumer
      .committableSource(consumerSettings, Subscriptions.topics(topics))
      .mapAsync(4) { msg =>
        val payload = msg.record.value()
        val topic = msg.record.topic()
        
        // Map topic to ClickHouse table
        val table = topic match {
          case "rawframes-processed" => "brain.rawframes"
          case "session-features" => "brain.session_features"
          case "inference-results" => "brain.inference_results"
          case "inference-alerts" => "brain.inference_alerts"
          case "env-features" => "brain.env_features"
          case "power-features" => "brain.power_features"
          case _ => "brain.rawframes"
        }

        // Write to ClickHouse with retry and DLQ
        retryWithDLQ(payload, table, topic).map(_ => msg.committableOffset)
      }
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Committer.sink(committerSettings))(Keep.both)
      .run()

    system.log.info(s"Started KafkaIngestConsumer for topics: ${topics.mkString(", ")}")
    killSwitch
  }

  private def retryWithDLQ(payload: String, table: String, originalTopic: String, attempts: Int = 3): Future[Done] = {
    writer.enqueueAck(payload, table).recoverWith {
      case e if attempts > 1 =>
        system.log.warn(s"ClickHouse write failed, retrying ($attempts left): ${e.getMessage}")
        org.apache.pekko.pattern.after(2.seconds, system.toClassic.scheduler)(retryWithDLQ(payload, table, originalTopic, attempts - 1))
      case e =>
        system.log.error(s"ClickHouse write permanently failed, sending to DLQ: ${e.getMessage}")
        dlqProducer.sendToDLQ(dlqTopic, payload, e.getMessage, originalTopic).map(_ => Done)
    }
  }
}
