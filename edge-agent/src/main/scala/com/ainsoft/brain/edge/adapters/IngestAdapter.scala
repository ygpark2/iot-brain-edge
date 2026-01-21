package com.ainsoft.brain.edge.adapters

import com.ainsoft.brain.core.Envelope

trait IngestAdapter {
  def start(onEnvelope: Envelope => Unit): Unit
  def stop(): Unit = ()
}
