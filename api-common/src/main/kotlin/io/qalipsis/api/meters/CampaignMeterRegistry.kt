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

import io.micrometer.core.instrument.Tag
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
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

    fun counter(
        scenarioName: ScenarioName = "",
        stepName: StepName = "",
        name: String,
        tags: Map<String, String> = emptyMap()
    ): Counter

    fun summary(
        scenarioName: ScenarioName = "",
        stepName: StepName = "",
        name: String,
        tags: Map<String, String> = emptyMap()
    ): DistributionSummary

    fun timer(
        scenarioName: ScenarioName = "",
        stepName: StepName = "",
        name: String,
        tags: Map<String, String> = emptyMap()
    ): Timer

    fun <T> gauge(
        scenarioName: ScenarioName = "",
        stepName: StepName = "",
        name: String,
        tags: Map<String, String> = emptyMap(),
        stateObject: T,
        valueFunction: ToDoubleFunction<T>
    ): Gauge

    fun <T : Number> gauge(
        scenarioName: ScenarioName = "",
        stepName: StepName = "",
        name: String,
        tags: Map<String, String> = emptyMap(),
        number: T
    ): Gauge

    fun <T : Collection<*>> gaugeCollectionSize(
        scenarioName: ScenarioName = "",
        stepName: StepName = "",
        name: String,
        tags: Map<String, String> = emptyMap(),
        collection: T
    ): Gauge

    fun <T : Map<*, *>> gaugeMapSize(
        scenarioName: ScenarioName = "",
        stepName: StepName = "",
        name: String,
        tags: Map<String, String> = emptyMap(),
        map: T
    ): Gauge

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun counter(name: String, tags: Iterable<Tag>): Counter

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun counter(name: String, vararg tags: String): Counter

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun summary(name: String, tags: Iterable<Tag>): DistributionSummary

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun summary(name: String, vararg tags: String): DistributionSummary

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun timer(name: String, tags: Iterable<Tag>): Timer

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun timer(name: String, vararg tags: String): Timer

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun <T> gauge(
        name: String,
        tags: Iterable<Tag>,
        stateObject: T,
        valueFunction: ToDoubleFunction<T>
    ): T

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun <T : Number> gauge(name: String, tags: Iterable<Tag>, number: T): T

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun <T : Collection<*>> gaugeCollectionSize(name: String, tags: Iterable<Tag>, collection: T): T

    @Deprecated(message = "Use the function with the scenario and step as argument")
    fun <T : Map<*, *>> gaugeMapSize(name: String, tags: Iterable<Tag>, map: T): T

    fun clear()
}