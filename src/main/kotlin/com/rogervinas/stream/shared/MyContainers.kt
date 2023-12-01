package com.rogervinas.stream.shared

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait.forLogMessage
import java.io.File
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Component
@Profile("docker-compose")
class MyContainers {

  companion object {
    private const val KAFKA = "kafka"
    private const val KAFKA_PORT = 9094

    private const val ZOOKEEPER = "zookeeper"
    private const val ZOOKEEPER_PORT = 2181
  }

  private val container = ComposeContainer(File("docker-compose.yml"))
    .withLocalCompose(true)
    .withExposedService(KAFKA, KAFKA_PORT, forLogMessage(".*creating topics.*", 1))
    .withExposedService(ZOOKEEPER, ZOOKEEPER_PORT, forLogMessage(".*binding to port.*", 1))

  @PostConstruct
  fun start() = container.start()

  @PreDestroy
  fun stop() = container.stop()
}
