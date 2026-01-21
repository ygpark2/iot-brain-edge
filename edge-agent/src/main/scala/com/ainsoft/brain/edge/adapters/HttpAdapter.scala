package com.ainsoft.brain.edge.adapters

import com.ainsoft.brain.core.{Envelope, SensorType}

final class HttpAdapter extends IngestAdapter {
  override def start(onEnvelope: Envelope => Unit): Unit = {
    // TODO: implement HTTP server adapter
  }
}
