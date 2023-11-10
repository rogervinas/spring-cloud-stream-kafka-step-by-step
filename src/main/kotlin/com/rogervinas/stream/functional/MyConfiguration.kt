package com.rogervinas.stream.functional

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.shared.MyEventPayload
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import reactor.core.publisher.Flux

@Configuration
class MyConfiguration {

  @Bean
  fun myEventConsumer() = object : MyEventConsumer {
    override fun consume(event: MyEvent) {
      println("Received ${event.text}")
    }
  }

  @Bean("my-consumer")
  fun myStreamEventConsumerFunction(consumer: MyEventConsumer): (MyEventPayload) -> Unit =
    MyStreamEventConsumer(consumer)

  @Bean
  fun myStreamEventProducer() = MyStreamEventProducer()

  @Bean("my-producer")
  fun myStreamEventProducerFunction(producer: MyStreamEventProducer): () -> Flux<Message<MyEventPayload>> =
    producer::get
}
