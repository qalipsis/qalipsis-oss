/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.meters

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import java.time.temporal.ChronoUnit
import java.util.Collections.emptyList
import java.util.Collections.emptyMap

/**
 * Campaign lifecycle relevant meter registry.
 *
 * @author Eric Jessé
 */
interface CampaignMeterRegistry {

    /**
     * Creates a new [Counter] metric to be added to the registry. This metric measures the
     * count of specific events collected over time.
     *
     * @param scenarioName the name of the scenario under which the count is collected
     * @param stepName the name of a step within the scenario
     * @param name the name of the counter metric
     * @param tags additional key-value pairs to associate with the counter metric
     *
     * @sample counterExample
     */
    fun counter(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String> = emptyMap(),
    ): Counter

    /**
     * Creates a new [Timer] metric to be added to the registry. This metric measures the duration of an operation or a task.
     *
     * @param scenarioName the name of the scenario under which the timer is recorded
     * @param stepName the name of a step within the scenario
     * @param name the name of the timer metric
     * @param tags additional key-value pairs to associate with the timer metric
     * @param percentiles a list of values within the range of 1.0-100.0, representing specific points of observation, defaults to an empty list
     *
     * @sample timerExample
     */
    fun timer(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String> = emptyMap(),
        percentiles: Collection<Double> = emptyList(),
    ): Timer

    /**
     * Creates a new [Gauge] metric to be added to the registry. This metric tracks instantaneous values
     * change over time.
     *
     * @param scenarioName the name of the scenario under which the gauge is collected
     * @param stepName the name of a step within the scenario
     * @param name the name of the gauge metric
     * @param tags additional key-value pairs to associate with the gauge metric
     *
     * @sample gaugeExample
     */
    fun gauge(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String> = emptyMap(),
    ): Gauge

    /**
     * Creates a new [DistributionSummary] metric to be added to the registry. This metric
     * provides statistical data about the values observed/collected from an operation.
     *
     * @param scenarioName the name of the scenario under which the summary is collected
     * @param stepName the name of a step within the scenario
     * @param name the name of the summary metric
     * @param tags additional key-value pairs to associate with the summary metric
     * @param percentiles a list of values within the range of 1.0-100.0, representing specific points of observation, defaults to an empty list
     *
     * @sample summaryExample
     */
    fun summary(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String> = emptyMap(),
        percentiles: Collection<Double> = emptyList(),
    ): DistributionSummary

    /**
     * Creates a new [Gauge] metric to be added to the registry. This metric tracks instantaneous values
     * change over time.
     *
     * @param name the name of the gauge metric
     * @param tags additional key-value pairs to associate with the gauge metric
     *
     * @sample gaugeExampleWithVarargTags
     */
    fun gauge(name: String, vararg tags: String): Gauge

    /**
     * Creates a new [Counter] metric to be added to the registry. This metric measures the
     * count of specific events collected over time.
     *
     * @param name the name of the counter metric
     * @param tags additional key-value pairs to associate with the counter metric
     *
     * @sample counterExampleWithVarargTags
     */
    fun counter(name: String, vararg tags: String): Counter

    /**
     * Creates a new [Rate] metric to be added to the registry. This metric calculates the
     * ratio between two independently tracked measurements.
     *
     * @param scenarioName the name of the scenario under which the rate is collected
     * @param stepName the name of a step within the scenario
     * @param name the name of the metric
     * @param tags additional key-value pairs to associate with the rate metric
     *
     * @sample rateExample
     */
    fun rate(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String> = emptyMap(),
    ): Rate

    /**
     * Creates a new [Rate] metric to be added to the registry. This metric calculates the
     * ratio between two independently tracked measurements.
     *
     * @param name the name of the metric
     * @param tags additional key-value pairs to associate with the rate metric
     *
     * @sample rateExampleWithVarargTags
     */
    fun rate(
        name: String,
        vararg tags: String,
    ): Rate

    /**
     * Creates a new [Throughput] metric to be added to the registry. This metric
     * tracks the number of hits measured per a configured unit of time, default to seconds.
     *
     * @param scenarioName the name of the scenario within which the throughput is measured
     * @param stepName the name of a step within the scenario
     * @param name the name of the metric
     * @param tags additional key-value pairs to associate with the metric
     * @param unit the time unit for the configured measurement interval, defaults to [ChronoUnit.SECONDS]
     * @param percentiles a list of values within the range of 1.0-100.0, representing specific points of observation,
     * defaults to an empty list
     *
     * @sample throughputExample
     */
    fun throughput(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        unit: ChronoUnit = ChronoUnit.SECONDS,
        percentiles: Collection<Double> = emptyList(),
        tags: Map<String, String> = emptyMap(),
    ): Throughput

