package com.ainsoft.brain.core.config

final case class KafkaConsumerConfig(
  enabled: Boolean,
  bootstrapServers: String,
  topic: String,
  groupId: String,
  dlqTopic: String,
  parallelism: Int,
  restartMinBackoffMs: Int,
  restartMaxBackoffMs: Int,
  restartRandomFactor: Double
)
