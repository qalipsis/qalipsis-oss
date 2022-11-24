/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.meters

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import java.util.function.Consumer
import java.util.function.ToDoubleFunction

/**
 * Campaign lifecycle relevant meter registry.
 *
 * For the documentation, consult [MeterRegistry]
 *
 * @author Eric Jessé
 *
 */
interface CampaignMeterRegistry {

    fun getMeters(): List<Meter>

    fun forEachMeter(consumer: Consumer<in Meter>)

    fun counter(name: String, tags: Iterable<Tag>): Counter

    fun counter(name: String, vararg tags: String): Counter

    fun summary(name: String, tags: Iterable<Tag>): DistributionSummary

    fun summary(name: String, vararg tags: String): DistributionSummary

    fun timer(name: String, tags: Iterable<Tag>): Timer

    fun timer(name: String, vararg tags: String): Timer

    fun more(): MeterRegistry.More

    fun <T> gauge(
        name: String,
        tags: Iterable<Tag>,
        stateObject: T,
        valueFunction: ToDoubleFunction<T>
    ): T

    fun <T : Number> gauge(name: String, tags: Iterable<Tag>, number: T): T

    fun <T : Number> gauge(name: String, number: T): T

    fun <T> gauge(name: String, stateObject: T, valueFunction: ToDoubleFunction<T>): T

    fun <T : Collection<*>> gaugeCollectionSize(name: String, tags: Iterable<Tag>, collection: T): T

    fun <T : Map<*, *>> gaugeMapSize(name: String, tags: Iterable<Tag>, map: T): T

    fun remove(meter: Meter): Meter

    fun removeByPreFilterId(preFilterId: Meter.Id): Meter

    fun remove(mappedId: Meter.Id): Meter

    fun clear()
}