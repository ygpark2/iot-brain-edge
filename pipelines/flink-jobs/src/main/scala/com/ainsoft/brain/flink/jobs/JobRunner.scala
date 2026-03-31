package com.ainsoft.brain.flink.jobs

import com.ainsoft.brain.flink.util.Env
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

object JobRunner {
  def main(args: Array[String]): Unit = {
    // Scala 2/3 간의 바이너리 호환성 이슈를 피하기 위해 
    // args 조작 시 암시적 변환(headOption 등)을 피하고 인덱스로 직접 접근합니다.
    val yamlPath = System.getenv("FLINK_CONFIG_YAML")
    if (yamlPath != null && yamlPath.nonEmpty) {
      Env.loadYaml(yamlPath)
    }

    val jobName = if (args != null && args.length > 0) {
      args(0)
    } else {
      val envJob = Env.get("FLINK_JOB", "sessionizer")
      if (envJob == null) "sessionizer" else envJob
    }

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    JobRegistry.get(jobName) match {
      case Some(spec) =>
        spec.register(env)
        env.execute(s"Flink Job: ${spec.name}")
      case None =>
        throw new IllegalArgumentException(s"Unknown FLINK_JOB: $jobName")
    }
  }
}
