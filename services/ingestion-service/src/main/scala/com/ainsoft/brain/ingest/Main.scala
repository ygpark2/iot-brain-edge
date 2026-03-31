package com.ainsoft.brain.ingest

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.model.StatusCodes
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class Health(status: String)
object Health {
  implicit val format: RootJsonFormat[Health] = jsonFormat1(Health.apply)
}

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(org.apache.pekko.actor.typed.scaladsl.Behaviors.empty, "ingestion-service")
    implicit val ec = system.executionContext
    val http = Http()

    // Environment variables with Docker-friendly defaults
    val kafkaBootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
    val kafkaTopic = sys.env.getOrElse("KAFKA_TOPIC", "rawframes")
    val dlqTopic = sys.env.getOrElse("KAFKA_DLQ_TOPIC", "ingest-dlq")
    
    val chUrl = sys.env.getOrElse("CLICKHOUSE_HTTP_URL", "http://clickhouse:8123")
    val chUser = sys.env.getOrElse("CLICKHOUSE_USER", "brain")
    val chPass = sys.env.getOrElse("CLICKHOUSE_PASSWORD", "brain")

    val kafkaPublisher = new KafkaPublisher(kafkaBootstrap, kafkaTopic)
    val chWriter = new ClickHouseWriter(chUrl, chUser, chPass)
    val dlqProducer = new DLQProducer(kafkaBootstrap)

    val kafkaConsumer = new KafkaIngestConsumer(
      kafkaBootstrap,
      "ingestion-service-group",
      Set("rawframes-processed", "session-features", "inference-results", "inference-alerts", "env-features", "power-features"),
      chWriter,
      dlqProducer,
      dlqTopic
    )
    val killSwitch = kafkaConsumer.run()

    val route =
      path("health") {
        get {
          complete(Health("ok").toJson.compactPrint)
        }
      } ~
        path("v1" / "ingest") {
          post {
            entity(as[String]) { body =>
              // Revert to Kafka Publish (Production Path)
              onComplete(kafkaPublisher.send(body)) {
                case Success(_) =>
                  complete("""{"accepted":true}""")
                case Failure(e) =>
                  system.log.error("HTTP ingest -> Kafka failed: {}", e.getMessage)
                  complete(StatusCodes.InternalServerError, s"""{"accepted":false, "error":"${e.getMessage}"}""")
              }
            }
          }
        }

    val bindingF = http.newServerAt("0.0.0.0", 8082).bind(route)

    bindingF.onComplete {
      case Success(binding) =>
        system.log.info("ingestion-service listening on {}", binding.localAddress)
      case Failure(ex) =>
        system.log.error("failed to bind HTTP endpoint", ex)
        system.terminate()
    }

    sys.addShutdownHook {
      killSwitch.shutdown()
      chWriter.close()
      kafkaPublisher.close()
      dlqProducer.close()
      bindingF.flatMap(_.unbind()).onComplete(_ => system.terminate())
    }
  }
}
