package com.walterwalker.brain.ingest

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

final case class KafkaConsumerCfg(
                                   enabled: Boolean,
                                   bootstrapServers: String,
                                   topic: String,
                                   groupId: String,
                                   dlqTopic: String,
                                   sourceTag: String,
                                   parallelism: Int,
                                   restartMinBackoff: FiniteDuration,
                                   restartMaxBackoff: FiniteDuration,
                                   restartRandomFactor: Double
                                 )

object KafkaConsumerCfg {
  def fromEnv(): KafkaConsumerCfg = {
    def env(name: String, default: String): String = sys.env.getOrElse(name, default)

    def envBool(name: String, default: Boolean): Boolean =
      sys.env
        .get(name)
        .map(_.trim.toLowerCase)
        .flatMap {
          case "true" | "1" | "yes" | "y"  => Some(true)
          case "false" | "0" | "no" | "n" => Some(false)
          case _ => None
        }
        .getOrElse(default)

    def envInt(name: String, default: Int): Int =
      sys.env.get(name).flatMap(s => scala.util.Try(s.trim.toInt).toOption).getOrElse(default)

    def envDouble(name: String, default: Double): Double =
      sys.env.get(name).flatMap(s => scala.util.Try(s.trim.toDouble).toOption).getOrElse(default)

    KafkaConsumerCfg(
      enabled = envBool("KAFKA_CONSUMER_ENABLED", true),
      bootstrapServers = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
      topic = env("KAFKA_TOPIC", "brain-events"),
      groupId = env("KAFKA_GROUP_ID", "ingestion-service"),
      dlqTopic = env("KAFKA_DLQ_TOPIC", "brain-events-dlq"),
      sourceTag = env("KAFKA_SOURCE_TAG", "kafka"),
      parallelism = envInt("KAFKA_PARALLELISM", 8),
      restartMinBackoff = FiniteDuration(envInt("KAFKA_RESTART_MIN_BACKOFF_MS", 1000).toLong, MILLISECONDS),
      restartMaxBackoff = FiniteDuration(envInt("KAFKA_RESTART_MAX_BACKOFF_MS", 30000).toLong, MILLISECONDS),
      restartRandomFactor = envDouble("KAFKA_RESTART_RANDOM_FACTOR", 0.2)
    )
  }
}
