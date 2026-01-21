package com.ainsoft.brain.core.kafka

import org.apache.pekko.kafka.ConsumerMessage
import org.apache.pekko.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import org.apache.pekko.kafka.scaladsl.{Consumer, Producer}
import org.apache.pekko.stream.scaladsl.{Keep, RestartSource, Sink, Source}
import org.apache.pekko.stream.{KillSwitches, Materializer, UniqueKillSwitch}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import spray.json.*

import scala.concurrent.duration.*
import scala.concurrent.{Future, Promise}

object PekkoKafkaSupport {
  def dlqProducer(system: ActorSystem[?], bootstrapServers: String): ProducerSettings[String, String] =
    ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServers)

  def startCommittableConsumer(
    system: ActorSystem[?],
    settings: ConsumerSettings[String, String],
    subscriptionTopic: String,
    parallelism: Int,
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    randomFactor: Double
  )(
    handler: ConsumerMessage.CommittableMessage[String, String] => Future[Unit]
  ): (UniqueKillSwitch, Future[Done]) = {
    implicit val mat: Materializer = Materializer(system)

    val restartable =
      RestartSource.withBackoff(
        minBackoff = minBackoff,
        maxBackoff = maxBackoff,
        randomFactor = randomFactor
      ) { () =>
        Consumer.committableSource(settings, Subscriptions.topics(subscriptionTopic))
      }

    restartable
      .viaMat(KillSwitches.single)(Keep.right)
      .mapAsync(parallelism)(handler)
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  def dlqRecord(raw: String, err: Throwable, topic: String, partition: Int, offset: Long, dlqTopic: String): ProducerRecord[String, String] = {
    val dlqJson =
      JsObject(
        "error" -> JsString(Option(err.getMessage).getOrElse(err.getClass.getName)),
        "kafka" -> JsObject(
          "topic" -> JsString(topic),
          "partition" -> JsNumber(partition),
          "offset" -> JsNumber(offset)
        ),
        "payload" -> JsString(raw)
      ).compactPrint

    new ProducerRecord[String, String](dlqTopic, null, dlqJson)
  }

  def delayFail[A](system: ActorSystem[?], d: FiniteDuration, ex: Throwable): Future[A] = {
    val p = Promise[A]()
    system.scheduler.scheduleOnce(d, new Runnable { override def run(): Unit = p.failure(ex) })(system.executionContext)
    p.future
  }

  def isPermanentHttpError(ex: Throwable): Boolean = {
    val msg = Option(ex.getMessage).getOrElse("")
    msg.contains("HTTP 400") || msg.contains("HTTP 401") || msg.contains("HTTP 403") || msg.contains("HTTP 404") || msg.contains("HTTP 409")
  }
}
