package io.qalipsis.core.factory.init

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.directives.DirectiveConsumer
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.handshake.HandshakeFactoryChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.handshake.RegistrationDirectedAcyclicGraph
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.core.heartbeat.HeartbeatEmitter
import io.qalipsis.core.lifetime.FactoryStartupComponent
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.annotation.PreDestroy

/**
 * Component in charge of processing the handshake with the head, from the factory perspective.
 *
 * @author Eric Jessé
 */
@Singleton
internal class InitializationContext(
    val factoryConfiguration: FactoryConfiguration,
    val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val directiveConsumer: DirectiveConsumer,
    private val handshakeFactoryChannel: HandshakeFactoryChannel,
    private val heartbeatEmitter: HeartbeatEmitter,
    @KTestable @Named(Executors.GLOBAL_EXECUTOR_NAME) private val coroutineDispatcher: CoroutineDispatcher,
    @KTestable @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
) : FactoryStartupComponent {

    private var consumptionJob: Job? = null

    override fun init() {
        consumptionJob = coroutineScope.launch {
            handshakeFactoryChannel.onReceiveResponse("${this@InitializationContext::class.simpleName}") {
                configureFactoryAfterHandshake(it)
            }
        }
    }

    private suspend fun configureFactoryAfterHandshake(response: HandshakeResponse) {
        persistNodeIdIfDifferent(response.nodeId)
        factoryConfiguration.nodeId = response.nodeId
        heartbeatEmitter.start(factoryConfiguration.nodeId, response.heartbeatChannel, response.heartbeatPeriod)
        feedbackFactoryChannel.start(response.feedbackChannel)
        directiveConsumer.start(response.unicastDirectivesChannel, response.broadcastDirectivesChannel)
        handshakeFactoryChannel.close()
    }

    @KTestable
    protected fun persistNodeIdIfDifferent(actualNodeId: String) {
        val directory = File(factoryConfiguration.metadataPath)
        val idFile = File(directory, FactoryConfiguration.NODE_ID_FILE_NAME)
        if (!idFile.exists() || actualNodeId != factoryConfiguration.nodeId) {
            try {
                directory.mkdirs()
                idFile.writeText(actualNodeId, Charsets.UTF_8)
            } catch (e: Exception) {
                log.error { e.message }
            }
        }
    }

    fun startHandshake(scenarios: Collection<Scenario>) {
        val feedbackScenarios = scenarios.map { scenario ->
            val feedbackDags = scenario.dags.map {
                RegistrationDirectedAcyclicGraph(
                    it.id, it.isSingleton, it.isRoot, it.isUnderLoad, it.stepsCount, it.selectors
                )
            }
            RegistrationScenario(
                scenario.id,
                scenario.minionsCount,
                feedbackDags
            )
        }

        runBlocking(coroutineDispatcher) {
            val request = HandshakeRequest(
                factoryConfiguration.nodeId,
                factoryConfiguration.selectors,
                factoryConfiguration.handshakeResponseChannel,
                feedbackScenarios
            )
            log.trace { "Sending handshake request $request" }
            handshakeFactoryChannel.send(request)
        }
    }

    @PreDestroy
    fun close() {
        kotlin.runCatching { consumptionJob?.cancel() }
    }

    private companion object {

        @JvmStatic
        val log = logger()

    }
}