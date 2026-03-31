package com.ainsoft.brain.ingest

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

/**
 * ClickHouseWriter with batching, buffering, and backpressure.
 * Implements ack-after-write semantics.
 */
class ClickHouseWriter(
    chUrl: String,
    chUser: String,
    chPass: String,
    maxBufferSize: Int = 1000,
    flushIntervalMs: Long = 5000
)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext
  private val http = Http(system.toClassic)

  private case class PendingEvent(payload: String, promise: Promise[Done], table: String)
  private val buffer = ListBuffer.empty[PendingEvent]

  // Periodically flush the buffer
  private val scheduler = system.scheduler
  private val flushTask = scheduler.scheduleWithFixedDelay(
    scala.concurrent.duration.Duration.Zero,
    scala.concurrent.duration.Duration(flushIntervalMs, "ms")
  )(() => flushAll())

  /**
   * Enqueue an event for writing to ClickHouse.
   * Returns a Future that completes when the event is successfully written (Ack-after-write).
   */
  def enqueueAck(payload: String, table: String): Future[Done] = synchronized {
    if (buffer.size >= maxBufferSize) {
      // Backpressure: reject new events if buffer is full
      Future.failed(new RuntimeException(s"ClickHouseWriter buffer full (max=$maxBufferSize)"))
    } else {
      val promise = Promise[Done]()
      buffer.append(PendingEvent(payload, promise, table))
      
      // Immediate flush if buffer is full
      if (buffer.size >= maxBufferSize) {
        Future { flushAll() }
      }
      
      promise.future
    }
  }

  def flushAll(): Unit = {
    val toFlush = synchronized {
      if (buffer.isEmpty) return
      val temp = buffer.toList
      buffer.clear()
      temp
    }

    // Group by table and send batches
    toFlush.groupBy(_.table).foreach { case (table, events) =>
      sendBatch(table, events)
    }
  }

  private def sendBatch(table: String, events: List[PendingEvent]): Unit = {
    val batchPayload = events.map(_.payload).mkString("\n")
    val query = s"INSERT INTO $table FORMAT JSONEachRow"
    
    val baseUri = Uri(chUrl)
    val requestUri = baseUri.withQuery(Uri.Query("query" -> query))

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = requestUri,
      entity = HttpEntity(ContentTypes.`application/json`, ByteString(batchPayload)),
      headers = List(Authorization(BasicHttpCredentials(chUser, chPass)))
    )

    http.singleRequest(request).onComplete {
      case Success(res) if res.status.isSuccess() =>
        res.discardEntityBytes()
        events.foreach(_.promise.success(Done))
      case Success(res) =>
        res.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String).onComplete {
          case Success(body) =>
            val err = s"ClickHouse write failed (HTTP ${res.status}): $body"
            events.foreach(_.promise.failure(new Exception(err)))
          case Failure(e) =>
            events.foreach(_.promise.failure(e))
        }
      case Failure(e) =>
        events.foreach(_.promise.failure(e))
    }
  }

  def close(): Unit = {
    flushTask.cancel()
    flushAll()
  }
}
