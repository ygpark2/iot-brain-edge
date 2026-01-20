ThisBuild / scalaVersion := "3.7.4"
ThisBuild / organization := "com.walterwalker"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val Versions = new {
  val pekko = "1.1.2"
  val pekkoHttp = "1.1.0"
  val pekkoKafka = "1.1.0"
  val logback = "1.5.25"
  val slf4j = "2.0.17"
  val scalapb = "0.11.20"
}

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature"),
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % Versions.slf4j,
    "ch.qos.logback" % "logback-classic" % Versions.logback,
    "org.apache.pekko" %% "pekko-slf4j" % Versions.pekko
  )
)

lazy val root = (project in file("."))
  .aggregate(core, edgeAgent, ingestionService)
  .settings(commonSettings)
  .settings(name := "brain-edget")

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "core",
    libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % Versions.scalapb % "protobuf",
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
      "org.apache.pekko" %% "pekko-slf4j" % Versions.pekko,
      "org.apache.pekko" %% "pekko-connectors-kafka" % Versions.pekkoKafka,
      "org.slf4j" % "slf4j-api" % Versions.slf4j,
      "ch.qos.logback" % "logback-classic" % Versions.logback
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

addCommandAlias("run-edge", ";project edgeAgent;run")
addCommandAlias("run-ingest", ";project ingestionService;run")


lazy val sensorSim = (project in file("tools/sensor-sim"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "sensor-sim",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-actor-typed" % Versions.pekko,
      "org.apache.pekko" %% "pekko-stream" % Versions.pekko,
      "org.apache.pekko" %% "pekko-http" % Versions.pekkoHttp,
      "ch.qos.logback" % "logback-classic" % Versions.logback,
    )
  )

addCommandAlias("run-sim", ";project sensorSim;run")
