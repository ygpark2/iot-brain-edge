package com.ainsoft.brain.alert

import com.ainsoft.brain.core.events.{EventJsonProtocol, InferenceAlert}
import com.ainsoft.brain.core.kafka.PekkoKafkaSupport
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.kafka.ConsumerSettings
import org.apache.pekko.kafka.ConsumerMessage
import org.apache.pekko.stream.{OverflowStrategy, UniqueKillSwitch}
import org.apache.kafka.common.serialization.StringDeserializer
import spray.json.*

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Main {
  final case class AlertConsumerCfg(
    enabled: Boolean,
    bootstrapServers: String,
    topic: String,
    groupId: String,
    dlqTopic: String,
    parallelism: Int,
    restartMinBackoff: FiniteDuration,
    restartMaxBackoff: FiniteDuration,
    restartRandomFactor: Double,
    webhookEnabled: Boolean,
    webhookUrl: String,
    webhookTimeoutMs: Int
  )

  object AlertConsumerCfg {
    def fromEnv(): AlertConsumerCfg = {
      def env(name: String, default: String): String = sys.env.getOrElse(name, default)

      def envBool(name: String, default: Boolean): Boolean =
        sys.env
          .get(name)
          .map(_.trim.toLowerCase)
          .flatMap {
            case "true" | "1" | "yes" | "y"  => Some(true)
            case "false" | "0" | "no" | "n" => Some(false)
            case _ => None
          }
          .getOrElse(default)

      def envInt(name: String, default: Int): Int =
        sys.env.get(name).flatMap(s => scala.util.Try(s.trim.toInt).toOption).getOrElse(default)

      def envDouble(name: String, default: Double): Double =
        sys.env.get(name).flatMap(s => scala.util.Try(s.trim.toDouble).toOption).getOrElse(default)

      AlertConsumerCfg(
        enabled = envBool("INFERENCE_ALERTS_ENABLED", true),
        bootstrapServers = env("INFERENCE_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
        topic = env("INFERENCE_ALERTS_TOPIC", "inference-alerts"),
        groupId = env("INFERENCE_ALERTS_GROUP_ID", "alert-service"),
        dlqTopic = env("INFERENCE_ALERTS_DLQ_TOPIC", "inference-alerts-dlq"),
        parallelism = envInt("INFERENCE_ALERTS_PARALLELISM", 2),
        restartMinBackoff = FiniteDuration(envInt("INFERENCE_ALERTS_RESTART_MIN_BACKOFF_MS", 1000).toLong, MILLISECONDS),
        restartMaxBackoff = FiniteDuration(envInt("INFERENCE_ALERTS_RESTART_MAX_BACKOFF_MS", 30000).toLong, MILLISECONDS),
        restartRandomFactor = envDouble("INFERENCE_ALERTS_RESTART_RANDOM_FACTOR", 0.2),
        webhookEnabled = envBool("ALERT_WEBHOOK_ENABLED", false),
        webhookUrl = env("ALERT_WEBHOOK_URL", ""),
        webhookTimeoutMs = envInt("ALERT_WEBHOOK_TIMEOUT_MS", 3000)
      )
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem(org.apache.pekko.actor.typed.scaladsl.Behaviors.empty, "alert-service")
    implicit val ec: ExecutionContext = system.executionContext

    val cfg = AlertConsumerCfg.fromEnv()
    if (!cfg.enabled) {
      system.log.info("alert-service disabled (INFERENCE_ALERTS_ENABLED=false)")
      system.terminate()
      return
    }

    val http = Http()

    def postWebhook(alert: InferenceAlert): Future[Unit] = {
      if (!cfg.webhookEnabled || cfg.webhookUrl.trim.isEmpty) return Future.successful(())
      import EventJsonProtocol.*
      val json = alert.toJson.compactPrint
      val entity = org.apache.pekko.http.scaladsl.model.HttpEntity(
        org.apache.pekko.http.scaladsl.model.ContentTypes.`application/json`,
        json
      )
      val req = org.apache.pekko.http.scaladsl.model.HttpRequest(
        method = org.apache.pekko.http.scaladsl.model.HttpMethods.POST,
        uri = cfg.webhookUrl,
        entity = entity
      )
      http.singleRequest(req).map { resp =>
        resp.discardEntityBytes()
        ()
      }
    }

    val consumerSettings =
      ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
        .withBootstrapServers(cfg.bootstrapServers)
        .withGroupId(cfg.groupId)
        .withProperty("auto.offset.reset", "earliest")
        .withProperty("enable.auto.commit", "false")

    val (ks, done) =
      PekkoKafkaSupport.startCommittableConsumer(
        system,
        consumerSettings,
        cfg.topic,
        cfg.parallelism,
        cfg.restartMinBackoff,
        cfg.restartMaxBackoff,
        cfg.restartRandomFactor
      ) { msg =>
        val raw = msg.record.value()
        val alert = raw.parseJson.convertTo[InferenceAlert](using EventJsonProtocol.alertFormat)

        postWebhook(alert).flatMap { _ =>
          msg.committableOffset.commitScaladsl().map(_ => ())
        }.recoverWith { case e =>
          val t = msg.record.topic()
          val p = msg.record.partition()
          val o = msg.record.offset()

          if (PekkoKafkaSupport.isPermanentHttpError(e)) {
            val rec = PekkoKafkaSupport.dlqRecord(raw, e, t, p, o, cfg.dlqTopic)
            val dlqProducer = PekkoKafkaSupport.dlqProducer(system, cfg.bootstrapServers)
            org.apache.pekko.kafka.scaladsl.Producer.plainSink(dlqProducer).runWith(
              org.apache.pekko.stream.scaladsl.Source.single(rec)
            ).flatMap(_ => msg.committableOffset.commitScaladsl()).map(_ => ())
          } else {
            PekkoKafkaSupport.delayFail[Unit](system, 1.second, e)
          }
        }
      }

    done.onComplete {
      case Success(_) => system.log.info("alert-service stream completed")
      case Failure(ex) => system.log.error("alert-service stream failed", ex)
    }

    system.log.info("alert-service started")
    scala.io.StdIn.readLine()
    ks.shutdown()
    system.terminate()
  }
}
