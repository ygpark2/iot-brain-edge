package com.ainsoft.brain.core.config

import org.yaml.snakeyaml.Yaml

import java.io.{File, FileInputStream}
import java.util.{Map => JMap}
import scala.jdk.CollectionConverters._

object YamlConfig {
  def load(path: String): Map[String, String] = {
    val file = new File(path)
    if (!file.exists()) return Map.empty

    val yaml = new Yaml()
    val input = new FileInputStream(file)
    try {
      val raw = yaml.load[Any](input)
      raw match {
        case m: JMap[_, _] =>
          m.asScala.collect { case (k: String, v) => k -> v.toString }.toMap
        case _ => Map.empty
      }
    } finally {
      input.close()
    }
  }
}
