package com.walterwalker.brain.ingest

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import org.apache.pekko.kafka.scaladsl.{Consumer, Producer}
import org.apache.pekko.stream.scaladsl.{Keep, RestartSource, Sink, Source}
import org.apache.pekko.stream.{KillSwitches, Materializer, OverflowStrategy, UniqueKillSwitch}
import org.apache.pekko.{Done, NotUsed}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer, ByteArrayDeserializer}
import spray.json.*

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}
import org.apache.pekko.kafka.ConsumerMessage


final class KafkaConsumer(
                           system: ActorSystem[?],
                           cfg: KafkaConsumerCfg,
                           chWriter: ClickHouseWriter
                         ) {
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val mat: Materializer = Materializer(system)

  private val dlqProducerSettings =
    ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers(cfg.bootstrapServers)

  // ✅ DLQ producer stream (reusable)
  private val (dlqQueue, dlqDone) = {
    Source
      .queue[ProducerRecord[String, String]](bufferSize = 1024, OverflowStrategy.backpressure)
      .toMat(Producer.plainSink(dlqProducerSettings))(Keep.both)
      .run()
  }

  private def sendDlq(rec: ProducerRecord[String, String]): Future[Unit] =
    dlqQueue.offer(rec).flatMap {
      case org.apache.pekko.stream.QueueOfferResult.Enqueued => Future.successful(())
      case other => Future.failed(new RuntimeException(s"DLQ enqueue failed: $other"))
    }

  private def isPermanent(ex: Throwable): Boolean = {
    val msg = Option(ex.getMessage).getOrElse("")
    msg.contains("HTTP 400") ||
      msg.contains("HTTP 401") ||
      msg.contains("HTTP 403") ||
      msg.contains("HTTP 404") ||
      msg.contains("HTTP 409")
  }

  private def dlqRecord(ev: IngestEvent, err: Throwable, topic: String, partition: Int, offset: Long): ProducerRecord[String, String] = {
    val dlqJson =
      JsObject(
        "event_id" -> JsString(ev.eventId),
        "source" -> JsString(ev.source),
        "payload" -> JsString(ev.payload),
        "error" -> JsString(Option(err.getMessage).getOrElse(err.getClass.getName)),
        "kafka" -> JsObject(
          "topic" -> JsString(topic),
          "partition" -> JsNumber(partition),
          "offset" -> JsNumber(offset)
        )
      ).compactPrint

    new ProducerRecord[String, String](cfg.dlqTopic, ev.eventId, dlqJson)
  }

  // ✅ transient backoff helper
  private def delayFail[A](d: FiniteDuration, ex: Throwable): Future[A] = {
    val p = Promise[A]()
    system.scheduler.scheduleOnce(d, new Runnable { override def run(): Unit = p.failure(ex) })(ec)
    p.future
  }

private def processOne(msg: ConsumerMessage.CommittableMessage[String, Array[Byte]]): Future[Unit] = {
    val bytes = msg.record.value()

    val ctHeader = Option(msg.record.headers.lastHeader("content-type"))
          .map(h => new String(h.value(), "UTF-8"))

    // ✅ protobuf + json/string 혼합 수용
    val ev = EventDecoder.decode(cfg.sourceTag, bytes, ctHeader)

    chWriter.enqueueAck(ev).flatMap { _ =>
      msg.committableOffset.commitScaladsl().map(_ => ())
    }.recoverWith { case e =>
      val t = msg.record.topic()
      val p = msg.record.partition()
      val o = msg.record.offset()

      if (isPermanent(e)) {
        system.log.error("permanent failure -> DLQ + commit topic={} partition={} offset={}", t, p: java.lang.Integer, o: java.lang.Long, e)
        val rec = dlqRecord(ev, e, t, p, o)

        sendDlq(rec)
          .flatMap(_ => msg.committableOffset.commitScaladsl())
          .map(_ => ())
      } else {
        // ✅ transient: 스트림을 바로 죽이지 말고, 짧게 backoff 후 실패 → RestartSource가 처리
        system.log.error("transient failure -> backoff then restart topic={} partition={} offset={}", t, p: java.lang.Integer, o: java.lang.Long, e)
        delayFail[Unit](1.second, e)
      }
    }
}

  /** enabled=false일 수 있으니 Option으로 반환 */
  def start(): Option[UniqueKillSwitch] = {
    if (!cfg.enabled) {
      system.log.info("Kafka consumer disabled (KAFKA_CONSUMER_ENABLED=false)")
      return None
    }

    val consumerSettings =
      ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
        .withBootstrapServers(cfg.bootstrapServers)
        .withGroupId(cfg.groupId)
        .withProperty("auto.offset.reset", "earliest")
        .withProperty("enable.auto.commit", "false")

    system.log.info(
      "Kafka consumer starting (restartable): bootstrapServers={} topic={} groupId={} dlqTopic={}",
      cfg.bootstrapServers, cfg.topic, cfg.groupId, cfg.dlqTopic
    )

    val restartable =
      RestartSource.withBackoff(
        minBackoff = cfg.restartMinBackoff,
        maxBackoff = cfg.restartMaxBackoff,
        randomFactor = cfg.restartRandomFactor
      ) { () =>
        system.log.info("Kafka consumer (re)starting stream...")
        Consumer.committableSource(consumerSettings, Subscriptions.topics(cfg.topic))
      }

    val (ks, done) =
      restartable
        .viaMat(KillSwitches.single)(Keep.right)
        .mapAsync(cfg.parallelism)(processOne)
        .toMat(Sink.ignore)(Keep.both)
        .run()

    done.onComplete {
      case Success(_) => system.log.info("Kafka consumer stream completed")
      case Failure(e) => system.log.error("Kafka consumer stream failed", e)
    }

    Some(ks)
  }

  def shutdownDlq(): Unit = {
    dlqQueue.complete()
  }
}
