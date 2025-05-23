/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.annotations.LogInput
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

    private var registrationFailed = false

    override fun getStartupOrder() = Ordered.HIGHEST_PRECEDENCE

    override fun getOrder() = 0

    override fun init() {
        timeoutJob = coroutineScope.launch {
            kotlin.runCatching {
                delay(handshakeConfiguration.timeout.toMillis())
                log.debug { "Releasing the handshake blocker, because no handshake response was received" }
                registrationFailed = true
                registeredLatch.release()
            }
        }
        super.init()
    }

    @LogInput
    fun notifySuccessfulRegistration() {
        log.debug { "Releasing the handshake blocker, after a successful registration" }
        cancel()
    }

    @LogInput
    override suspend fun join() {
        registeredLatch.await()
    }

    @LogInput
    override fun cancel() {
        timeoutJob.cancel()
        registeredLatch.cancel()
    }

    override suspend fun await(): Optional<Int> {
        // Returns the code 101 when the registration could not be completed.
        return if (running && registrationFailed) {
            log.error { "The factory was not successfully registered to a head" }
            Optional.of(101)
        } else {
            Optional.empty()
        }
    }

    override fun accept(directive: Directive): Boolean {
        return directive is FactoryShutdownDirective
    }

    @LogInput
    override suspend fun notify(directive: FactoryShutdownDirective) {
        running = false
        timeoutJob.cancel()
        registeredLatch.cancel()
    }

    private companion object {
        val log = logger()
    }
}