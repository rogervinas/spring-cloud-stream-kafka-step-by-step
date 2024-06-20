package com.rogervinas.stream.helper

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Locale
import java.util.Properties
import java.util.UUID

class KafkaConsumerHelper(bootstrapServers: String, topic: String) {
  private val consumer: Consumer<String, String>

  companion object {
    private const val MILLIS_POLL: Long = 250
  }

  init {
    consumer = KafkaConsumer(consumerConfig(bootstrapServers))
    consumer.subscribe(arrayListOf(topic))
  }

  fun consumeAll(): List<ConsumerRecord<String, String>> {
    return consumeAtLeast(100, Duration.ofSeconds(5))
  }

  fun consumeAtLeast(
    numberOfRecords: Int,
    timeout: Duration,
  ): List<ConsumerRecord<String, String>> {
    val records: MutableList<ConsumerRecord<String, String>> = ArrayList()
    var millisLeft = timeout.toMillis()
    do {
      consumer.poll(Duration.ofMillis(MILLIS_POLL)).iterator().forEachRemaining { records.add(it) }
      millisLeft -= MILLIS_POLL
    } while (millisLeft > 0 && records.size < numberOfRecords)
    return records
  }

  private fun consumerConfig(bootstrapServers: String): Properties {
    val config = Properties()
    config.setProperty(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString())
    config.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    config.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
    config.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
    config.setProperty(
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
      OffsetResetStrategy.EARLIEST.name.lowercase(Locale.getDefault()),
    )
    return config
  }
}
