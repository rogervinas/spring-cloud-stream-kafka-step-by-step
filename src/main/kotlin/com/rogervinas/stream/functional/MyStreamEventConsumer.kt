package com.rogervinas.stream.functional

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.shared.MyEventPayload

class MyStreamEventConsumer(private val consumer: MyEventConsumer) : (MyEventPayload) -> Unit {
  override fun invoke(payload: MyEventPayload) {
    consumer.consume(fromPayload(payload))
  }

  private fun fromPayload(payload: MyEventPayload): MyEvent {
    return MyEvent(payload.string)
  }
}
