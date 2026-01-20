package com.walterwalker.brain.edge.transport

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

final class HttpProducer(url: String)(using system: ActorSystem[?]) extends Producer:
  private val http = Http()

  override def send(line: String)(using ec: ExecutionContext): Future[Boolean] =
    val entity = HttpEntity(ContentTypes.`application/json`, ByteString(line))
    val req = HttpRequest(method = HttpMethods.POST, uri = url, entity = entity)
    http.singleRequest(req).map(_.status.isSuccess())

  override def close(): Unit =
    () // keep it simple for now
