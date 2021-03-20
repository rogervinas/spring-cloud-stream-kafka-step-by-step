package com.rogervinas.stream.functional

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventProducer
import com.rogervinas.stream.shared.MyEventPayload
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST
import java.util.function.Supplier

class MyStreamEventProducer : Supplier<Flux<Message<MyEventPayload>>>, MyEventProducer {

    val sink = Sinks.many().unicast().onBackpressureBuffer<Message<MyEventPayload>>()

    override fun produce(event: MyEvent) {
        val message = MessageBuilder
                .withPayload(toPayload(event))
                .setHeader(KafkaHeaders.MESSAGE_KEY, toKey(event))
                .build()
        sink.emitNext(message, FAIL_FAST)
    }

    override fun get(): Flux<Message<MyEventPayload>> {
        return sink.asFlux()
    }

    private fun toPayload(event: MyEvent): MyEventPayload {
        return MyEventPayload(event.text, event.text.length)
    }

    private fun toKey(event: MyEvent): String {
        return "key-${event.text.length}"
    }
}