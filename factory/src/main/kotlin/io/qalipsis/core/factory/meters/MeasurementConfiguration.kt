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

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import io.qalipsis.core.factory.meters.MeasurementConfiguration.Summary
import io.qalipsis.core.factory.meters.MeasurementConfiguration.Timer

/**
 * Additional configuration properties to support percentiles and histogram measurement for
 * QALIPSIS timer and summary meters.
 *
 * @property Summary.percentiles a list of values within the range of 1.0-100.0, representing specific points of observation, defaults to a list of 50.0, 75.0 and 99.9
 * @property Timer.percentiles a list of values within the range of 1.0-100.0, representing specific points of observation, defaults to a list of 50.0, 75.0 and 99.9
 *
 * @author Francisca Eze
 */
@ConfigurationProperties("meters.export")
internal interface MeasurementConfiguration {

    val summary: Summary

    val timer: Timer

    @ConfigurationProperties("summary")
    interface Summary {

        @get:Bindable(defaultValue = "")
        val percentiles: List<Double>?
    }

    @ConfigurationProperties("timer")
    interface Timer {

        @get:Bindable(defaultValue = "")
        val percentiles: List<Double>?

    }
}