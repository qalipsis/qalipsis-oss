package io.qalipsis.runtime.executors

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Infrastructure
import io.qalipsis.api.Executors
import io.qalipsis.api.coroutines.CoroutineScopeProvider
import io.qalipsis.api.logging.LoggerHelper.logger
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil

/**
 * Creates the instances of [CoroutineScope] and [CoroutineContext] to be shared in the application.
 *
 * @author Eric Jessé
 */
@DelicateCoroutinesApi
@Factory
internal class ExecutorsFactory {

    private lateinit var createdScopes: Map<String, NamedCoroutineScope>

    /**
     * Creates a new instance of [CoroutineScopeProvider] that matches the configuration.
     */
    @Infrastructure
    @Context
    @Bean(preDestroy = "close")
    fun coroutineScopeProvider(configuration: ExecutorsConfiguration): CoroutineScopeProvider {
        val scopes =
            mutableMapOf(
                Executors.GLOBAL_EXECUTOR_NAME to createScope(
                    Executors.GLOBAL_EXECUTOR_NAME,
                    configuration.global.takeIf(String::isNotBlank) ?: DEFAULT_EXECUTOR_CONFIG
                )
            )

        val nonGlobalConfigs = mapOf(
            Executors.CAMPAIGN_EXECUTOR_NAME to (configuration.campaign.takeIf(String::isNotBlank)
                ?: DEFAULT_EXECUTOR_CONFIG),
            Executors.IO_EXECUTOR_NAME to (configuration.io.takeIf(String::isNotBlank) ?: DEFAULT_EXECUTOR_CONFIG),
            Executors.BACKGROUND_EXECUTOR_NAME to (configuration.background.takeIf(String::isNotBlank)
                ?: DEFAULT_EXECUTOR_CONFIG),
            Executors.ORCHESTRATION_EXECUTOR_NAME to (configuration.orchestration.takeIf(String::isNotBlank)
                ?: DEFAULT_EXECUTOR_CONFIG)
        )
        // Scopes with a defined size or referencing the global scope.
        @Suppress("UNCHECKED_CAST") val definedScopes = nonGlobalConfigs
            .mapValues { (name, config) -> createScope(name, config) }
            .filterValues { it != null } as Map<String, NamedCoroutineScope>
        scopes += definedScopes

        val referencingScopes = (nonGlobalConfigs - scopes.keys).mapValues { (_, config) ->
            scopes[config.trim()]
                ?: throw IllegalArgumentException("No defined executor with name '$config' could be found")
        }
        scopes += referencingScopes
        @Suppress("UNCHECKED_CAST")
        createdScopes = scopes as Map<String, NamedCoroutineScope>

        return SimpleCoroutineScopeProvider(
            scopes[Executors.GLOBAL_EXECUTOR_NAME]!!,
            scopes[Executors.CAMPAIGN_EXECUTOR_NAME]!!,
            scopes[Executors.IO_EXECUTOR_NAME]!!,
            scopes[Executors.BACKGROUND_EXECUTOR_NAME]!!,
            scopes[Executors.ORCHESTRATION_EXECUTOR_NAME]!!,
        )
    }

    private fun createScope(name: String, configuration: String): NamedCoroutineScope? {
        val threadsCount = configuration.toIntOrNull()
        val actualThreadsCount = threadsCount
            ?: if (configuration.trim().endsWith("x")
                && configuration.trim().substringBefore("x").toDoubleOrNull() != null
            ) {
                // When the configuration finishes with x and starts with a double, it is a factor for the available processors.
                val factor = configuration.substringBefore("x").trim().toDouble()
                ceil(factor * AVAILABLE_PROCESSORS).toInt().coerceAtLeast(MIN_EXECUTOR_SIZE)
            } else {
                null
            }

        return actualThreadsCount?.let { threads ->
            createFixedThreadPoolContext(name, threads)
        }
    }

    private fun createFixedThreadPoolContext(name: String, threadsCount: Int): NamedCoroutineScope {
        return if (threadsCount > 0) {
            val dispatcher = newFixedThreadPoolContext(threadsCount, name)
            NamedCoroutineScope(name, dispatcher, dispatcher, CoroutineScope(dispatcher)).also {
                log.trace { "Created the coroutine context $name with $threadsCount threads: $it" }
            }
        } else {
            log.trace { "Creating the coroutine context $name with the GlobalScope" }
            NamedCoroutineScope(name, Dispatchers.Default, Dispatchers.Default, GlobalScope)
        }
    }

