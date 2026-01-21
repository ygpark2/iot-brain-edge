package com.ainsoft.brain.edge.adapters

import com.ainsoft.brain.core.Envelope

final class MqttAdapter extends IngestAdapter {
  override def start(onEnvelope: Envelope => Unit): Unit = {
    // TODO: implement MQTT adapter
  }
}
