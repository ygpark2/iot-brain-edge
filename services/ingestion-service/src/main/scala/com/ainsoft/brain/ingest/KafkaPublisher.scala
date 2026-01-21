package com.ainsoft.brain.ingest

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.kafka.scaladsl.SendProducer

import scala.concurrent.{ExecutionContext, Future}

final class KafkaPublisher(
  bootstrapServers: String,
  topic: String,
  clientId: String = "ingestion-service",
  acks: String = "all"
)(using system: ActorSystem[?]) {

  private given ExecutionContext = system.executionContext

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

  def send(line: String): Future[Unit] = {
    val key = extractEventId(line)
    val rec = new ProducerRecord[String, String](topic, key, line)
    producer.send(rec).map(_ => ())
  }

  def close(): Unit = {
    producer.close()
  }

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
