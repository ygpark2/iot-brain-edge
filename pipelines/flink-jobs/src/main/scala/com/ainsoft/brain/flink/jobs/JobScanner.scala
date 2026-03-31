package com.ainsoft.brain.flink.jobs

import io.github.classgraph.ClassGraph
import java.util.{ArrayList, List => JList}

object JobScanner {
  // 반환 타입도 Java List를 고려하거나, 최상위에서만 Seq로 변환
  def scan(): Seq[JobSpec] = {
    val scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages("com.ainsoft.brain.flink.jobs")
      .scan()

    val javaList = new ArrayList[JobSpec]()
    
    try {
      val classList = scanResult.getClassesImplementing(classOf[JobSpec].getName)
      val it = classList.iterator()
      while (it.hasNext) {
        val classInfo = it.next()
        val cls = classInfo.loadClass()
        try {
          val module = cls.getField("MODULE$").get(null).asInstanceOf[JobSpec]
          if (module != null) {
            javaList.add(module)
          }
        } catch {
          case _: NoSuchFieldException => // Ignore non-objects
          case _: Exception => // Log error if needed
        }
      }
      
      // 마지막에만 Scala Seq로 변환 (이 시점은 이미 스칼라 3 런타임이 안정화된 후임)
      val result = new scala.collection.mutable.ArrayBuffer[JobSpec](javaList.size())
      val resIt = javaList.iterator()
      while(resIt.hasNext) {
        result += resIt.next()
      }
      result.toSeq
    } finally {
      scanResult.close()
    }
  }
}
