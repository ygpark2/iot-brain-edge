package com.ainsoft.brain.flink.jobs

import com.ainsoft.brain.flink.util.Env
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

object JobRunner {
  def main(args: Array[String]): Unit = {
    val yamlPathOpt = sys.env.get("FLINK_CONFIG_YAML")
    yamlPathOpt.foreach(Env.loadYaml)

    val jobName = args.headOption.getOrElse(Env.get("FLINK_JOB", "sessionizer"))
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val spec = JobRegistry.get(jobName).getOrElse {
      throw new IllegalArgumentException(s"Unknown FLINK_JOB: $jobName")
    }

    spec.register(env)
    env.execute(s"Flink Job: ${spec.name}")
  }
}
