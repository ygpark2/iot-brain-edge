package com.ainsoft.brain.edge

import com.ainsoft.brain.edge.spool.DiskSpool
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import com.ainsoft.brain.edge.transport.{HttpProducer, KafkaProducer, Producer, SpoolSender}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.io.StdIn
import java.nio.file.Paths
import java.util.UUID

object Main:

  def main(args: Array[String]): Unit =
    val system: ActorSystem[EdgeSupervisor.Command] =
      ActorSystem(EdgeSupervisor(), "edge-agent")

    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext
    given Materializer = Materializer(system)

    system.log.info("edge-agent started")

    system ! EdgeSupervisor.Start

    system.log.info("Press ENTER to stop edge-agent...")
    StdIn.readLine()

    system.terminate()
