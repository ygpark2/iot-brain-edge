package com.ainsoft.brain.ingest

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.model.StatusCodes
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}

object Main extends DefaultJsonProtocol {

  final case class Health(status: String)
  implicit val healthFormat: RootJsonFormat[Health] = jsonFormat1(Health.apply)

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem(org.apache.pekko.actor.typed.scaladsl.Behaviors.empty, "ingestion-service")
    implicit val ec: ExecutionContext = system.executionContext

    val http = Http()

    val kafkaBootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val kafkaTopic = sys.env.getOrElse("KAFKA_TOPIC", "rawframes")
    val kafkaPublisher = new KafkaPublisher(kafkaBootstrap, kafkaTopic)

    val route =
      path("health") {
        get {
          complete(Health("ok").toJson.compactPrint)
        }
      } ~
        path("v1" / "ingest") {
          post {
            entity(as[String]) { body =>
              system.log.info("ingest received bytes={}", body.length: java.lang.Integer)

              onComplete(kafkaPublisher.send(body)) {
                case Success(_) =>
                  complete("""{"accepted":true}""")
                case Failure(e) =>
                  system.log.error("HTTP ingest -> Kafka failed: {}", e.getMessage)
                  complete(StatusCodes.InternalServerError, """{"accepted":false}""")
              }
            }
          }
        }

    val bindingF =
      http.newServerAt("0.0.0.0", 8080).bind(route)

    bindingF.onComplete {
      case Success(binding) =>
        system.log.info("ingestion-service listening on {}", binding.localAddress)
      case Failure(ex) =>
        system.log.error("failed to bind HTTP endpoint", ex)
        system.terminate()
    }

    system.log.info("Press ENTER to stop ingestion-service...")
    StdIn.readLine()

    system.log.info("Shutting down (kafka -> http)...")

    val shutdownF: Future[Unit] =
      Future {
        kafkaPublisher.close()
      }

    shutdownF.onComplete { _ =>
      bindingF.flatMap(_.unbind()).onComplete(_ => system.terminate())
    }
  }
}
