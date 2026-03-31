package com.ainsoft.brain.processor

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.kafka.scaladsl.{Committer, Consumer}
import org.apache.pekko.kafka.{CommitterSettings, ConsumerSettings, Subscriptions}
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import scala.concurrent.ExecutionContext

object Main {
  def main(args: Array[String]): Unit = {
    ActorSystem(Behaviors.setup[Unit] { ctx =>
      implicit val system = ctx.system
      implicit val ec: ExecutionContext = system.executionContext

      val bootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
      val inputTopic = sys.env.getOrElse("KAFKA_INPUT_TOPIC", "rawframes")
      
      val chUrl = sys.env.getOrElse("CLICKHOUSE_HTTP_URL", "http://clickhouse:8123")
      val chUser = sys.env.getOrElse("CLICKHOUSE_USER", "brain")
      val chPass = sys.env.getOrElse("CLICKHOUSE_PASSWORD", "brain")

      val chWriter = new ClickHouseWriter(chUrl, chUser, chPass)

      ctx.log.info("Starting Processor Service. Kafka: {}, Ingesting to ClickHouse: {}", bootstrapServers, chUrl)

      val consumerSettings = ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
        .withBootstrapServers(bootstrapServers)
        .withGroupId("processor-final-group")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

      val committerSettings = CommitterSettings(system)

      Consumer
        .committableSource(consumerSettings, Subscriptions.topics(inputTopic))
        .mapAsync(4) { msg =>
          val payload = new String(msg.record.value(), "UTF-8")
          // ClickHouse 저장 후 오프셋 커밋 (At-least-once 보장)
          chWriter.enqueueAck(payload, "brain.rawframes").map(_ => msg.committableOffset)
        }
        .toMat(Committer.sink(committerSettings))(Keep.both)
        .run()

      Behaviors.empty
    }, "processor-service")
  }
}
