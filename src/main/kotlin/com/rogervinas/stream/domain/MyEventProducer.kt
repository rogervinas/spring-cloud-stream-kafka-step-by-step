package com.rogervinas.stream.domain

interface MyEventProducer {
  fun produce(event: MyEvent)
}
