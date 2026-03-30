package com.ainsoft.brain.flink.jobs.aggregate

import com.ainsoft.brain.core.events.WindowFeature
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import org.mockito.Mockito.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import java.util.Collections

class WindowFeatureProcessFunctionSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  "WindowFeatureProcessFunction" should "calculate mean and collect WindowFeature" in {
    val windowSizeMs = 60000L
    val function = new WindowFeatureProcessFunction(windowSizeMs)

    val key = SensorKey("device1", "env")
    val context = mock[function.Context]
    val window = mock[TimeWindow]
    val out = mock[Collector[WindowFeature]]

    when(window.getStart).thenReturn(1000L)
    when(window.getEnd).thenReturn(2000L)
    when(context.window).thenReturn(window)

    val sumCount = SumCount(100.0, 5L)
    val elements = Collections.singletonList(sumCount)

    function.process(key, context, elements, out)

    val expectedMean = 100.0 / 5.0
    verify(out).collect(
      WindowFeature(
        deviceId = "device1",
        sensorType = "env",
        windowStartMs = 1000L,
        windowEndMs = 2000L,
        windowSizeMs = windowSizeMs,
        count = 5L,
        meanValue = expectedMean,
        featureSchemaVersion = "v1"
      )
    )
  }

  it should "handle zero count" in {
    val windowSizeMs = 60000L
    val function = new WindowFeatureProcessFunction(windowSizeMs)

    val key = SensorKey("device1", "env")
    val context = mock[function.Context]
    val window = mock[TimeWindow]
    val out = mock[Collector[WindowFeature]]

    when(window.getStart).thenReturn(1000L)
    when(window.getEnd).thenReturn(2000L)
    when(context.window).thenReturn(window)

    val sumCount = SumCount(0.0, 0L)
    val elements = Collections.singletonList(sumCount)

    function.process(key, context, elements, out)

    verify(out).collect(
      WindowFeature(
        deviceId = "device1",
        sensorType = "env",
        windowStartMs = 1000L,
        windowEndMs = 2000L,
        windowSizeMs = windowSizeMs,
        count = 0L,
        meanValue = 0.0,
        featureSchemaVersion = "v1"
      )
    )
  }
}
