package com.rogervinas.stream.legacy

import org.springframework.cloud.stream.annotation.Input
import org.springframework.messaging.SubscribableChannel

interface MyConsumerBinding {
    @Input("my-consumer")
    fun myConsumer(): SubscribableChannel
}