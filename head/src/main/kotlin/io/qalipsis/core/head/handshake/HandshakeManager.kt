package io.qalipsis.core.head.handshake

import io.micronaut.context.env.Environment
import io.qalipsis.api.Executors
import io.qalipsis.api.heads.StartupHeadComponent
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeHeadChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.campaign.CampaignAutoStarter
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.event.Level
import java.util.Optional
import javax.annotation.PreDestroy

/**
 * Component to handle the handshakes coming from the factories..
 *
 * @author Eric Jessé
 */
@Singleton
internal class HandshakeManager(
    private val environment: Environment,
    private val handshakeHeadChannel: HandshakeHeadChannel,
    private val idGenerator: IdGenerator,
    private val campaignAutoStarter: Optional<CampaignAutoStarter>,
    private val factoryService: FactoryService,
    private val headConfiguration: HeadConfiguration,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val executionCoroutineScope: CoroutineScope
) : StartupHeadComponent {

    private var handshakeConsumptionJob: Job? = null

    override fun getStartupOrder() = Int.MIN_VALUE

    override fun init() {
        executionCoroutineScope.launch {
            log.debug { "Consuming from $handshakeHeadChannel" }
            handshakeConsumptionJob =
                handshakeHeadChannel.onReceiveRequest("${this@HandshakeManager::class.simpleName}") { request ->
                    receivedHandshake(request)
                }
        }
    }

    @LogInputAndOutput(level = Level.DEBUG)
    protected suspend fun receivedHandshake(handshakeRequest: HandshakeRequest) {
        val nodeRegistrationId = handshakeRequest.nodeId
        val actualNodeId = if (nodeRegistrationId.startsWith("_")) {
            idGenerator.short()
        } else {
            nodeRegistrationId
        }
        log.info { "The factory $actualNodeId just started the handshake, persisting its state..." }
        factoryService.register(actualNodeId, handshakeRequest)

        handshakeHeadChannel.sendResponse(
            handshakeRequest.replyTo,
            HandshakeResponse(
                handshakeNodeId = handshakeRequest.nodeId,
                nodeId = actualNodeId,
                unicastDirectivesChannel = headConfiguration.unicastChannelPrefix + actualNodeId,
                broadcastDirectivesChannel = headConfiguration.broadcastChannel,
                feedbackChannel = headConfiguration.feedbackChannel,
                heartbeatChannel = headConfiguration.heartbeatChannel,
                heartbeatPeriod = headConfiguration.heartbeatDuration
            )
        )

        // If the CampaignAutoStarter is active, it is triggered.
        if (campaignAutoStarter.isPresent) {
            executionCoroutineScope.launch {
                // Wait to be sure that the factory receives the response before the campaign is triggered.
                delay(1000)
                campaignAutoStarter.get().trigger(handshakeRequest.scenarios.map { it.id })
            }
        }

        if (ExecutionEnvironments.STANDALONE in environment.activeNames) {
            // When the service starts as standalone, there is no need to keep the consumption task open.
            handshakeConsumptionJob?.cancel()
        }
    }

    @PreDestroy
    fun destroy() {
        handshakeConsumptionJob?.cancel()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