    /**
     * Creates a new [Throughput] metric to be added to the registry. This metric
     * tracks the number of hits measured per a configured unit of time, default to seconds.
     *
     * @param name the name of the metric
     * @param unit the time unit for the configured measurement interval, defaults to [ChronoUnit.SECONDS]
     * @param tags additional key-value pairs to associate with this meter
     *
     * @sample throughputExampleWithVarargTags
     */
    fun throughput(name: String, vararg tags: String): Throughput

    /**
     * Creates a new [DistributionSummary] metric to be added to the registry. This metric
     * provides statistical data about the values observed/collected from an operation.
     *
     * @param name the name of the summary metric
     * @param tags additional key-value pairs to associate with the summary metric
     *
     * @sample summaryExampleWithVarargTags
     */
    fun summary(name: String, vararg tags: String): DistributionSummary

    /**
     * Creates a new [Timer] metric to be added to the registry. This metric measures the duration of an operation or a task.
     *
     * @param name the name of the timer metric
     * @param tags additional key-value pairs to associate with the timer metric
     *
     * @sample timerExampleWithVarargTags
     */
    fun timer(name: String, vararg tags: String): Timer

    /**
     * Example usage of the `counter` function with tags as a [Map].
     */
    private fun counterExample() {
        counter(
            scenarioName = "scenario 1",
            stepName = "step 1",
            name = "counter name",
            tags = mapOf("foo" to "bar")
        )
    }

    /**
     * Example usage of the `counter` function with vararg tags.
     */
    private fun counterExampleWithVarargTags() {
        counter(
            name = "counter name",
            tags = arrayOf("scenario", "scenario-1", "foo", "bar", "hello", "world", "step", "test-step")
        )
    }

    /**
     * Example usage of the `gauge` function with tags as a [Map].
     */
    private fun gaugeExample() {
        gauge(
            scenarioName = "scenario 1",
            stepName = "step 1",
            name = "gauge name",
            tags = mapOf("tag-1" to "value-1", "tag-2" to "value-2"),
        )
    }


    /**
     * Example usage of the `gauge` function with vararg tags.
     */
    private fun gaugeExampleWithVarargTags() {
        gauge(
            name = "gauge name",
            tags = arrayOf("tag-1", "value-1", "tag-2", "value-2"),
        )
    }

    /**
     * Example usage of the `timer` function with tags as a [Map].
     */
    private fun timerExample() {
        timer(
            scenarioName = "scenario 1",
            stepName = "step 1",
            name = "http-requests duration",
            tags = mapOf("environment" to "production", "region" to "us-west"),
            percentiles = emptyList(),
        )
    }

    /**
     * Example usage of the `timer` function with vararg tags.
     */
    private fun timerExampleWithVarargTags() {
        timer(
            name = "http-requests duration",
            tags = arrayOf("environment", "production", "region", "us-west", "stepName", "step-2"),
        )
    }

    /**
     * Example usage of the `summary` function with tags as a [Map].
     */
    private fun summaryExample() {
        summary(
            scenarioName = "scenario 1",
            stepName = "step 1",
            name = "requests spread",
            tags = mapOf("foo" to "bar", "region" to "us-east"),
            percentiles = emptyList()
        )
    }

    /**
     * Example usage of the `summary` function with vararg tags.
     */
    private fun summaryExampleWithVarargTags() {
        summary(
            name = "requests spread",
            tags = arrayOf("foo", "bar", "region", "us-east", "hello", "world", "scenario", "scenario-2"),
        )
    }

    /**
     * Example usage of the `rate` function with vararg tags.
     */
    private fun rateExampleWithVarargTags() {
        rate(
            name = "requests rate",
            tags = arrayOf("foo", "bar", "region", "us-east")
        )
    }


    /**
     * Example usage of the `rate` function with tags as a [Map].
     */
    private fun rateExample() {
        rate(
            scenarioName = "scenario 1",
            stepName = "step 1",
            name = "requests rate",
            tags = mapOf("foo" to "bar", "region" to "us-east")
        )
    }

    /**
     * Example usage of the `throughput` function with tags as a [Map].
     */
    private fun throughputExample() {
        throughput(
            scenarioName = "scenario 1",
            stepName = "step 1",
            name = "requests throughput",
            tags = mapOf("foo" to "bar", "region" to "us-east"),
            percentiles = emptyList(),
            unit = ChronoUnit.SECONDS
        )
    }

    /**
     * Example usage of the `throughput` function with vararg tags.
     */
    private fun throughputExampleWithVarargTags() {
        throughput(
            name = "http-requests throughput",
            tags = arrayOf("environment", "production", "region", "us-west", "stepName", "step-2"),
        )
    }

}