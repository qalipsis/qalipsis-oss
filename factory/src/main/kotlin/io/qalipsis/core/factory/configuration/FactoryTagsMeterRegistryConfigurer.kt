package io.qalipsis.core.factory.configuration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.context.annotation.Requires
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton

/**
 * Adds the factory tags on all the locally generated Micrometer meters.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class FactoryTagsMeterRegistryConfigurer(
    private val factoryConfiguration: FactoryConfiguration
) : MeterRegistryConfigurer<MeterRegistry> {

    override fun configure(meterRegistry: MeterRegistry) {
        val tags = factoryConfiguration.tags.map { (key, value) -> Tag.of(key, value) }.toMutableSet()
        tags += Tag.of("tenant", "_qalipsis_ten_")
        factoryConfiguration.zone?.let {
            tags += Tag.of("zone", it)
        }
        meterRegistry.Config().commonTags(tags)
    }

    override fun getType() = MeterRegistry::class.java

    override fun supports(meterRegistry: MeterRegistry) = true

}
