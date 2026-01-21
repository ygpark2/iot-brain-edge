package com.ainsoft.brain.edge.transport

import scala.concurrent.{ExecutionContext, Future}

trait Producer:
  /** Send one event line (NDJSON line).
    * Return true if the send is durably accepted (ok to commit cursor).
    * Return false to retry later.
    */
  def send(line: String)(using ec: ExecutionContext): Future[Boolean]

  /** Optional shutdown hook (close clients). */
  def close(): Unit = ()
