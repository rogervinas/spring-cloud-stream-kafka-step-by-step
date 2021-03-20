package com.rogervinas.stream.legacy

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.shared.MyEventPayload
import org.springframework.cloud.stream.annotation.StreamListener

class MyStreamEventConsumer(private val consumer: MyEventConsumer) {

    @StreamListener("my-consumer")
    fun consume(payload: MyEventPayload) {
        consumer.consume(fromPayload(payload))
    }

    private fun fromPayload(payload: MyEventPayload): MyEvent {
        return MyEvent(payload.string)
    }
}