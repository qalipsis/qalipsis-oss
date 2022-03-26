package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryShutdownDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.lifetime.FactoryStartupComponent
import io.qalipsis.core.lifetime.ProcessBlocker
import io.qalipsis.core.lifetime.ProcessExitCodeSupplier
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Optional

/**
 * Service in charge of blocking the main thread until the handshake reaches the timeout or forever when a
 * handshake response is received.
 *
 * The service forces the exit status 3 when the registration failed.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY])
internal class HandshakeBlocker(
    private val handshakeConfiguration: FactoryConfiguration.HandshakeConfiguration,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
) : FactoryStartupComponent, ProcessBlocker, ProcessExitCodeSupplier, DirectiveListener<FactoryShutdownDirective> {

    private val registeredLatch = Latch(true)

    private lateinit var timeoutJob: Job

    private var running = true

    override fun getStartupOrder() = Ordered.HIGHEST_PRECEDENCE

    override fun getOrder() = 0

    override fun init() {
        timeoutJob = coroutineScope.launch {
            kotlin.runCatching {
                delay(handshakeConfiguration.timeout.toMillis())
                log.debug { "Releasing the handshake blocker, because no handshake response was received" }
                registeredLatch.release()
            }
        }
        super.init()
    }

    fun notifySuccessfulRegistration() {
        timeoutJob.cancel()
    }

    override suspend fun join() {
        registeredLatch.await()
    }

    override fun cancel() {
        timeoutJob.cancel()
        registeredLatch.cancel()
    }

    override suspend fun await(): Optional<Int> {
        // Returns the code 101 when the registration could not be completed.
        return if (running && !registeredLatch.isLocked) {
            log.error { "The factory was not successfully registered to a head" }
            Optional.of(101)
        } else {
            Optional.empty()
        }
    }

    override fun accept(directive: Directive): Boolean {
        return directive is FactoryShutdownDirective
    }

    override suspend fun notify(directive: FactoryShutdownDirective) {
        running = false
        timeoutJob.cancel()
        registeredLatch.cancel()
    }

    private companion object {
        val log = logger()
    }
}