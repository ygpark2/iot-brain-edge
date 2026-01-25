import sbtassembly.AssemblyPlugin.autoImport.*

ThisBuild / scalaVersion := "3.7.4"
ThisBuild / organization := "com.ainsoft"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val Versions = new {
  val pekko = "1.1.2"
  val pekkoHttp = "1.1.0"
  val pekkoKafka = "1.1.0"
  val logback = "1.5.25"
  val slf4j = "2.0.17"
  val scalapb = "0.11.20"
  val flink = "2.0.0"
  val flinkKafka = "4.0.1-2.0"
  val sprayJson = "1.3.6"
}

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-Wconf:src=target/scala-.*:silent"
  ),
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % Versions.slf4j,
    "ch.qos.logback" % "logback-classic" % Versions.logback,
    "org.apache.pekko" %% "pekko-slf4j" % Versions.pekko
  )
)

lazy val root = (project in file("."))
  .aggregate(core, edgeAgent, ingestionService, flinkJobs, alertService)
  .settings(commonSettings)
  .settings(name := "brain-edget")

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime" % Versions.scalapb % "protobuf",
      "io.spray" %% "spray-json" % Versions.sprayJson,
      "org.yaml" % "snakeyaml" % "2.2",
      "org.apache.pekko" %% "pekko-actor-typed" % Versions.pekko,
      "org.apache.pekko" %% "pekko-stream" % Versions.pekko,
      "org.apache.pekko" %% "pekko-connectors-kafka" % Versions.pekkoKafka
    ),
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )

lazy val edgeAgent = (project in file("edge-agent"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "edge-agent",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-actor-typed" % Versions.pekko,
      "org.apache.pekko" %% "pekko-stream" % Versions.pekko,
      "org.apache.pekko" %% "pekko-http" % Versions.pekkoHttp,
      "org.apache.pekko" %% "pekko-http-spray-json" % Versions.pekkoHttp,
      "org.apache.pekko" %% "pekko-connectors-kafka" % Versions.pekkoKafka
    )
  )

lazy val flinkJobs = (project in file("pipelines/flink-jobs"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "flink-jobs",
    run / fork := true,
    assembly / assemblyJarName := "flink-jobs-assembly.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case x => MergeStrategy.first
    },
    libraryDependencies ++= Seq(
      "org.apache.flink" % "flink-streaming-java" % Versions.flink,
      "org.apache.flink" % "flink-runtime" % Versions.flink,
      "org.apache.flink" % "flink-clients" % Versions.flink,
      "org.apache.flink" % "flink-connector-base" % Versions.flink,
      "org.apache.flink" % "flink-connector-kafka" % Versions.flinkKafka,
      "org.apache.flink" % "flink-json" % Versions.flink,
      "org.apache.flink" % "flink-runtime-web" % Versions.flink % "runtime",
      "com.thesamet.scalapb" %% "scalapb-runtime" % Versions.scalapb,
      "io.spray" %% "spray-json" % Versions.sprayJson,
      "io.github.classgraph" % "classgraph" % "4.8.174",
    )
  )

lazy val ingestionService = (project in file("services/ingestion-service"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "ingestion-service",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-actor-typed" % Versions.pekko,
      "org.apache.pekko" %% "pekko-stream" % Versions.pekko,
      "org.apache.pekko" %% "pekko-http" % Versions.pekkoHttp,
      "org.apache.pekko" %% "pekko-http-spray-json" % Versions.pekkoHttp,
      "org.apache.pekko" %% "pekko-connectors-kafka" % Versions.pekkoKafka,
    )
  )

lazy val alertService = (project in file("services/alert-service"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "alert-service",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-actor-typed" % Versions.pekko,
      "org.apache.pekko" %% "pekko-stream" % Versions.pekko,
      "org.apache.pekko" %% "pekko-http" % Versions.pekkoHttp,
      "org.apache.pekko" %% "pekko-http-spray-json" % Versions.pekkoHttp,
      "org.apache.pekko" %% "pekko-connectors-kafka" % Versions.pekkoKafka,
    )
  )

addCommandAlias("run-edge", ";project edgeAgent;run")
addCommandAlias("run-ingest", ";project ingestionService;run")
