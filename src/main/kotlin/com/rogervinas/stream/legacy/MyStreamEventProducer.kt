package com.rogervinas.stream.legacy

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventProducer
import com.rogervinas.stream.shared.MyEventPayload
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.support.MessageBuilder

class MyStreamEventProducer(private val messageChannel: MessageChannel) : MyEventProducer {

    override fun produce(event: MyEvent) {
        val message = MessageBuilder.createMessage(toPayload(event), MessageHeaders(emptyMap()))
        messageChannel.send(message)
    }

    private fun toPayload(event: MyEvent): MyEventPayload {
        return MyEventPayload(event.text, event.text.length)
    }
}