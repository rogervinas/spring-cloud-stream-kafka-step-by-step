package com.rogervinas.stream.functional

import com.fasterxml.jackson.databind.ObjectMapper
import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.shared.MyEventPayload
import org.springframework.cloud.function.json.JacksonMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.messaging.Message
import reactor.core.publisher.Flux

@Configuration
class MyConfiguration {
  @Bean
  fun myEventConsumer() =
    object : MyEventConsumer {
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
  fun myStreamEventProducerFunction(producer: MyStreamEventProducer): () -> Flux<Message<MyEventPayload>> = producer

  // Workaround for https://github.com/spring-cloud/spring-cloud-function/issues/1158
  // Introduced in spring-cloud-function 4.1.3 via spring-cloud-dependencies 2023.0.3
  @Bean @Primary
  fun jacksonMapper(objectMapper: ObjectMapper) = JacksonMapper(objectMapper)
}
