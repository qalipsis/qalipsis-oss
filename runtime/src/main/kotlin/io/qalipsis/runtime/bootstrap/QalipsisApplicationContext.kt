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

package io.qalipsis.runtime.bootstrap

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.EmbeddedApplication
import io.qalipsis.api.coroutines.CoroutineScopeProvider
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.lifetime.ExitStatusException
import io.qalipsis.core.lifetime.FactoryStartupComponent
import io.qalipsis.core.lifetime.HeadStartupComponent
import io.qalipsis.core.lifetime.ProcessBlocker
import io.qalipsis.core.lifetime.ProcessExitCodeSupplier
import kotlinx.coroutines.runBlocking
import java.util.Optional

/**
 * Application context for the runtime of QALIPSIS.
 *
 * @author Eric Jessé
 */
internal class QalipsisApplicationContext(
    private val role: DeploymentRole
) {

    private lateinit var applicationContext: ApplicationContext

    private lateinit var activeEnvironments: Collection<String>

    private lateinit var processBlockers: List<ProcessBlocker>

    private lateinit var processExitCodeSuppliers: List<ProcessExitCodeSupplier>

    private var embeddedApplication: Optional<EmbeddedApplication<*>> = Optional.empty()

    private var exitCode = 0

    fun execute(applicationContext: ApplicationContext): Int {
        this.applicationContext = applicationContext
        activeEnvironments = applicationContext.environment.activeNames
        processBlockers =
            applicationContext.getBeansOfType(ProcessBlocker::class.java).sortedBy(ProcessBlocker::getOrder)
        processExitCodeSuppliers = applicationContext.getBeansOfType(ProcessExitCodeSupplier::class.java)
            .sortedBy(ProcessExitCodeSupplier::getOrder)
        embeddedApplication = applicationContext.findBean(EmbeddedApplication::class.java)

        // Starts the webserver only when the head starts in non-autostart mode.
        startWebServerWhenRequired(embeddedApplication)

        try {
            doExecute(processBlockers, processExitCodeSuppliers)
        } catch (e: Exception) {
            if (log.isDebugEnabled) {
                log.error(e) { "QALIPSIS could not start: ${e.message}" }
            } else {
                log.error { "QALIPSIS could not start: ${e.message}" }
            }
            exitCode = (e as? ExitStatusException)?.exitStatus ?: exitCode.takeIf { it != 0 } ?: 2
            log.error(e) { e.message }
            log.warn { "Cancelling all the process blockers" }
            processBlockers.forEach { blocker ->
                runCatching {
                    blocker.cancel()
                    applicationContext.destroyBean(blocker)
                }
            }
            log.warn { "Forcing all the coroutine scope providers to close" }
            applicationContext.getBeansOfType(CoroutineScopeProvider::class.java).forEach {
                runCatching {
                    applicationContext.destroyBean(it)
                }
            }
        }

        return exitCode
    }

    /**
     * Starts the embedded webserver when the process has a head and dies not run in autostart mode.
     */
    private fun startWebServerWhenRequired(embeddedApplication: Optional<EmbeddedApplication<*>>) {
        if ((role == DeploymentRole.HEAD || role == DeploymentRole.STANDALONE) && ExecutionEnvironments.AUTOSTART !in applicationContext.environment.activeNames) {
            embeddedApplication.ifPresent { it.start() }
        }
    }

    /**
     * Executes the Qalipsis main thread.
     */
    private fun doExecute(
        processBlockers: Collection<ProcessBlocker>,
        processExitCodeSuppliers: Collection<ProcessExitCodeSupplier>
    ) {
        // Forces the loading of key services.
        if (role == DeploymentRole.STANDALONE || role == DeploymentRole.HEAD) {
            log.info { "Starting the head services" }
            startHead()
        }
        if (role == DeploymentRole.STANDALONE || role == DeploymentRole.FACTORY) {
            log.info { "Starting the factory services" }
            startFactory()
        }
        awaitProcessBlockers(processBlockers)
        awaitProcessExitCodeSuppliers(processExitCodeSuppliers)
    }

    /**
     * Starts the head components.
     */
    private fun startHead() {
        log.trace { "Triggering startup components for the head..." }
        val headerStarters = applicationContext.getBeansOfType(HeadStartupComponent::class.java)
            .sortedBy(HeadStartupComponent::getStartupOrder)
        for (starter in headerStarters) {
            log.trace { "Triggering ${starter::class.simpleName}" }
            starter.init()
            log.trace { "Triggered ${starter::class.simpleName}" }
        }
    }

    /**
     * Starts the factory components.
     */
    private fun startFactory() {
        log.trace { "Triggering startup components for the factory..." }
        val factoryStarters = applicationContext.getBeansOfType(FactoryStartupComponent::class.java)
            .sortedBy(FactoryStartupComponent::getStartupOrder)
        for (starter in factoryStarters) {
            log.trace { "Triggering ${starter::class.simpleName}" }
            starter.init()
            log.trace { "Triggered ${starter::class.simpleName}" }
        }
    }

    /**
     * Wait for all the active [ProcessBlocker] to be completed.
     * [ProcessBlocker]s are barrier that keeps the main thread running until all the required operations
     * are complete.
     *
     * Look at the details of their implementations and their conditions of activation.
     */
    private fun awaitProcessBlockers(processBlockers: Collection<ProcessBlocker>) {
        if (processBlockers.isNotEmpty()) {
            runBlocking {
                log.debug { "${processBlockers.size} service(s) to wait before exiting the process: ${processBlockers.joinToString { blocker -> "${blocker::class.simpleName}" }}" }
                processBlockers.forEach { blocker ->
                    log.debug { "Waiting for ${blocker::class.simpleName} to complete..." }
                    blocker.join()
                    log.debug { "${blocker::class.simpleName} completed" }
                }
            }
        }
    }

    /**
     * Once the [ProcessBlocker]s were all release and the process is about to leave, [ProcessExitCodeSupplier]
     * are requested to give an exit status to the overall process.
     */
    private fun awaitProcessExitCodeSuppliers(processExitCodeSuppliers: Collection<ProcessExitCodeSupplier>) {
        if (exitCode == 0 && processExitCodeSuppliers.isNotEmpty()) {
            runBlocking {
                log.debug { "${processExitCodeSuppliers.size} exit status suppliers to verify: ${processExitCodeSuppliers.joinToString { supplier -> "${supplier::class.simpleName}" }}" }
                val processExitCodeSuppliersIterator = processExitCodeSuppliers.iterator()
                var exitStatus = Optional.empty<Int>()
                while (exitStatus.isEmpty && processExitCodeSuppliersIterator.hasNext()) {
                    val supplier = processExitCodeSuppliersIterator.next()
                    exitStatus = supplier.await()
                    exitStatus.ifPresent {
                        log.debug { "Status $it returned by ${supplier::class.simpleName}" }
                    }
                }
                exitCode = exitStatus.orElse(0)
            }
        }
    }

    /**
     * Stops the embedded server if it is still running.
     */
    fun shutdown() {
        tryAndLogOrNull(log) {
            if (applicationContext.isRunning) {
                applicationContext.close()
            }
        }
        tryAndLogOrNull(log) {
            embeddedApplication.ifPresent { embedded ->
                if (embedded.isRunning) {
                    embedded.stop()
                }
            }
        }
    }

    private companion object {
        val log = logger()
    }
}