package com.rogervinas.stream

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.timeout
import com.nhaarman.mockito_kotlin.verify
import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.domain.MyEventProducer
import com.rogervinas.stream.domain.MyRetryableException
import com.rogervinas.stream.helper.DockerComposeContainerHelper
import com.rogervinas.stream.helper.KafkaConsumerHelper
import com.rogervinas.stream.helper.KafkaProducerHelper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.awaitility.Durations.TEN_SECONDS
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockReset
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.function.Consumer

@SpringBootTest(webEnvironment = NONE)
@Testcontainers
@ActiveProfiles("test")
class MyApplicationIntegrationTest {
  companion object {
    private const val TOPIC = "my.topic"
    private const val TOPIC_DLQ = "my.topic.errors"

    private const val ONE = 1
    private const val FIVE = 5

    @Container
    val container = DockerComposeContainerHelper().createContainer()
  }

  @Autowired
  @Qualifier("myStreamEventProducer") // Avoid SpringBootTest issue: expected single matching bean but found 2
  lateinit var eventProducer: MyEventProducer

  @MockitoBean(reset = MockReset.BEFORE)
  lateinit var eventConsumer: MyEventConsumer

  @Value("\${spring.cloud.stream.kafka.binder.brokers}")
  lateinit var kafkaBroker: String
  lateinit var kafkaProducerHelper: KafkaProducerHelper
  lateinit var kafkaConsumerHelper: KafkaConsumerHelper
  lateinit var kafkaDLQConsumerHelper: KafkaConsumerHelper

  @BeforeEach
  fun setUp() {
    kafkaConsumerHelper = KafkaConsumerHelper(kafkaBroker, TOPIC)
    kafkaConsumerHelper.consumeAll()
    kafkaDLQConsumerHelper = KafkaConsumerHelper(kafkaBroker, TOPIC_DLQ)
    kafkaDLQConsumerHelper.consumeAll()
    kafkaProducerHelper = KafkaProducerHelper(kafkaBroker)
  }

  @Test
  fun `should produce event`() {
    val text = "hello ${UUID.randomUUID()}"
    eventProducer.produce(MyEvent(text))

    val records = kafkaConsumerHelper.consumeAtLeast(1, TEN_SECONDS)

    assertThat(records).singleElement().satisfies(
      Consumer { record ->
        JSONAssert.assertEquals(record.value(), "{\"number\":${text.length},\"string\":\"$text\"}", true)
        assertThat(record.key()).isEqualTo("key-${text.length}")
      },
    )
  }

  @Test
  fun `should consume event`() {
    val eventCaptor = argumentCaptor<MyEvent>()
    doNothing().`when`(eventConsumer).consume(eventCaptor.capture())

    val text = "hello ${UUID.randomUUID()}"
    kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

    verify(eventConsumer, timeout(TEN_SECONDS.toMillis())).consume(any())

    await().atMost(TEN_SECONDS).untilAsserted {
      assertThat(eventCaptor.allValues.filter { it.text == text }).hasSize(ONE)
    }
  }

  @Test
  fun `should retry consume event 5 times`() {
    val eventCaptor = argumentCaptor<MyEvent>()
    doThrow(MyRetryableException("retry later!")).`when`(eventConsumer).consume(eventCaptor.capture())

    val text = "hello ${UUID.randomUUID()}"
    kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

    await().atMost(TEN_SECONDS).untilAsserted {
      assertThat(eventCaptor.allValues.filter { it.text == text }).hasSize(FIVE)
    }
  }

  @Test
  fun `should send to DLQ rejected messages`() {
    doThrow(MyRetryableException("retry later!")).`when`(eventConsumer).consume(any())

    val text = "hello ${UUID.randomUUID()}"
    kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

    val errorRecords = kafkaDLQConsumerHelper.consumeAtLeast(1, TEN_SECONDS)
    assertThat(errorRecords).singleElement().satisfies(
      Consumer { record ->
        JSONAssert.assertEquals(record.value(), "{\"number\":${text.length},\"string\":\"$text\"}", true)
      },
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["plain text", "{\"unknownField\":\"not expected\"}"])
  fun `should send to DLQ undeserializable messages`(body: String) {
    kafkaProducerHelper.send(TOPIC, body)

    val errorRecords = kafkaDLQConsumerHelper.consumeAtLeast(1, TEN_SECONDS)
    assertThat(errorRecords).singleElement().satisfies(
      Consumer { record ->
        assertThat(record.value()).isEqualTo(body)
      },
    )
  }
}