    @Named(Executors.GLOBAL_EXECUTOR_NAME)
    @Bean(typed = [CoroutineDispatcher::class])
    fun globalDispatcher() = createdScopes[Executors.GLOBAL_EXECUTOR_NAME]!!.dispatcher

    @Named(Executors.BACKGROUND_EXECUTOR_NAME)
    @Bean(typed = [CoroutineDispatcher::class])
    fun backgroundDispatcher() = createdScopes[Executors.BACKGROUND_EXECUTOR_NAME]!!.dispatcher

    @Named(Executors.IO_EXECUTOR_NAME)
    @Bean(typed = [CoroutineDispatcher::class])
    fun ioDispatcher() = createdScopes[Executors.IO_EXECUTOR_NAME]!!.dispatcher

    @Named(Executors.CAMPAIGN_EXECUTOR_NAME)
    @Bean(typed = [CoroutineDispatcher::class])
    fun campaignDispatcher() = createdScopes[Executors.CAMPAIGN_EXECUTOR_NAME]!!.dispatcher

    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME)
    @Bean(typed = [CoroutineDispatcher::class])
    fun orchestrationDispatcher() = createdScopes[Executors.ORCHESTRATION_EXECUTOR_NAME]!!.dispatcher

    @Named(Executors.GLOBAL_EXECUTOR_NAME)
    @Bean(typed = [CoroutineContext::class])
    fun globalContext() = createdScopes[Executors.GLOBAL_EXECUTOR_NAME]!!.context

    @Named(Executors.BACKGROUND_EXECUTOR_NAME)
    @Bean(typed = [CoroutineContext::class])
    fun backgroundContext() = createdScopes[Executors.BACKGROUND_EXECUTOR_NAME]!!.context

    @Named(Executors.IO_EXECUTOR_NAME)
    @Bean(typed = [CoroutineContext::class])
    fun ioContext() = createdScopes[Executors.IO_EXECUTOR_NAME]!!.context

    @Named(Executors.CAMPAIGN_EXECUTOR_NAME)
    @Bean(typed = [CoroutineContext::class])
    fun campaignContext() = createdScopes[Executors.CAMPAIGN_EXECUTOR_NAME]!!.context

    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME)
    @Bean(typed = [CoroutineContext::class])
    fun orchestrationContext() = createdScopes[Executors.ORCHESTRATION_EXECUTOR_NAME]!!.context

    @Named(Executors.GLOBAL_EXECUTOR_NAME)
    @Bean(typed = [CoroutineScope::class])
    fun globalScope() = createdScopes[Executors.GLOBAL_EXECUTOR_NAME]!!

    @Named(Executors.BACKGROUND_EXECUTOR_NAME)
    @Bean(typed = [CoroutineScope::class])
    fun backgroundScope() = createdScopes[Executors.BACKGROUND_EXECUTOR_NAME]!!

    @Named(Executors.IO_EXECUTOR_NAME)
    @Bean(typed = [CoroutineScope::class])
    fun ioScope() = createdScopes[Executors.IO_EXECUTOR_NAME]

    @Named(Executors.CAMPAIGN_EXECUTOR_NAME)
    @Bean(typed = [CoroutineScope::class])
    fun campaignScope() = createdScopes[Executors.CAMPAIGN_EXECUTOR_NAME]!!

    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME)
    @Bean(typed = [CoroutineScope::class])
    fun orchestrationScope() = createdScopes[Executors.ORCHESTRATION_EXECUTOR_NAME]!!

    internal class NamedCoroutineScope(
        val name: String,
        val dispatcher: CoroutineDispatcher,
        val context: CoroutineContext,
        delegate: CoroutineScope
    ) : CoroutineScope by delegate

    private companion object {

        /**
         * Coroutine executor considered as default when not specified.
         */
        const val DEFAULT_EXECUTOR_CONFIG = Executors.GLOBAL_EXECUTOR_NAME

        /**
         * Minimal number of threads for an Executor.
         */
        const val MIN_EXECUTOR_SIZE = 2

        /**
         * Number of CPU cores of the current machine.
         */
        val AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors()

        @JvmStatic
        val log = logger()

    }
}