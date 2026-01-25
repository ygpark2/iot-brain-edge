package com.ainsoft.brain.flink.jobs.aggregate;

import com.ainsoft.brain.core.events.WindowFeature;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.lang.Iterable;

public final class WindowFeatureProcessFunction
    extends ProcessWindowFunction<SumCount, WindowFeature, SensorKey, TimeWindow> {

  private final long windowSizeMs;

  public WindowFeatureProcessFunction(long windowSizeMs) {
    this.windowSizeMs = windowSizeMs;
  }

  @Override
  public void process(
      SensorKey key,
      Context context,
      Iterable<SumCount> elements,
      Collector<WindowFeature> out
  ) {
    SumCount agg = elements.iterator().next();
    double mean = agg.count() == 0L ? 0.0 : agg.sum() / (double) agg.count();
    out.collect(
        new WindowFeature(
            key.deviceId(),
            key.sensorType(),
            context.window().getStart(),
            context.window().getEnd(),
            windowSizeMs,
            agg.count(),
            mean,
            "v1"
        )
    );
  }
}
