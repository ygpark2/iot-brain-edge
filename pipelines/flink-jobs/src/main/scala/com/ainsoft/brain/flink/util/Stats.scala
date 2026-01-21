package com.ainsoft.brain.flink.util

final case class PayloadStats(count: Long, sum: Long, sumSquares: Long, peak: Int, mean: Double, contactCount: Long)

object PayloadStats {
  def fromBytes(bytes: Array[Byte]): PayloadStats = {
    if (bytes.isEmpty) return PayloadStats(0L, 0L, 0L, 0, 0.0, 0L)
    var c = 0L
    var s = 0L
    var ss = 0L
    var p = 0
    var contact = 0L
    var i = 0
    while (i < bytes.length) {
      val v = bytes(i) & 0xff
      c += 1
      s += v
      ss += v.toLong * v.toLong
      if (v > p) p = v
      if (v > 0) contact += 1
      i += 1
    }
    val mean = if (c == 0) 0.0 else s.toDouble / c
    PayloadStats(c, s, ss, p, mean, contact)
  }
}

final case class SessionAggregate(
  deviceId: String,
  sessionId: String,
  sensorType: String,
  startTsMs: Long,
  endTsMs: Long,
  frameCount: Long,
  sampleCount: Long,
  sum: Long,
  sumSquares: Long,
  peak: Int,
  firstMean: Double,
  firstTsMs: Long,
  lastMean: Double,
  lastTsMs: Long,
  contactCount: Long
)
