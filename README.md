![CI](https://github.com/rogervinas/spring-cloud-stream-step-by-step/actions/workflows/gradle.yml/badge.svg)

# Spring Cloud Stream step by step

[Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream) is the solution provided by Spring to build applications connected to shared messaging systems.

It offers an abstraction (the **binding**) that works the same whatever underneath implementation we use (the **binder**):
* **Apache Kafka**
* **Rabbit MQ**
* **Kafka Streams**
* **Amazon Kinesis**
* ...

As of today there are two ways to configure Spring Cloud Stream:
* With annotations (legacy since 3.1)
* With functional programming model

Let's try to setup a simple example step by step and see how it works!

This demo has been created using this [spring initializr configuration](https://start.spring.io/#!type=gradle-project&language=kotlin&platformVersion=2.4.4.RELEASE&packaging=jar&jvmVersion=11&groupId=com.example&artifactId=demo&name=demo&description=Demo%20project%20for%20Spring%20Boot&packageName=com.example.demo&dependencies=cloud-stream,web) adding Kafka binder dependency `spring-cloud-starter-stream-kafka`.

In this demo:
* [Producer](#producer)
  * [Producer with annotations (legacy)](#producer-with-annotations-legacy) 
  * [Producer with functional programming model](#producer-with-functional-programming-model)
* [Consumer](#consumer)
  * [Consumer with annotations (legacy)](#consumer-with-annotations-legacy) 
  * [Consumer with functional programming model](#consumer-with-functional-programming-model)
* [Extras](#extras)
  * [Kafka Message Key](#kafka-message-key)
  * [Retries](#retries)
  * [Dead Letter Queue](#dead-letter-queue)
* [Run](#run)

## Producer

Our final goal is to produce messages to a Kafka topic.

From the point of view of the application we want an interface `MyEventProducer` to produce events to a generic messaging system. These events will be of type `MyEvent`, just containing a `text` field to make it simpler:
```kotlin
class MyEvent(val text: String)

interface MyEventProducer {
    fun produce(event: MyEvent)
}
```

We can first configure the binding using annotations (legacy way) and later we can change it to use functional interfaces. Checkout **legacy** tag in this repository if you want to go back to the legacy version.

### Producer with annotations (legacy)

#### 1) We configure the binding `my-producer` in application.yml:
```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: "localhost:9094"
      bindings:
        my-producer:
          destination: "my.topic"
``` 
Everything under `spring.cloud.kafka.binder` is related to the Kafka binder implementation and we can use all these extra [Kafka binder properties](https://github.com/spring-cloud/spring-cloud-stream-binder-kafka#kafka-binder-properties).

Everything under `spring.cloud.stream.bindings` is related to the Spring Cloud Stream binding abstraction and we can use all these extra [binding properties](https://docs.spring.io/spring-cloud-stream/docs/3.1.1/reference/html/spring-cloud-stream.html#binding-properties).

Now we have to link the binding `my-producer` with our implementation using annotations... ready?

#### 2) We create an interface with a method annotated with `@Output` and returning a `MessageChannel`:
```kotlin
interface MyProducerBinding {
    @Output("my-producer")
    fun myProducer(): MessageChannel
}
```
We use the name of the binding `my-producer` as the value of the `@Output` annotation.

#### 3) We create an implementation of `MyEventProducer` using a `MessageChannel`:
```kotlin
class MyStreamEventProducer(private val messageChannel: MessageChannel) : MyEventProducer {
    override fun produce(event: MyEvent) {
        val message = MessageBuilder.createMessage(toPayload(event), MessageHeaders(emptyMap()))
        messageChannel.send(message)
    }

    private fun toPayload(event: MyEvent): MyEventPayload {
        return MyEventPayload(event.text, event.text.length)
    }
}

class MyEventPayload @JsonCreator constructor(
        @JsonProperty("string") val string: String,
        @JsonProperty("number") val number: Int
)
```
We use a DTO `MyEventPayload` to specify how do we want the payload to be serialized to JSON (using [Jackson](https://github.com/FasterXML/jackson) annotations).

We do a simple transformation between `MyEvent` and `MyEventPayload` just as an example.

We use the `MessageChannel` to send the message.

#### 4) We wire everything together:
```kotlin
@EnableBinding(MyProducerBinding::class)
class MyConfiguration {
    @Bean
    fun myStreamEventProducer(binding: MyProducerBinding): MyEventProducer {
        return MyStreamEventProducer(binding.myProducer())
    }
}
```
`@EnableBinding` annotation makes Spring to create an instance of `MyProducerBinding` with a `myProducer()` method returning a `MessageChannel` that will be linked to the `my-producer` binding thanks to the `@Output("my-producer")` annotation.

Then we create an instance of type `MyEventProducer` that we can use in our code. This instance is implemented by a `MyStreamEventProducer` that will use the `MessageChannel` linked to the `my-producer` binding.

#### 5) For testing we start a Kafka container using [Testcontainers](https://www.testcontainers.org/):
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
        assertThat(records)
                .singleElement().satisfies { record ->
                    JSONAssert.assertEquals(
                            record.value(),
                            "{\"number\":${text.length},\"string\":\"$text\"}",
                            true
                    )
                }
    }
}
```
Check the complete test in [MyApplicationShould.kt](src/test/kotlin/com/rogervinas/stream/MyApplicationShould.kt).

### Producer with functional programming model

#### 1) We configure the binding `my-producer` in application.yml but declaring it as a function:
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
As stated in [functional binding names](https://docs.spring.io/spring-cloud-stream/docs/3.1.2/reference/html/spring-cloud-stream.html#_functional_binding_names): `my-producer` is the function name, `out` is for output bindings and `0` is the index we have to use if we have a single function.

#### 2) We create an implementation of `MyEventProducer` as a `Supplier` of `Flux<MyEventPayload>`, to fulfill the interfaces that both our application and Spring Cloud Stream are expecting:
```kotlin
class MyStreamEventProducer : Supplier<Flux<MyEventPayload>>, MyEventProducer {
    val sink = Sinks.many().unicast().onBackpressureBuffer<MyEventPayload>()

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
```
Every time we emit a `MyEventPayload` through the `Flux`, Spring Cloud Stream will publish it to Kafka.

#### 3) Finally, we create an instance of `MyStreamEventProducer` naming it `my-producer` to link it to the function definition:
```kotlin
@Configuration
class MyConfiguration {
    @Bean("my-producer")
    fun myStreamEventProducer(): MyEventProducer {
        return MyStreamEventProducer()
    }
}
```

#### 4) Test [MyApplicationShould.kt](src/test/kotlin/com/rogervinas/stream/MyApplicationShould.kt) should work the same!

## Consumer

Our final goal is to consume messages from a Kafka topic.

From the point of view of the application we want an interface `MyEventConsumer` to be called every time an event is consumed from a generic messaging system. These events will be of type `MyEvent` like in the producer example:
```kotlin
class MyEvent(val text: String)

interface MyEventConsumer {
    fun consume(event: MyEvent)
}
```

Again we can first configure the binding using annotations (legacy way) and later we can change it to use functional interfaces. Checkout **legacy** tag in this repository if you want to go back to the legacy version.

### Consumer with annotations (legacy)

#### 1) We configure the binding `my-consumer` in application.yml:
```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: "localhost:9094"
      bindings:
        my-consumer:
          destination: "my.topic"
          group: "${spring.application.name}"
``` 
Remember that everything under `spring.cloud.kafka.binder` is related to the Kafka binder implementation and we can use all these extra [Kafka binder properties](https://github.com/spring-cloud/spring-cloud-stream-binder-kafka#kafka-binder-properties) and everything under `spring.cloud.stream.bindings` is related to the Spring Cloud Stream binding abstraction and we can use all these extra [binding properties](https://docs.spring.io/spring-cloud-stream/docs/3.1.1/reference/html/spring-cloud-stream.html#binding-properties).

We configure a `group` because we want the application to consume from Kafka identifiying itself as a consumer group so if there were to be more than one instance of the application every message will be delivered to only one of the instances. 

Now we have to link the binding `my-consumer` with our implementation using annotations... ready?

#### 2) We create an interface with a method annotated with `@Input` and returning a `SubscribableChannel`:
```kotlin
interface MyConsumerBinding {
    @Input("my-consumer")
    fun myConsumer(): SubscribableChannel
}
```
We use the name of the binding `my-consumer` as the value of the `@Input` annotation.

#### 3) We create a class `MyStreamEventConsumer` that will receive `MyEventPayload`, transform to `MyEvent` and redirect to a `MyEventConsumer`:
```kotlin
class MyStreamEventConsumer(private val consumer: MyEventConsumer) {
   @StreamListener("my-consumer")
   fun consume(payload: MyEventPayload) {
      consumer.consume(fromPayload(payload))
   }

   private fun fromPayload(payload: MyEventPayload): MyEvent {
      return MyEvent(payload.string)
   }
}
```
* `MyStreamEventConsumer` has a method `consume` annotated with `@StreamListener` linking it to the `my-consumer` binding. This means that every time a new message is received in the Kafka topic, its payload will be deserialized to a `MyEventPayload` (applying [Jackson](https://github.com/FasterXML/jackson) annotations) and the `consume` method will we called.
* Then the only thing we have to do is to tranform the `MyEventPayload` to a `MyEvent` and callback the generic `MyEventConsumer`.

#### 4) We wire everything together:
```kotlin
@EnableBinding(MyConsumerBinding::class)
class MyConfiguration {
   @Bean
   fun myStreamEventConsumer(consumer: MyEventConsumer): MyStreamEventConsumer {
      return MyStreamEventConsumer(consumer)
   }

   @Bean
   fun myEventConsumer(): MyEventConsumer {
      return object : MyEventConsumer {
         override fun consume(event: MyEvent) {
            println("Received ${event.text}")
         }
      }
   }
}
```
`@EnableBinding` annotation makes Spring to create an instance of `MyConsumerBinding` and invoke its `myConsumer` method annotated with `@Input("my-consumer")` so we will have a `SubscribableChannel` up and running, listening to messages.

We create an instance of type `MyStreamEventConsumer` and its method `consume` annotated with `@StreamListener("my-consumer")` will be linked automatically to the `SubscribableChannel`.

We create a simple implementation of `MyEventConsumer` that justs prints the event.

#### 5) For testing we start a Kafka container using [Testcontainers](https://www.testcontainers.org/):
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

      assertThat(eventCaptor.firstValue).satisfies { event -> assertThat(event.text).isEqualTo(text) }
   }
}
```
Check the complete test in [MyApplicationShould.kt](src/test/kotlin/com/rogervinas/stream/MyApplicationShould.kt).

### Consumer with functional programming model

#### 1) We configure the binding `my-consumer` in application.yml but declaring it as a function:
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
As stated in [functional binding names](https://docs.spring.io/spring-cloud-stream/docs/3.1.2/reference/html/spring-cloud-stream.html#_functional_binding_names): `my-consumer` is the function name, `in` is for input bindings and `0` is the index we have to use if we have a single function.

#### 2) We create the same class `MyStreamEventConsumer` but implementing `Consumer<MyEventPayload>` to fulfill the interface required by Spring Cloud Stream:
```kotlin
class MyStreamEventConsumer(private val consumer: MyEventConsumer) : Consumer<MyEventPayload> {
   override fun accept(payload: MyEventPayload) {
      consumer.consume(fromPayload(payload))
   }

   private fun fromPayload(payload: MyEventPayload): MyEvent {
      return MyEvent(payload.string)
   }
}
```
Every time a new message is received in the Kafka topic, its payload will be deserialized to a `MyEventPayload` (applying [Jackson](https://github.com/FasterXML/jackson) annotations) and the `consume` method will we called.

Then the only thing we have to do is to tranform the `MyEventPayload` to a `MyEvent` and callback the generic `MyEventConsumer`.

#### 3) Finally, we create an instance of `MyStreamEventConsumer` naming it `my-consumer` to link it to the function definition:
```kotlin
@Configuration
class MyConfiguration {
   @Bean("my-consumer")
   fun myStreamEventConsumer(consumer: MyEventConsumer): MyStreamEventConsumer {
      return MyStreamEventConsumer(consumer)
   }

   @Bean
   fun myEventConsumer(): MyEventConsumer {
      return object : MyEventConsumer {
         override fun consume(event: MyEvent) {
            println("Received ${event.text}")
         }
      }
   }
}
```
We create a simple implementation of `MyEventConsumer` that justs prints the event.

#### 4) Test [MyApplicationShould.kt](src/test/kotlin/com/rogervinas/stream/MyApplicationShould.kt) should work the same!

## Extras

### Kafka Message Key

Kafka topics are partitioned to allow horizontal scalability.

When a message is sent to a topic, Kafka chooses randomly the destination partition. If we specify a key for the message, Kafka will use this key to choose the destination partition, then all messages sharing the same key will always be sent to the same partition.

This is important on the consumer side, because **chronological order of messages is only guaranteed within the same partition**, so if we need to consume some messages in the order they were produced, we should use the same key for all of them (i.e. for messages of a *user*, we use the *user* id as the message key).

To specify the message key in `MyStreamEventProducer` we can produce `Message<MyEventPayload>` instead of `MyEventPayload` and inform the `KafkaHeaders.MESSAGE_KEY` header:
```kotlin
class MyStreamEventProducer : Supplier<Flux<Message<MyEventPayload>>>, MyEventProducer {
  // ...
  override fun produce(event: MyEvent) {
    val message = MessageBuilder
            .withPayload(MyEventPayload(event.text, event.text.length))
            .setHeader(KafkaHeaders.MESSAGE_KEY, "key-${event.text.length}")
            .build()
    sink.emitNext(message, FAIL_FAST)
  }
  // ...
}
```

And we can test it like this:
```kotlin
@Test
fun `produce event`() {
    val text = "hello ${UUID.randomUUID()}"
    eventProducer.produce(MyEvent(text))

    val records = kafkaConsumerHelper.consumeAtLeast(1, TEN_SECONDS)

    assertThat(records)
            .singleElement().satisfies { record ->
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

Alternatively we can use `partitionKeyExpression` and other related [binding producer properties](https://docs.spring.io/spring-cloud-stream/docs/3.1.1/reference/html/spring-cloud-stream.html#_producer_properties) to achieve the same but at the binding abstraction level of Spring Cloud Stream.

### Retries

If errors are thrown while consuming messages, we can tell Spring Cloud Stream what to do using the following [binding consumer properties](https://docs.spring.io/spring-cloud-stream/docs/3.1.1/reference/html/spring-cloud-stream.html#_consumer_properties):
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
    assertThat(eventCaptor.allValues).allSatisfy { event -> assertThat(event.text).isEqualTo(text) }
}
```

### Dead Letter Queue

Additional to retries, DLQ is another mechanism we can use to deal with consumer errors.

In the case of Kafka it consists of sending to another topic all the messages that the consumer has rejected.

We can configure the DLQ using these [Kafka binder consumer properties](https://github.com/spring-cloud/spring-cloud-stream-binder-kafka#kafka-consumer-properties):
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

Application errors:
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
    assertThat(errorRecords)
            .singleElement().satisfies { record ->
                JSONAssert.assertEquals(
                        record.value(),
                        "{\"number\":${text.length},\"string\":\"$text\"}",
                        true
                )
            }
}
```

Message deserialization errors:
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
    assertThat(errorRecords)
            .singleElement().satisfies { record ->
                assertThat(record.value()).isEqualTo(body)
            }
}
```

## Run

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

Then you can use [kafkacat](https://github.com/edenhill/kafkacat) to produce/consume to/from Kafka:
```shell
# consume
kafkacat -b localhost:9094 -C -t my.topic
kafkacat -b localhost:9094 -C -t my.topic.errors

# produce a valid message
echo '{"string":"hello!", "number":37}' | kafkacat -b localhost:9094 -P -t my.topic

# produce an invalid message
echo 'hello!' | kafkacat -b localhost:9094 -P -t my.topic
```

That's it! Happy coding!