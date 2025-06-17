package com.rogervinas.stream.helper

import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait.forListeningPort
import org.testcontainers.containers.wait.strategy.Wait.forLogMessage
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode.WITH_INDIVIDUAL_TIMEOUTS_ONLY
import java.io.File

class DockerComposeContainerHelper {
  companion object {
    private const val BROKER = "kafka-kraft"
    private const val BROKER_PORT = 9092
  }

  fun createContainer(): ComposeContainer {
    return ComposeContainer(File("docker-compose.yml"))
      .withLocalCompose(true)
      .withExposedService(
        BROKER,
        BROKER_PORT,
        WaitAllStrategy(WITH_INDIVIDUAL_TIMEOUTS_ONLY)
          .withStrategy(forListeningPort())
          .withStrategy(forLogMessage(".*Kafka Server started.*", 1)),
      )
  }
}
