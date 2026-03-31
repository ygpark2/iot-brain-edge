package com.ainsoft.brain.flink.jobs

import java.util.concurrent.ConcurrentHashMap

object JobRegistry {
  // Scala Map 초기화 중 발생할 수 있는 호환성 에러를 피하기 위해 Java Map 사용
  private val specsMap = new ConcurrentHashMap[String, JobSpec]()
  private var initialized = false

  private def initialize(): Unit = synchronized {
    if (!initialized) {
      val foundSpecs = JobScanner.scan()
      val it = foundSpecs.iterator
      while(it.hasNext) {
        val s = it.next()
        specsMap.put(s.name, s)
      }
      initialized = true
    }
  }

  def get(name: String): Option[JobSpec] = {
    if (!initialized) initialize()
    val res = specsMap.get(name)
    if (res == null) None else Some(res)
  }
}
