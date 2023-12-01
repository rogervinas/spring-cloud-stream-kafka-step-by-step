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

class MyStreamEventProducer : () -> Flux<Message<MyEventPayload>>, MyEventProducer {

  private val sink = Sinks.many().unicast().onBackpressureBuffer<Message<MyEventPayload>>()

  override fun produce(event: MyEvent) {
    val message = MessageBuilder
      .withPayload(toPayload(event))
      .setHeader(KafkaHeaders.KEY, toKey(event))
      .build()
    sink.emitNext(message, FAIL_FAST)
  }

  override fun invoke() = sink.asFlux()

  private fun toPayload(event: MyEvent) = MyEventPayload(event.text, event.text.length)

  private fun toKey(event: MyEvent) = "key-${event.text.length}"
}
