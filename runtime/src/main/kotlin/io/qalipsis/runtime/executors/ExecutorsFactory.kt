package io.qalipsis.runtime.executors

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.qalipsis.api.Executors
import io.qalipsis.api.coroutines.CoroutineScopeProvider
import io.qalipsis.api.logging.LoggerHelper.logger
import jakarta.inject.Named
import jakarta.inject.Singleton
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

    /**
     * Creates a new instance of [CoroutineScopeProvider] that matches the configuration.
     */
    @Singleton
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
            .filterValues { it != null } as Map<String, CoroutineScope>
        scopes += definedScopes

        val referencingScopes = (nonGlobalConfigs - scopes.keys).mapValues { (_, config) ->
            scopes[config.trim()]
                ?: throw IllegalArgumentException("No defined executor with name '$config' could be found")
        }
        scopes += referencingScopes

        return SimpleCoroutineScopeProvider(
            scopes[Executors.GLOBAL_EXECUTOR_NAME]!!,
            scopes[Executors.CAMPAIGN_EXECUTOR_NAME]!!,
            scopes[Executors.IO_EXECUTOR_NAME]!!,
            scopes[Executors.BACKGROUND_EXECUTOR_NAME]!!,
            scopes[Executors.ORCHESTRATION_EXECUTOR_NAME]!!,
        )
    }

    private fun createScope(name: String, configuration: String): CoroutineScope? {
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

    private fun createFixedThreadPoolContext(name: String, threadsCount: Int): CoroutineScope {
        return if (threadsCount > 0) {
            log.info { "Creating the coroutine context $name with $threadsCount threads" }
            CoroutineScope(newFixedThreadPoolContext(threadsCount, "$name-scope"))
        } else {
            log.info { "Creating the coroutine context $name with the GlobalScope" }
            GlobalScope
        }
    }

    @Named(Executors.GLOBAL_EXECUTOR_NAME)
    @Singleton
    fun globalScope(provider: CoroutineScopeProvider): CoroutineScope {
        return provider.global
    }

    @Named(Executors.CAMPAIGN_EXECUTOR_NAME)
    @Singleton
    fun campaignScope(provider: CoroutineScopeProvider): CoroutineScope {
        return provider.campaign
    }

    @Named(Executors.IO_EXECUTOR_NAME)
    @Singleton
    fun ioScope(provider: CoroutineScopeProvider): CoroutineScope {
        return provider.io
    }

    @Named(Executors.BACKGROUND_EXECUTOR_NAME)
    @Singleton
    fun backgroundScope(provider: CoroutineScopeProvider): CoroutineScope {
        return provider.background
    }

    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME)
    @Singleton
    fun orchestrationScope(provider: CoroutineScopeProvider): CoroutineScope {
        return provider.orchestration
    }

    @EachBean(CoroutineScope::class)
    @Bean(typed = [CoroutineContext::class])
    fun coroutineContexts(scope: CoroutineScope): CoroutineContext {
        return scope.coroutineContext
    }

    @EachBean(CoroutineContext::class)
    @Bean(typed = [CoroutineDispatcher::class])
    fun coroutineDispatchers(context: CoroutineContext): CoroutineDispatcher {
        return when (context) {
            is CoroutineDispatcher -> context
            else -> {
                log.warn { "Context $context is not a CoroutineDispatcher and is replaced by Dispatchers.Default" }
                Dispatchers.Default
            }
        }
    }

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