package com.ainsoft.brain.flink.jobs.aggregate

import com.ainsoft.brain.core.events.WindowFeature
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

final class WindowFeatureProcessFunction(windowSizeMs: Long)
    extends ProcessWindowFunction[SumCount, WindowFeature, SensorKey, TimeWindow] {

  override def process(
      key: SensorKey,
      context: ProcessWindowFunction[SumCount, WindowFeature, SensorKey, TimeWindow]#Context,
      elements: java.lang.Iterable[SumCount],
      out: Collector[WindowFeature]
  ): Unit = {
    val it = elements.iterator()
    if (it.hasNext) {
      val agg = it.next()
      val mean = if (agg.count == 0L) 0.0 else agg.sum / agg.count.toDouble
      out.collect(
        WindowFeature(
          deviceId = key.deviceId,
          sensorType = key.sensorType,
          windowStartMs = context.window.getStart,
          windowEndMs = context.window.getEnd,
          windowSizeMs = windowSizeMs,
          count = agg.count,
          meanValue = mean,
          featureSchemaVersion = "v1"
        )
      )
    }
  }
}
