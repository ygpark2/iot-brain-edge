package com.walterwalker.brain.ingest

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

    // ClickHouse writer
    val chCfg = ClickHouseWriter.fromEnv()
    val chWriter = new ClickHouseWriter(system, http, chCfg)

    // Kafka consumer (Kafka -> ClickHouseWriter.enqueueAck -> commit)
    val kafkaCfg = KafkaConsumerCfg.fromEnv()
    val kafkaConsumer = new KafkaConsumer(system, kafkaCfg, chWriter)
    val kafkaKsOpt = kafkaConsumer.start()

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

              // ✅ 운영급: ClickHouse 적재 성공 후에만 accepted=true
              val ev = IngestEvent.fromPayload("ingest-http", body)

              onComplete(chWriter.enqueueAck(ev)) {
                case Success(_) =>
                  complete("""{"accepted":true}""")
                case Failure(e) =>
                  system.log.error("HTTP ingest -> ClickHouse failed: {}", e.getMessage)
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

    // graceful shutdown order:
    // 1) stop kafka stream
    // 2) flush ClickHouse writer
    // 3) unbind http
    // 4) terminate actor system
    system.log.info("Shutting down (kafka -> clickhouse -> http)...")

    kafkaKsOpt.foreach(_.shutdown())
    kafkaConsumer.shutdownDlq()

    val shutdownF: Future[Unit] =
      chWriter.shutdown().recover { case ex =>
        system.log.error("ClickHouseWriter shutdown failed: {}", ex.getMessage)
      }

    shutdownF.onComplete { _ =>
      bindingF.flatMap(_.unbind()).onComplete(_ => system.terminate())
    }
  }
}
