package com.rogervinas.stream

import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.timeout
import com.nhaarman.mockito_kotlin.verify
import com.rogervinas.stream.domain.MyEvent
import com.rogervinas.stream.domain.MyEventConsumer
import com.rogervinas.stream.domain.MyEventProducer
import com.rogervinas.stream.domain.MyRetryableException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.*

@SpringBootTest(webEnvironment = DEFINED_PORT)
@ActiveProfiles("docker-compose")
class MyApplicationShould {

    val TOPIC = "my.topic"
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

    @BeforeEach
    fun setUp() {
        val bootstrapServers = env.getProperty("spring.cloud.stream.kafka.binder.brokers")!!
        kafkaConsumerHelper = MyKafkaConsumerHelper(bootstrapServers, TOPIC)
        kafkaConsumerHelper.consumeAll()
        kafkaProducerHelper = MyKafkaProducerHelper(bootstrapServers)
    }

    @Test
    fun `produce event`() {
        val text = "hello ${UUID.randomUUID()}"
        eventProducer.produce(MyEvent(text))

        val records = kafkaConsumerHelper.consumeAtLeast(1, TEN_SECONDS)

        assertThat(records)
                .singleElement().satisfies { record ->
                    JSONAssert.assertEquals(
                            record.value(),
                            "{\"number\":${text.length},\"string\":\"$text\"}",
                            true
                    )
                    assertThat(record.key())
                            .isEqualTo("key-${text.length}")
                }
    }

    @Test
    fun `consume event`() {
        val text = "hello ${UUID.randomUUID()}"
        kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

        val eventCaptor = argumentCaptor<MyEvent>()
        verify(eventConsumer, timeout(TEN_SECONDS.toMillis())).consume(eventCaptor.capture())

        assertThat(eventCaptor.firstValue).satisfies { event -> assertThat(event.text).isEqualTo(text) }
    }

    @Test
    fun `retry consume event 5 times`() {
        val eventCaptor = argumentCaptor<MyEvent>()
        doThrow(MyRetryableException("retry later!")).`when`(eventConsumer).consume(eventCaptor.capture())

        val text = "hello ${UUID.randomUUID()}"
        kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

        verify(eventConsumer, timeout(TEN_SECONDS.toMillis()).times(FIVE)).consume(eventCaptor.capture())

        assertThat(eventCaptor.allValues).allSatisfy { event -> assertThat(event.text).isEqualTo(text) }
    }
}