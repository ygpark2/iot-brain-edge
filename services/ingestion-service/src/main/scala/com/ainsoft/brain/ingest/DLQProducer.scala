package com.ainsoft.brain.ingest

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Persistent DLQ producer with structured error payload.
 */
class DLQProducer(bootstrapServers: String)(implicit ec: ExecutionContext) {

  private val props = new Properties()
  props.put("bootstrap.servers", bootstrapServers)
  props.put("key.serializer", classOf[StringSerializer].getName)
  props.put("value.serializer", classOf[StringSerializer].getName)
  props.put("acks", "all")
  props.put("retries", "3")

  private val producer = new KafkaProducer[String, String](props)

  def sendToDLQ(topic: String, payload: String, error: String, originalTopic: String): Future[Unit] = {
    val promise = Promise[Unit]()
    
    // Structured error payload
    val dlqPayload = s"""{
      "original_topic": "$originalTopic",
      "error": "${error.replace("\"", "\\\"")}",
      "timestamp": "${java.time.Instant.now()}",
      "payload": $payload
    }"""

    val record = new ProducerRecord[String, String](topic, dlqPayload)
    
    producer.send(record, (metadata, exception) => {
      if (exception != null) promise.failure(exception)
      else promise.success(())
    })

    promise.future
  }

  def close(): Unit = {
    producer.close()
  }
}
