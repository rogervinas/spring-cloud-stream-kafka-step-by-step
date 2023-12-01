package com.rogervinas.stream

import com.rogervinas.stream.domain.MyEventProducer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component

@SpringBootApplication
class MyApplication

@Component
class MyApplicationUseCase(val producer: MyEventProducer) {
  // This class just ensures that in a real scenario we can inject a MyEventProducer
}

fun main(args: Array<String>) {
  runApplication<MyApplication>(*args)
}
