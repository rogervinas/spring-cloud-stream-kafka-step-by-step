![CI](https://github.com/rogervinas/spring-cloud-stream-kafka-step-by-step/actions/workflows/gradle.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-blue?labelColor=black)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.21-blue?labelColor=black)
![SpringBoot](https://img.shields.io/badge/SpringBoot-3.2.0-blue?labelColor=black)
![SpringCloud](https://img.shields.io/badge/SpringCloud-2023.0.0_RC1-blue?labelColor=black)

# Spring Cloud Stream & Kafka binder step by step

[Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream) is the solution provided by **Spring** to build applications connected to shared messaging systems.

It offers an abstraction (the **binding**) that works the same whatever underneath implementation we use (the **binder**):
* **Apache Kafka**
* **Rabbit MQ**
* **Kafka Streams**
* **Amazon Kinesis**
* ...

Let's try to setup a simple example step by step and see how it works!

This demo has been created using this [spring initializr configuration](https://start.spring.io/#!type=gradle-project&language=kotlin&packaging=jar&groupId=com.example&artifactId=demo&name=demo&description=Demo%20project%20for%20Spring%20Boot&packageName=com.example.demo&dependencies=cloud-stream,web) adding Kafka binder dependency `spring-cloud-starter-stream-kafka`.

* [Producer with functional programming model](#producer-with-functional-programming-model)
* [Consumer with functional programming model](#consumer-with-functional-programming-model)
* [Extras](#extras)
  * [Kafka Message Key](#kafka-message-key)
  * [Retries](#retries)
  * [Dead Letter Queue](#dead-letter-queue)
* [Test this demo](#test-this-demo)
* [Run this demo](#run-this-demo)
* See also:
  * :octocat: [Spring Cloud Stream & Kafka Confluent Avro Schema Registry](https://github.com/rogervinas/spring-cloud-stream-kafka-confluent-avro-schema-registry)
  * :octocat: [Spring Cloud Stream & Kafka Streams Binder first steps](https://github.com/rogervinas/spring-cloud-stream-kafka-streams-first-steps)
  * :octocat: [Spring Cloud Stream Multibinder](https://github.com/rogervinas/spring-cloud-stream-multibinder)
  * :octocat: [Spring Cloud Stream & Kafka Streams Binder + Processor API](https://github.com/rogervinas/spring-cloud-stream-kafka-streams-processor)

You can browse older versions of this repo:
* [Spring Boot 2.x with legacy annotations](https://github.com/rogervinas/spring-cloud-stream-kafka-step-by-step/tree/spring-boot-2.x-legacy-annotations) (deprecated since spring-cloud-stream:3.1)
* [Spring Boot 2.x with functional programming model](https://github.com/rogervinas/spring-cloud-stream-kafka-step-by-step/tree/spring-boot-2.x)

## Producer with functional programming model

Our final goal is to produce messages to a Kafka topic.

From the point of view of the application we want an interface `MyEventProducer` to produce events to a generic messaging system. These events will be of type `MyEvent`, just containing a `text` field to make it simpler:
```kotlin
data class MyEvent(val text: String)

interface MyEventProducer { 
  fun produce(event: MyEvent)
}
```

Then we follow these steps:

### 1) We configure the binding `my-producer` in application.yml:
```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: "localhost:9094"
      bindings:
        my-producer-out-0:
          destination: "my.topic"
    function:
      definition: "my-producer"
``` 
* Everything under `spring.cloud.kafka.binder` is related to the Kafka binder implementation and we can use all these extra [Kafka binder properties](https://docs.spring.io/spring-cloud-stream-binder-kafka/docs/current/reference/html/spring-cloud-stream-binder-kafka.html#_kafka_binder_properties).
* Everything under `spring.cloud.stream.bindings` is related to the Spring Cloud Stream binding abstraction and we can use all these extra [binding properties](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#binding-properties).
* As stated in [functional binding names](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#_functional_binding_names): `my-producer` is the function name, `out` is for output bindings and `0` is the index we have to use if we have a single function.

### 2) We create an implementation of `MyEventProducer` as a `Supplier` of `Flux<MyEventPayload>`, to fulfill the interfaces that both our application and Spring Cloud Stream are expecting:
```kotlin
class MyStreamEventProducer : Supplier<Flux<MyEventPayload>>, MyEventProducer {
  private val sink = Sinks.many().unicast().onBackpressureBuffer<MyEventPayload>()

  override fun produce(event: MyEvent) {
    sink.emitNext(toPayload(event), FAIL_FAST)
  }

  override fun get(): Flux<MyEventPayload> {
    return sink.asFlux()
  }

  private fun toPayload(event: MyEvent): MyEventPayload {
    return MyEventPayload(event.text, event.text.length)
  }
}

data class MyEventPayload(
  val string: String,
  val number: Int
)
```
* We use a DTO `MyEventPayload` to specify how do we want the payload to be serialized to JSON. In this case we don't need to but we could use [Jackson](https://github.com/FasterXML/jackson) annotations if we wanted to customize the JSON serialization.
* We do a simple transformation between `MyEvent` and `MyEventPayload` just as an example.
* Every time we emit a `MyEventPayload` through the `Flux`, Spring Cloud Stream will publish it to Kafka.

### 3) Finally, we configure the beans needed to link `my-producer` function definition:
```kotlin
@Configuration
class MyConfiguration { 
  @Bean
  fun myStreamEventProducer() = MyStreamEventProducer()
  
  @Bean("my-producer")
  fun myStreamEventProducerFunction(producer: MyStreamEventProducer): () -> Flux<Message<MyEventPayload>> =
    producer::get
}
```
* We create a `MyStreamEventProducer` that will be injected wherever a `MyEventProducer` is needed.
* We create a lambda returning a `Flux<Message<MyEventPayload>>` that will be linked to the `my-producer` function, implemented by calling `myStreamEventProducer.get()` method.
* We do not merge both beans in one to avoid issues with `KotlinLambdaToFunctionAutoConfiguration`.

### 4) For testing we start a Kafka container using [Testcontainers](https://www.testcontainers.org/):
```kotlin
@SpringBootTest
class MyApplicationShould {
  // we inject MyEventProducer (it should be a MyStreamEventProducer)
  @Autowired lateinit var eventProducer: MyEventProducer
    
  @Test
  fun `produce event`() {
    // we produce using MyEventProducer
    val text = "hello ${UUID.randomUUID()}"
    eventProducer.produce(MyEvent(text))

    // we consume from Kafka using a helper
    val records = consumerHelper.consumeAtLeast(1, FIVE_SECONDS)

    // we verify the received json
    assertThat(records).singleElement().satisfies { record ->
      JSONAssert.assertEquals(
        record.value(),
        "{\"number\":${text.length},\"string\":\"$text\"}",
        true
      )
    }
  }
}
```
* Check the complete test in [MyApplicationShould.kt](src/test/kotlin/com/rogervinas/stream/MyApplicationShould.kt).

## Consumer with functional programming model

Our final goal is to consume messages from a Kafka topic.

From the point of view of the application we want an interface `MyEventConsumer` to be called every time an event is consumed from a generic messaging system. These events will be of type `MyEvent` like in the producer example:
```kotlin
data class MyEvent(val text: String)

interface MyEventConsumer {
  fun consume(event: MyEvent)
}
```

Then we follow these steps:

### 1) We configure the binding `my-consumer` in application.yml declaring it as a function:
```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: "localhost:9094"
      bindings:
        my-consumer-in-0:
          destination: "my.topic"
          group: "${spring.application.name}"
    function:
      definition: "my-consumer"
```
* Remember that everything under `spring.cloud.kafka.binder` is related to the Kafka binder implementation and we can use all these extra [Kafka binder properties](https://docs.spring.io/spring-cloud-stream-binder-kafka/docs/current/reference/html/spring-cloud-stream-binder-kafka.html#_kafka_binder_properties) and everything under `spring.cloud.stream.bindings` is related to the Spring Cloud Stream binding abstraction and we can use all these extra [binding properties](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#binding-properties).
* We configure a `group` because we want the application to consume from Kafka identifiying itself as a consumer group so if there were to be more than one instance of the application every message will be delivered to only one of the instances. 
* As stated in [functional binding names](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#_functional_binding_names): `my-consumer` is the function name, `in` is for input bindings and `0` is the index we have to use if we have a single function.

### 2) We create `MyStreamEventConsumer` to fulfill the interface required by Spring Cloud Stream:
```kotlin
class MyStreamEventConsumer(private val consumer: MyEventConsumer) : (MyEventPayload) -> Unit {
  override fun invoke(payload: MyEventPayload) {
    consumer.consume(fromPayload(payload))
  }

  private fun fromPayload(payload: MyEventPayload): MyEvent {
    return MyEvent(payload.string)
  }
}
```
* Every time a new message is received in the Kafka topic, its payload will be deserialized to a `MyEventPayload` and the `invoke` method will we called.
* Then the only thing we have to do is to tranform the `MyEventPayload` to a `MyEvent` and callback the generic `MyEventConsumer`.

### 3) Finally, we configure the beans needed to link `my-consumer` function definition:
```kotlin
@Configuration
class MyConfiguration {
  @Bean
  fun myEventConsumer() = object : MyEventConsumer {
    override fun consume(event: MyEvent) {
      println("Received ${event.text}")
    }
  }

  @Bean("my-consumer")
  fun myStreamEventConsumerFunction(consumer: MyEventConsumer): (MyEventPayload) -> Unit =
    MyStreamEventConsumer(consumer)
}
```
* We create a lambda receiving a `MyEventPayload` that will be linked to the `my-consumer` function, implemented by a `MyStreamEventConsumer`.
* We create a simple implementation of `MyEventConsumer` that justs prints the event.

### 4) For testing we start a Kafka container using [Testcontainers](https://www.testcontainers.org/):
```kotlin
@SpringBootTest
class MyApplicationShould {
  // we mock MyEventConsumer
  @MockBean lateinit var eventConsumer: MyEventConsumer

 @Test
 fun `consume event`() {
    // we send a Kafka message using a helper
    val text = "hello ${UUID.randomUUID()}"
    kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

    // we wait at most 5 seconds to receive the expected MyEvent in the MyEventConsumer mock
    val eventCaptor = argumentCaptor<MyEvent>()
    verify(eventConsumer, timeout(FIVE_SECONDS.toMillis())).consume(eventCaptor.capture())

    assertThat(eventCaptor.firstValue).satisfies { event -> 
      assertThat(event.text).isEqualTo(text) 
    }
 }
}
```
* Check the complete test in [MyApplicationShould.kt](src/test/kotlin/com/rogervinas/stream/MyApplicationShould.kt).

## Extras

### Kafka Message Key

Kafka topics are partitioned to allow horizontal scalability.

When a message is sent to a topic, Kafka chooses randomly the destination partition. If we specify a key for the message, Kafka will use this key to choose the destination partition, then all messages sharing the same key will always be sent to the same partition.

This is important on the consumer side, because **chronological order of messages is only guaranteed within the same partition**, so if we need to consume some messages in the order they were produced, we should use the same key for all of them (i.e. for messages of a *user*, we use the *user* id as the message key).

To specify the message key in `MyStreamEventProducer` we can produce `Message<MyEventPayload>` instead of `MyEventPayload` and inform the `KafkaHeaders.KEY` header:
```kotlin
class MyStreamEventProducer : Supplier<Flux<Message<MyEventPayload>>>, MyEventProducer {
  // ...
  override fun produce(event: MyEvent) {
    val message = MessageBuilder
      .withPayload(MyEventPayload(event.text, event.text.length))
      .setHeader(KafkaHeaders.KEY, "key-${event.text.length}")
      .build()
    sink.emitNext(message, FAIL_FAST)
  }
  // ...
}
```

As we are setting a key of type `String` we should use a `StringSerializer` as `key.serializer`:
```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: "localhost:9094"
          producer-properties:
            key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
```

And we can test it like this:
```kotlin
@Test
fun `produce event`() {
  val text = "hello ${UUID.randomUUID()}"
  eventProducer.produce(MyEvent(text))
  
  val records = kafkaConsumerHelper.consumeAtLeast(1, TEN_SECONDS)
  
  assertThat(records).singleElement().satisfies { record ->
    // check the message payload
    JSONAssert.assertEquals(
      record.value(),
      "{\"number\":${text.length},\"string\":\"$text\"}",
      true
    )
    // check the message key
    assertThat(record.key())
      .isEqualTo("key-${text.length}")
  }
}
```
* Alternatively we can use `partitionKeyExpression` and other related [binding producer properties](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#_producer_properties) to achieve the same but at the binding abstraction level of Spring Cloud Stream.

### Retries

If errors are thrown while consuming messages, we can tell Spring Cloud Stream what to do using the following [binding consumer properties](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#_consumer_properties):
* **maxAttempts**: number of retries
* **backOffInitialInterval**, **backOffMaxInterval**, **backOffMultiplier**: backoff parameters to increase delay between retries
* **defaultRetryable**, **retryableExceptions**: which exceptions retry or not

For example we can use this configuration:
```yaml
spring:
  cloud:
    stream:
      bindings:
        my-consumer-in-0:
          destination: "my.topic"
          group: "${spring.application.name}"
          consumer:
            max-attempts: 5
            back-off-initial-interval: 100
            default-retryable: false
            retryable-exceptions:
              com.rogervinas.stream.domain.MyRetryableException: true            
```

And we can test it like this:
```kotlin
@Test
fun `retry consume event 5 times`() {
  // we throw a MyRetryableException every time we receive a message
  doThrow(MyRetryableException("retry later!")).`when`(eventConsumer).consume(any())

  // we send a Kafka message using a helper
  val text = "hello ${UUID.randomUUID()}"
  kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

  // consumer has been called five times with the same message
  val eventCaptor = argumentCaptor<MyEvent>()
  verify(eventConsumer, timeout(TEN_SECONDS.toMillis()).times(FIVE)).consume(eventCaptor.capture())
  assertThat(eventCaptor.allValues).allSatisfy { event -> 
    assertThat(event.text).isEqualTo(text) 
  }
}
```

### Dead Letter Queue

Additional to retries, DLQ is another mechanism we can use to deal with consumer errors.

In the case of Kafka it consists of sending to another topic all the messages that the consumer has rejected.

We can configure the DLQ using these [Kafka binder consumer properties](https://docs.spring.io/spring-cloud-stream-binder-kafka/docs/current/reference/html/spring-cloud-stream-binder-kafka.html#kafka-consumer-properties):
* **enableDlq**: enable DLQ
* **dlqName**:
  * **not set**: defaults to `error.<destination>.<group>`
  * **set**: use a specific DLQ topic
* **dlqPartitions**:
  * **not set**: DLQ topic should have the same number of partitions as the original one
  * **set to 0**: DLQ topic should have only 1 partition
  * **set to N>0**: we should provide a `DlqPartitionFunction` bean

For example we can use this configuration:
```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: "localhost:9094"
        bindings:
          my-consumer-in-0:
            consumer:
              enable-dlq: true
              dlq-name: "my.topic.errors"
              dlq-partitions: 1    
      bindings:
        my-consumer-in-0:
          destination: "my.topic"
          group: "${spring.application.name}"
```

And we can test it like this:

#### Application errors:
```kotlin
@Test
fun `send to DLQ rejected messages`() {
  // we throw a MyRetryableException every time we receive a message
  doThrow(MyRetryableException("retry later!")).`when`(eventConsumer).consume(any())

  // we send a Kafka message using a helper
  val text = "hello ${UUID.randomUUID()}"
  kafkaProducerHelper.send(TOPIC, "{\"number\":${text.length},\"string\":\"$text\"}")

  // we check the message has been sent to the DLQ
  val errorRecords = kafkaDLQConsumerHelper.consumeAtLeast(1, TEN_SECONDS)
  assertThat(errorRecords).singleElement().satisfies { record ->
    JSONAssert.assertEquals(
      record.value(),
      "{\"number\":${text.length},\"string\":\"$text\"}",
      true
    )
  }
}
```

#### Message deserialization errors:
```kotlin
@ParameterizedTest
@ValueSource(strings = [
  "plain text",
  "{\"unknownField\":\"not expected\"}"
])
fun `send to DLQ undeserializable messages`(body: String) {
  // we send a Kafka message with an invalid body using a helper
  kafkaProducerHelper.send(TOPIC, body)

  // we check the message has been sent to the DLQ
  val errorRecords = kafkaDLQConsumerHelper.consumeAtLeast(1, TEN_SECONDS)
  assertThat(errorRecords).singleElement().satisfies { record ->
    assertThat(record.value()).isEqualTo(body)
  }
}
```

That's it! Happy coding! 💙

## Test this demo

```shell
./gradlew test
```

## Run this demo

Run with docker-compose:
```shell
docker-compose up -d
./gradlew bootRun
docker-compose down
```

Run with testcontainers:
```shell
SPRING_PROFILES_ACTIVE=docker-compose ./gradlew bootRun
```

Then you can use [kcat](https://github.com/edenhill/kcat) to produce/consume to/from Kafka:
```shell
# consume
kcat -b localhost:9094 -C -t my.topic
kcat -b localhost:9094 -C -t my.topic.errors

# produce a valid message
echo '{"string":"hello!", "number":37}' | kcat -b localhost:9094 -P -t my.topic

# produce an invalid message
echo 'hello!' | kcat -b localhost:9094 -P -t my.topic
```
