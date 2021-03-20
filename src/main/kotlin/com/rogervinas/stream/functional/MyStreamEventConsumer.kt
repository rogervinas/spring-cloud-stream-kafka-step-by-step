package com.rogervinas.stream.functional

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.shared.MyEventPayload
import java.util.function.Consumer

class MyStreamEventConsumer(private val consumer: MyEventConsumer) : Consumer<MyEventPayload> {

    override fun accept(payload: MyEventPayload) {
        consumer.consume(fromPayload(payload))
    }

    private fun fromPayload(payload: MyEventPayload): MyEvent {
        return MyEvent(payload.string)
    }
}