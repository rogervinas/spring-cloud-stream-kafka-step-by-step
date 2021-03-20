package com.rogervinas.stream.shared

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait.forLogMessage
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode.WITH_INDIVIDUAL_TIMEOUTS_ONLY
import java.io.File
import java.util.stream.Collectors
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Component
@Profile("docker-compose")
class MyContainers {

    private val KAFKA = "kafka"
    private val KAFKA_PORT = 9094

    private val ZOOKEEPER = "zookeeper"
    private val ZOOKEEPER_PORT = 2181

    private val container = DockerComposeContainer<Nothing>(File("docker-compose.yml"))
            .apply { withLocalCompose(true) }
            .apply {
                withExposedService(KAFKA, KAFKA_PORT, WaitAllStrategy(WITH_INDIVIDUAL_TIMEOUTS_ONLY)
                        .apply { withStrategy(forListeningPortFixDockerDesktop322()) }
                        .apply { withStrategy(forLogMessage(".*creating topics.*", 1)) }
                )
            }
            .apply {
                withExposedService(ZOOKEEPER, ZOOKEEPER_PORT, WaitAllStrategy(WITH_INDIVIDUAL_TIMEOUTS_ONLY)
                        .apply { withStrategy(forListeningPortFixDockerDesktop322()) }
                        .apply { withStrategy(forLogMessage(".*binding to port.*", 1)) }
                )
            }

    private fun forListeningPortFixDockerDesktop322() = HostPortWaitStrategyFixDockerDesktop322()

    @PostConstruct
    fun start() = container.start()

    @PreDestroy
    fun stop() = container.stop()
}

class HostPortWaitStrategyFixDockerDesktop322 : HostPortWaitStrategy() {
    override fun getLivenessCheckPorts(): MutableSet<Int> {
        return super.getLivenessCheckPorts().stream()
                .filter { port -> port > 0 }
                .collect(Collectors.toSet())
    }
}