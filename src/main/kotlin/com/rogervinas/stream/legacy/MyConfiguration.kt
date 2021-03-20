package com.rogervinas.stream.legacy

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.domain.MyEventProducer
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.context.annotation.Bean

@EnableBinding(value = [MyProducerBinding::class, MyConsumerBinding::class])
class MyConfiguration {

    @Bean
    fun myStreamEventProducer(binding: MyProducerBinding): MyEventProducer {
        return MyStreamEventProducer(binding.myProducer())
    }

    @Bean
    fun myStreamEventConsumer(consumer: MyEventConsumer): MyStreamEventConsumer {
        return MyStreamEventConsumer(consumer)
    }

    @Bean
    fun myEventConsumer(): MyEventConsumer {
        return object : MyEventConsumer {
            override fun consume(event: MyEvent) {
                println("Received ${event.text}")
            }
        }
    }
}