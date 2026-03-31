package com.ainsoft.brain.edge

import com.ainsoft.brain.edge.spool.DiskSpool
import com.ainsoft.brain.edge.transport.{HttpProducer, KafkaProducer, Producer, SpoolSender}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import java.nio.file.Paths
import java.util.UUID

object Main:
  private final case class RuntimeConfig(
    producerKind: String,
    ingestUrl: String,
    kafkaBootstrapServers: String,
    kafkaTopic: String,
    spoolDir: java.nio.file.Path,
    deviceId: String,
    sessionId: String,
    sensorType: String,
    hz: Int
  )

  private object RuntimeConfig:
    def load(): RuntimeConfig =
      val sessionIdDefault = s"sess-${UUID.randomUUID().toString.take(8)}"
      val requestedProducerKind = sys.env.getOrElse("PRODUCER_KIND", "http").trim.toLowerCase
      val producerKind =
        requestedProducerKind match
          case "http" | "kafka" => requestedProducerKind
          case other =>
            System.err.println(s"[edge-agent] Unknown PRODUCER_KIND=$other, falling back to http")
            "http"

      RuntimeConfig(
        producerKind = producerKind,
        ingestUrl = sys.env.getOrElse("INGEST_URL", "http://127.0.0.1:8082/v1/ingest"),
        kafkaBootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9094"),
        kafkaTopic = sys.env.getOrElse("KAFKA_TOPIC", "rawframes"),
        spoolDir = Paths.get(sys.env.getOrElse("EDGE_SPOOL_DIR", ".data/edge-agent-spool")),
        deviceId = sys.env.getOrElse("EDGE_DEVICE_ID", "dev-mock-001"),
        sessionId = sys.env.getOrElse("EDGE_SESSION_ID", sessionIdDefault),
        sensorType = sys.env.getOrElse("EDGE_SENSOR_TYPE", "GRF"),
        hz = sys.env.get("EDGE_MOCK_HZ").flatMap(_.toIntOption).filter(_ > 0).getOrElse(2)
      )

  def main(args: Array[String]): Unit =
    val cfg = RuntimeConfig.load()
    val system: ActorSystem[EdgeSupervisor.Command] =
      ActorSystem(EdgeSupervisor(), "edge-agent")

    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext
    given Materializer = Materializer(system)

    system.log.info("edge-agent started")

    system ! EdgeSupervisor.Start

    val spool = new DiskSpool(cfg.spoolDir)
    val producer = buildProducer(cfg)
    SpoolSender.start(spool, producer, SpoolSender.Config())

    val interval = Math.max(1L, (1000.0 / cfg.hz).toLong).millis
    val mockStream =
      Source
        .tick(0.seconds, interval, ())
        .map { _ =>
          val line = buildMockEventJson(cfg)
          spool.appendLine(line)
        }
        .toMat(Sink.ignore)(Keep.left)
        .run()

    system.log.info(
      "Mock data stream initialized mode={} deviceId={} sessionId={} hz={} spoolDir={}",
      cfg.producerKind,
      cfg.deviceId,
      cfg.sessionId,
      cfg.hz: java.lang.Integer,
      cfg.spoolDir.toAbsolutePath.toString
    )

    sys.addShutdownHook {
      mockStream.cancel()
      producer.close()
      system.terminate()
    }

    system.log.info("edge-agent is running. Use SIGTERM/Ctrl+C to stop.")

    while (true) {
      Thread.sleep(10000)
    }

  private def buildProducer(cfg: RuntimeConfig)(using system: ActorSystem[?]): Producer =
    cfg.producerKind match
      case "kafka" =>
        system.log.info(
          "Using Kafka producer bootstrapServers={} topic={}",
          cfg.kafkaBootstrapServers,
          cfg.kafkaTopic
        )
        new KafkaProducer(cfg.kafkaBootstrapServers, cfg.kafkaTopic)
      case _ =>
        system.log.info("Using HTTP producer ingestUrl={}", cfg.ingestUrl)
        new HttpProducer(cfg.ingestUrl)

  private def buildMockEventJson(cfg: RuntimeConfig): String =
    val now = System.currentTimeMillis()
    val eventId = UUID.randomUUID().toString
    val payloadB64 = java.util.Base64.getEncoder.encodeToString("edge-demo-payload".getBytes("UTF-8"))
    s"""{"event_id":"$eventId","schema_version":"v1","event_type":"RawFrame","device_id":"${cfg.deviceId}","sensor_type":"${cfg.sensorType}","session_id":"${cfg.sessionId}","sensor_timestamp_ms":$now,"ingest_timestamp_ms":$now,"payload_b64":"$payloadB64"}"""
