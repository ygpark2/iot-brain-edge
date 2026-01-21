package com.ainsoft.brain.flink.jobs

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

trait JobSpec {
  def name: String
  def register(env: StreamExecutionEnvironment): Unit
}
