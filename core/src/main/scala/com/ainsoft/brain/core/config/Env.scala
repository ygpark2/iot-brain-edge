package com.ainsoft.brain.core.config

object Env {
  @volatile private var overrides: Map[String, String] = Map.empty

  def loadYaml(path: String): Unit = {
    overrides = YamlConfig.load(path)
  }

  def get(name: String, default: String): String =
    overrides.get(name)
      .orElse(Option(System.getenv(name)).filter(_.nonEmpty))
      .getOrElse(default)

  def getOpt(name: String): Option[String] =
    overrides.get(name)
      .orElse(Option(System.getenv(name)).filter(_.nonEmpty))
}
