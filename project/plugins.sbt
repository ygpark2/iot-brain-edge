
// project/protoc.sbt  (권장)
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.8")

// ScalaPB code generator (빌드용)
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.20"

// Fat jar for Flink submit
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.0")
