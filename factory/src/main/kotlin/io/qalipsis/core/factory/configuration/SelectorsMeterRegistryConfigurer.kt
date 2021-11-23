package io.qalipsis.core.factory.configuration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import jakarta.inject.Singleton

/**
 * Adds the factory selectors on all the locally generated Micrometer meters.
 *
 * @author Eric Jessé
 */
@Singleton
internal class SelectorsMeterRegistryConfigurer(
    private val factoryConfiguration: FactoryConfiguration
) : MeterRegistryConfigurer<MeterRegistry> {

    override fun configure(meterRegistry: MeterRegistry) {
        val selectors = factoryConfiguration.selectors.map { (key, value) -> Tag.of(key, value) }
        meterRegistry.Config().commonTags(selectors)
    }

    override fun getType() = MeterRegistry::class.java

    override fun supports(meterRegistry: MeterRegistry) = true

}
