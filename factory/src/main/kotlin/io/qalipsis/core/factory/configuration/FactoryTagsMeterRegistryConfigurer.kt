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

package io.qalipsis.core.factory.configuration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.context.annotation.Property
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
    @Property(name = "factory.tenant", defaultValue = "_qalipsis_ten_") private val tenant: String,
    private val factoryConfiguration: FactoryConfiguration
) : MeterRegistryConfigurer<MeterRegistry> {

    override fun configure(meterRegistry: MeterRegistry) {
        val tags = factoryConfiguration.tags.map { (key, value) -> Tag.of(key, value) }.toMutableSet()
        tags += Tag.of("tenant", tenant)
        factoryConfiguration.zone?.let {
            tags += Tag.of("zone", it)
        }
        meterRegistry.Config().commonTags(tags)
    }

    override fun getType() = MeterRegistry::class.java

    override fun supports(meterRegistry: MeterRegistry) = true

}
