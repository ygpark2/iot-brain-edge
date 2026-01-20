package com.walterwalker.brain.ingest

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.HttpExt
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import spray.json.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

final class ClickHouseWriter(
                              system: ActorSystem[?],
                              http: HttpExt,
                              cfg: ClickHouseWriter.Config
                            ) {

  private given ActorSystem[?] = system
  private given ExecutionContext = system.executionContext

  private final case class Pending(line: String, ack: Promise[Unit])

  private var buffer = Vector.empty[Pending]
  private var flushing = false

  private val tick =
    system.scheduler.scheduleAtFixedRate(
      initialDelay = cfg.flushEvery,
      interval = cfg.flushEvery
    )(() => flushIfNeeded(force = true))

  def enqueue(payload: String, source: String = cfg.defaultSource): Unit =
    enqueueAck(IngestEvent.fromPayload(source, payload)); ()

  /** 운영급: ClickHouse에 실제 적재 성공하면 Future 성공 */
  def enqueueAck(ev: IngestEvent): Future[Unit] = synchronized {
    if (buffer.size >= cfg.maxBuffer) {
      val ex = new RuntimeException(
        s"ClickHouseWriter buffer overflow: size=${buffer.size} maxBuffer=${cfg.maxBuffer}"
      )
      // overflow 정책: FAIL (자연 백프레셔)
      return Future.failed(ex)
    }

    val p = Promise[Unit]()
    val line = JsObject(
      "event_id" -> JsString(ev.eventId),
      "source" -> JsString(ev.source),
      "payload" -> JsString(ev.payload)
    ).compactPrint

    buffer :+= Pending(line, p)
    if (buffer.size >= cfg.batchSize) flushIfNeeded(force = true)
    p.future
  }

  private def flushIfNeeded(force: Boolean): Unit = {
    val batchOpt: Option[Vector[Pending]] = synchronized {
      if (flushing) None
      else if (buffer.isEmpty) None
      else if (!force && buffer.size < cfg.batchSize) None
      else {
        flushing = true
        val take = buffer.take(cfg.batchSize)
        buffer = buffer.drop(take.size)
        Some(take)
      }
    }

    batchOpt.foreach { batch =>
      insertBatch(batch.map(_.line)).onComplete {
        case Success(_) =>
          batch.foreach(_.ack.trySuccess(()))
          synchronized { flushing = false }

        case Failure(ex) =>
          batch.foreach(_.ack.tryFailure(ex))
          synchronized {
            buffer = batch ++ buffer
            flushing = false
          }
          system.log.error("ClickHouse insert failed (will retry on next flush): {}", ex.getMessage)
      }
    }
  }

  def shutdown(): Future[Unit] = {
    tick.cancel()
    insertAllRemaining()
  }

  private def insertAllRemaining(): Future[Unit] = {
    def loop(): Future[Unit] = {
      val batch: Vector[Pending] = synchronized {
        if (buffer.isEmpty) Vector.empty
        else {
          val take = buffer.take(cfg.batchSize)
          buffer = buffer.drop(take.size)
          take
        }
      }
      if (batch.isEmpty) Future.successful(())
      else
        insertBatch(batch.map(_.line)).transformWith {
          case Success(_) =>
            batch.foreach(_.ack.trySuccess(()))
            loop()
          case Failure(e) =>
            batch.foreach(_.ack.tryFailure(e))
            Future.failed(e)
        }
    }
    loop()
  }

  private def insertBatch(lines: Vector[String]): Future[Unit] = {
    val query = s"INSERT INTO ${cfg.db}.${cfg.table} (event_id, source, payload) FORMAT JSONEachRow"
    val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
    val uri = s"${cfg.httpUrl}/?query=$encoded"

    val entity = HttpEntity(ContentTypes.`application/json`, lines.mkString("\n") + "\n")

    val req0 = HttpRequest(method = HttpMethods.POST, uri = uri, entity = entity)
    val req =
      (cfg.user, cfg.password) match {
        case (Some(u), Some(p)) =>
          req0.addHeader(Authorization(BasicHttpCredentials(u, p)))
        case _ => req0
      }

    http.singleRequest(req).flatMap { resp =>
      // 운영급: 에러 바디까지 읽어 원인 추적
      resp.entity.dataBytes.runFold(new StringBuilder)((sb, bs) => sb.append(bs.utf8String)).flatMap { sb =>
        resp.discardEntityBytes()
        if (resp.status.isSuccess()) Future.successful(())
        else Future.failed(new RuntimeException(s"ClickHouse HTTP ${resp.status.intValue()} body=${sb.toString}"))
      }
    }
  }
}

object ClickHouseWriter {

  final case class Config(
                           httpUrl: String,
                           db: String,
                           table: String,
                           user: Option[String],
                           password: Option[String],
                           batchSize: Int,
                           flushEvery: FiniteDuration,
                           maxBuffer: Int,
                           defaultSource: String
                         )

  def fromEnv()(using system: ActorSystem[?]): Config = {
    def env(k: String): Option[String] = Option(System.getenv(k)).filter(_.nonEmpty)

    val httpUrl = env("CLICKHOUSE_HTTP_URL").getOrElse("http://localhost:8123")
    val db = env("CLICKHOUSE_DB").getOrElse("brain")
    val table = env("CLICKHOUSE_TABLE").getOrElse("rawframes")
    val user = env("CLICKHOUSE_USER")
    val password = env("CLICKHOUSE_PASSWORD")

    val batchSize = env("CLICKHOUSE_BATCH_SIZE").flatMap(_.toIntOption).getOrElse(200)
    val flushMs = env("CLICKHOUSE_FLUSH_MS").flatMap(_.toLongOption).getOrElse(500L)
    val maxBuffer = env("CLICKHOUSE_MAX_BUFFER").flatMap(_.toIntOption).getOrElse(20000)
    val defaultSource = env("CLICKHOUSE_DEFAULT_SOURCE").getOrElse("ingest-http")

    Config(
      httpUrl = httpUrl,
      db = db,
      table = table,
      user = user,
      password = password,
      batchSize = batchSize,
      flushEvery = flushMs.millis,
      maxBuffer = maxBuffer,
      defaultSource = defaultSource
    )
  }
}
