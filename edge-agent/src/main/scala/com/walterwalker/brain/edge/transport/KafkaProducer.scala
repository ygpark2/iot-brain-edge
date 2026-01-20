package com.walterwalker.brain.edge.transport

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.kafka.scaladsl.SendProducer

import scala.concurrent.{ExecutionContext, Future}

final class KafkaProducer(
                           bootstrapServers: String,
                           topic: String,
                           clientId: String = "edge-agent",
                           acks: String = "all"
                         )(using system: ActorSystem[?]) extends Producer {

  // ExecutionContext를 implicit으로 제공
  private given ExecutionContext = system.executionContext

  // Kafka 프로듀서 설정
  private val settings: ProducerSettings[String, String] =
    ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServers)
      .withProperty("client.id", clientId)
      .withProperty("acks", acks)
      .withProperty("enable.idempotence", "true")
      .withProperty("retries", Integer.toString(Integer.MAX_VALUE))
      .withProperty("linger.ms", "5")

  private val producer: SendProducer[String, String] =
    SendProducer(settings)

  // 메시지 전송
  override def send(line: String)(using ec: ExecutionContext): Future[Boolean] = {
    val key = extractEventId(line)
    val rec = new ProducerRecord[String, String](topic, key, line)
    producer.send(rec).map(_ => true).recover(_ => false)
  }

  override def close(): Unit = {
    producer.close()
  }

  // event_id 추출
  private def extractEventId(jsonLine: String): String | Null = {
    val needle = "\"event_id\":\""
    val i = jsonLine.indexOf(needle)
    if i < 0 then null
    else {
      val start = i + needle.length
      val end = jsonLine.indexOf('"', start)
      if end < 0 then null else jsonLine.substring(start, end)
    }
  }
}