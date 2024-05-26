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

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName

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
        percentiles: Collection<Double> = emptyList()
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
        percentiles: Collection<Double> = emptyList()
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

}