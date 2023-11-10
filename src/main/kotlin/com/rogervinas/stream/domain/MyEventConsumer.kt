package com.rogervinas.stream.domain

interface MyEventConsumer {
  fun consume(event: MyEvent)
}
