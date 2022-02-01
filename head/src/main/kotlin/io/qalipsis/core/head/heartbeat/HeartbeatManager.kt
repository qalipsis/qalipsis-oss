package io.qalipsis.core.head.heartbeat

import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.heartbeat.HeartbeatConsumer
import io.qalipsis.core.lifetime.HeadStartupComponent
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.annotation.PreDestroy

/**
 * Component to handle the handshakes coming from the factories..
 *
 * @author Eric Jessé
 */
@Singleton
internal class HeartbeatManager(
    private val factoryService: FactoryService,
    private val headConfiguration: HeadConfiguration,
    private val heartbeatConsumer: HeartbeatConsumer,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val executionCoroutineScope: CoroutineScope
) : HeadStartupComponent {

    private var heartbeatConsumptionJob: Job? = null

    override fun init() {
        executionCoroutineScope.launch {
            log.debug { "Consuming from $heartbeatConsumer" }
            heartbeatConsumptionJob =
                heartbeatConsumer.start(headConfiguration.heartbeatChannel) { heartbeat ->
                    factoryService.updateHeartbeat(heartbeat)
                }
        }
    }

    @PreDestroy
    fun destroy() {
        heartbeatConsumptionJob?.cancel()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
