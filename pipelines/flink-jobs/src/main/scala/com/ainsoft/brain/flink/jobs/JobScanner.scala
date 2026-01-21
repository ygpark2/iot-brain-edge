package com.ainsoft.brain.flink.jobs

import io.github.classgraph.ClassGraph
import scala.jdk.CollectionConverters._

object JobScanner {
  def scan(): Seq[JobSpec] = {
    val scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages("com.ainsoft.brain.flink.jobs")
      .scan()

    try {
      scanResult.getClassesImplementing(classOf[JobSpec].getName).asScala.toSeq.flatMap { classInfo =>
        val cls = classInfo.loadClass()
        val module = cls.getField("MODULE$").get(null).asInstanceOf[JobSpec]
        Option(module)
      }
    } finally {
      scanResult.close()
    }
  }
}
