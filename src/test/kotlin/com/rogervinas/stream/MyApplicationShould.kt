package com.rogervinas.stream

import com.nhaarman.mockito_kotlin.*
import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.domain.MyEventProducer
import com.rogervinas.stream.domain.MyRetryableException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.*
import java.util.function.Consumer

@SpringBootTest(webEnvironment = DEFINED_PORT)
@ActiveProfiles("docker-compose")
class MyApplicationShould {

  val TOPIC = "my.topic"
  val TOPIC_DLQ = "my.topic.errors"

  val TEN_SECONDS = Duration.ofSeconds(10)
  val FIVE = 5

  @Autowired
  lateinit var env: Environment

  @Autowired
  lateinit var eventProducer: MyEventProducer

  @MockBean
  lateinit var eventConsumer: MyEventConsumer

  lateinit var kafkaProducerHelper: MyKafkaProducerHelper
  lateinit var kafkaConsumerHelper: MyKafkaConsumerHelper
  lateinit var kafkaDLQConsumerHelper: MyKafkaConsumerHelper

  @BeforeEach
  fun setUp() {
    val bootstrapServers = env.getProperty("spring.cloud.stream.kafka.binder.brokers")!!
    kafkaConsumerHelper = MyKafkaConsumerHelper(bootstrapServers, TOPIC)
    kafkaConsumerHelper.consumeAll()
    kafkaDLQConsumerHelper = MyKafkaConsumerHelper(bootstrapServers, TOPIC_DLQ)
    kafkaDLQConsumerHelper.consumeAll()
    kafkaProducerHelper = MyKafkaProducerHelper(bootstrapServers)
  }

  @Test
  fun `produce event`() {
    val text = "hello ${UUID.randomUUID()}"
    eventProducer.produce(MyEvent(text))

    val records = kafkaConsumerHelper.consumeAtLeast(1, TEN_SECONDS)

    assertThat(records).singleElement().satisfies(Consumer { record ->
      JSONAssert.assertEquals(record.value(), "{\"number\":${text.length},\"string\":\"$text\"}", true)
      assertThat(record.key()).isEqualTo("key-${text.length}")
    })
  }

  @Test
  fun `consume event`() {
    val text = "hello ${UUID.randomUUID()}"
    kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

    val eventCaptor = argumentCaptor<MyEvent>()
    verify(eventConsumer, timeout(TEN_SECONDS.toMillis())).consume(eventCaptor.capture())

    assertThat(eventCaptor.firstValue).satisfies(Consumer { event -> assertThat(event.text).isEqualTo(text) })
  }

  @Test
  fun `retry consume event 5 times`() {
    doThrow(MyRetryableException("retry later!")).`when`(eventConsumer).consume(any())

    val text = "hello ${UUID.randomUUID()}"
    kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

    val eventCaptor = argumentCaptor<MyEvent>()
    verify(eventConsumer, timeout(TEN_SECONDS.toMillis()).times(FIVE)).consume(eventCaptor.capture())

    assertThat(eventCaptor.allValues).allSatisfy(Consumer { event -> assertThat(event.text).isEqualTo(text) })
  }

  @Test
  fun `send to DLQ rejected messages`() {
    doThrow(MyRetryableException("retry later!")).`when`(eventConsumer).consume(any())

    val text = "hello ${UUID.randomUUID()}"
    kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

    val errorRecords = kafkaDLQConsumerHelper.consumeAtLeast(1, TEN_SECONDS)
    assertThat(errorRecords).singleElement().satisfies(Consumer { record ->
      JSONAssert.assertEquals(record.value(), "{\"number\":${text.length},\"string\":\"$text\"}", true)
    })
  }

  @ParameterizedTest
  @ValueSource(strings = ["plain text", "{\"unknownField\":\"not expected\"}"])
  fun `send to DLQ undeserializable messages`(body: String) {
    kafkaProducerHelper.send(TOPIC, body)

    val errorRecords = kafkaDLQConsumerHelper.consumeAtLeast(1, TEN_SECONDS)
    assertThat(errorRecords).singleElement().satisfies(Consumer { record ->
      assertThat(record.value()).isEqualTo(body)
    })
  }
}
