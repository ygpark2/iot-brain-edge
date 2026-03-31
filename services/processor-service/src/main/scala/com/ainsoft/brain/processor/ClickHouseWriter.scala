package com.ainsoft.brain.processor

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers._
import org.apache.pekko.util.ByteString

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class ClickHouseWriter(
    chUrl: String, chUser: String, chPass: String,
    maxBufferSize: Int = 100, flushIntervalMs: Long = 2000
)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext
  private val http = Http(system.toClassic)
  private case class PendingEvent(payload: String, promise: Promise[Done], table: String)
  private val buffer = ListBuffer.empty[PendingEvent]

  private val flushTask = system.scheduler.scheduleWithFixedDelay(
    scala.concurrent.duration.Duration.Zero,
    scala.concurrent.duration.Duration(flushIntervalMs, "ms")
  )(() => flushAll())(system.executionContext)

  def enqueueAck(payload: String, table: String): Future[Done] = synchronized {
    val promise = Promise[Done]()
    buffer.append(PendingEvent(payload, promise, table))
    if (buffer.size >= maxBufferSize) Future { flushAll() }
    promise.future
  }

  def flushAll(): Unit = {
    val toFlush = synchronized {
      if (buffer.isEmpty) return
      val temp = buffer.toList
      buffer.clear()
      temp
    }
    toFlush.groupBy(_.table).foreach { case (table, events) => sendBatch(table, events) }
  }

  private def sendBatch(table: String, events: List[PendingEvent]): Unit = {
    val batchPayload = events.map(_.payload).mkString("\n")
    val query = s"INSERT INTO $table FORMAT JSONEachRow"
    val requestUri = Uri(chUrl).withQuery(Uri.Query("query" -> query))

    val request = HttpRequest(
      method = HttpMethods.POST, uri = requestUri,
      entity = HttpEntity(ContentTypes.`application/json`, ByteString(batchPayload)),
      headers = List(Authorization(BasicHttpCredentials(chUser, chPass)))
    )

    http.singleRequest(request).onComplete {
      case Success(res) if res.status.isSuccess() =>
        res.discardEntityBytes()
        events.foreach(_.promise.success(Done))
      case Success(res) =>
        events.foreach(_.promise.failure(new Exception(s"CH Fail ${res.status}")))
      case Failure(e) =>
        events.foreach(_.promise.failure(e))
    }
  }

  def close(): Unit = {
    flushTask.cancel()
    flushAll()
  }
}
