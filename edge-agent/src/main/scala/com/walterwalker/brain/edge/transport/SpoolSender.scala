package com.walterwalker.brain.edge.transport

import com.walterwalker.brain.edge.spool.DiskSpool
import org.apache.pekko.actor.typed.ActorSystem

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

object SpoolSender:

  final case class Config(
    minBackoff: FiniteDuration = 200.millis,
    maxBackoff: FiniteDuration = 3.seconds,
    logEveryN: Long = 50L,
    logEvery: FiniteDuration = 5.seconds
  )

  /** Starts background loop: read from spool cursor -> producer.send -> commitCursor on success */
  def start(spool: DiskSpool, producer: Producer, cfg: Config)
           (using system: ActorSystem[?], ec: ExecutionContext): Unit =

    var backoff = cfg.minBackoff
    val ok = new AtomicLong(0L)
    val fail = new AtomicLong(0L)

    system.scheduler.scheduleAtFixedRate(cfg.logEvery, cfg.logEvery)(() => {
      val size = Files.size(spool.dir.resolve("spool.ndjson"))
      system.log.info(
        "SpoolSender stats: ok={} fail={} cursor={} spoolSizeBytes={}",
        ok.get(): java.lang.Long,
        fail.get(): java.lang.Long,
        spool.currentCursorOffset(): java.lang.Long,
        size: java.lang.Long
      )
    })

    def loop(): Unit =
      spool.readNextFromCursor() match
        case None =>
          backoff = cfg.minBackoff
          system.scheduler.scheduleOnce(200.millis, () => loop())
        case Some((line, nextOffset)) =>
          producer.send(line).onComplete {
            case scala.util.Success(true) =>
              spool.commitCursor(nextOffset)
              val n = ok.incrementAndGet()
              if n % cfg.logEveryN == 0 then
                system.log.info("SpoolSender progress: ok={} cursor={}", n: java.lang.Long, spool.currentCursorOffset(): java.lang.Long)
              backoff = cfg.minBackoff
              loop()

            case scala.util.Success(false) =>
              fail.incrementAndGet()
              scheduleRetry()

            case scala.util.Failure(_) =>
              fail.incrementAndGet()
              scheduleRetry()
          }

    def scheduleRetry(): Unit =
      val d = backoff
      backoff = (backoff * 2).min(cfg.maxBackoff)
      system.scheduler.scheduleOnce(d, () => loop())

    system.log.info("SpoolSender started cursor={}", spool.currentCursorOffset(): java.lang.Long)
    loop()
