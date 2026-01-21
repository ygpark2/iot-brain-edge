package com.ainsoft.brain.flink.jobs


object JobRegistry {
  private lazy val specs: Map[String, JobSpec] =
    JobScanner.scan().map(spec => spec.name -> spec).toMap

  def get(name: String): Option[JobSpec] = specs.get(name)
}
