package com.ainsoft.brain.flink.util

import com.ainsoft.brain.core.config.Env as CoreEnv

object Env {
  def get(name: String, default: String): String = CoreEnv.get(name, default)
  def getOpt(name: String): Option[String] = CoreEnv.getOpt(name)
  def loadYaml(path: String): Unit = CoreEnv.loadYaml(path)
}
