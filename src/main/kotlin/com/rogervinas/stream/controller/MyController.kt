package com.rogervinas.stream.controller

import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventProducer
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/produce")
class MyController(private val eventProducer: MyEventProducer) {

    @PostMapping
    fun produce(@RequestBody text: String) {
        eventProducer.produce(MyEvent(text))
    }
}