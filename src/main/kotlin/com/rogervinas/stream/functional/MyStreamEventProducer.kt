package com.rogervinas.stream.functional

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventProducer
import com.rogervinas.stream.shared.MyEventPayload
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST
import java.util.function.Supplier

class MyStreamEventProducer : Supplier<Flux<MyEventPayload>>, MyEventProducer {

    val sink = Sinks.many().unicast().onBackpressureBuffer<MyEventPayload>()

    override fun produce(event: MyEvent) {
        sink.emitNext(toPayload(event), FAIL_FAST)
    }

    override fun get(): Flux<MyEventPayload> {
        return sink.asFlux()
    }

    private fun toPayload(event: MyEvent): MyEventPayload {
        return MyEventPayload(event.text, event.text.length)
    }
}