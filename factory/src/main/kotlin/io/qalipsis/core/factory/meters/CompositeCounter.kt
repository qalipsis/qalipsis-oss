/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters

import io.qalipsis.api.meters.Counter
import io.micrometer.core.instrument.Counter as MicrometerCounter

/**
 * Composite class to encapsulate the [Counter]s at the scenario and campaign
 * level in order to update both at the same time.
 *
 * This [Counter] should be seen as the [scenarioLevelCounter] by the calling application.
 * The meter registries are the only one aware of the existence of the [campaignLevelCounter]
 * and will ask for its publication when required.
 *
 * This instance of [Counter] is not known by the instances of [io.micrometer.core.instrument.MeterRegistry].
 *
 * @author Joël Valère
 */
internal class CompositeCounter(
    private val scenarioLevelCounter: Counter,
    private val campaignLevelCounter: MicrometerCounter
) : Counter by scenarioLevelCounter {

    override fun increment(amount: Double) {
        scenarioLevelCounter.increment(amount)
        campaignLevelCounter.increment(amount)
    }
}