package com.rogervinas.stream.legacy

import org.springframework.cloud.stream.annotation.Output
import org.springframework.messaging.MessageChannel

interface MyProducerBinding {
    @Output("my-producer")
    fun myProducer(): MessageChannel
}