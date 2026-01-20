package com.walterwalker.brain.edge

import com.walterwalker.brain.edge.spool.DiskSpool
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import com.walterwalker.brain.edge.transport.{HttpProducer, KafkaProducer, Producer, SpoolSender}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.io.StdIn
import java.nio.file.Paths
import java.util.UUID

object Main:

  def main(args: Array[String]): Unit =
    val system: ActorSystem[EdgeGuardian.Command] =
      ActorSystem(EdgeGuardian(), "edge-agent")

    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext
    given Materializer = Materializer(system)

    system.log.info("edge-agent started (spool+resend enabled)")

    // 1) Typed actor demo path (optional)
    system ! EdgeGuardian.Connect("demo-1", "session-1")

    // 2) Disk spool
    val spoolDir = Paths.get(".data", "spool")
    val spool = new DiskSpool(spoolDir)
    // 환경변수에서 PRODUCER_KIND 값을 읽어 Producer 결정
    val kind = sys.env.getOrElse("PRODUCER_KIND", "http").toLowerCase
    val producer: Producer = kind match
      case "kafka" =>
        val bs = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9094")
        val topic = sys.env.getOrElse("KAFKA_TOPIC", "rawframes")
        system.log.info(s"Using KafkaProducer bootstrapServers=$bs topic=$topic")
        new KafkaProducer(bs, topic)
      case _ =>
        val ingestUrl = sys.env.getOrElse("INGEST_URL", "http://127.0.0.1:8080/v1/ingest")
        system.log.info(s"Using HttpProducer ingestUrl=$ingestUrl")
        new HttpProducer(ingestUrl)

    // 3) Start sender loop (retries forever)
    SpoolSender.start(
      spool,
      producer,
      SpoolSender.Config()
    )

    // 4) Mock generator: append events to spool at 5Hz
    val interval: FiniteDuration = 200.millis
    val genCancellable =
      Source
        .tick(0.seconds, interval, ())
        .map { _ =>
          val now = System.currentTimeMillis()
          val eventId = UUID.randomUUID().toString
          val payloadB64 = java.util.Base64.getEncoder.encodeToString("edge-demo-payload".getBytes("UTF-8"))
          s"""{"event_id":"$eventId","schema_version":"v1","event_type":"RawFrame","device_id":"demo-1","sensor_type":"GRF","session_id":"session-1","sensor_timestamp_ms":$now,"ingest_timestamp_ms":$now,"payload_b64":"$payloadB64"}"""
        }
        .toMat(Sink.foreach { json =>
          spool.appendLine(json)
        })(Keep.left)
        .run()

    system.log.info("Press ENTER to stop edge-agent...")
    StdIn.readLine()

    genCancellable.cancel()
    system.terminate()
