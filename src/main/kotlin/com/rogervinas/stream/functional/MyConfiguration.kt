package com.rogervinas.stream.functional

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.domain.MyEventProducer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MyConfiguration {

    @Bean("my-producer")
    fun myStreamEventProducer(): MyEventProducer {
        return MyStreamEventProducer()
    }

    @Bean("my-consumer")
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