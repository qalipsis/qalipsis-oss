/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.steps.verification

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepSpecification

/**
 * Direction in which the values of a key are expected to evolve, consecutive equal values being always accepted.
 */
enum class EvolutionDirection {

    /**
     * Each value has to be greater than or equal to the previous one of the same key.
     */
    INCREASING,

    /**
     * Each value has to be less than or equal to the previous one of the same key.
     */
    DECREASING
}

/**
 * Expectation on the evolution of the values verified by [verifyIncreasing] or [verifyDecreasing].
 */
interface EvolutionExpectationSpec {

    /**
     * Sets the minimal percentage of the transitions (pairs of consecutive values of a same key) that have to evolve
     * in the expected direction. The expectation is met when `valid transitions / all transitions * 100 >= percentile`.
     *
     * @param percentile minimal percentage of valid transitions, between 0 (excluded) and 100 (included)
     */
    fun percentile(percentile: Double)
}

/**
 * Specification for a step verifying that the values of each key evolve in an [EvolutionDirection], with a tolerance.
 */
interface EvolutionVerificationStepSpecification<INPUT> :
    StepSpecification<INPUT, INPUT, EvolutionVerificationStepSpecification<INPUT>>,
    ConfigurableStepSpecification<INPUT, INPUT, EvolutionVerificationStepSpecification<INPUT>> {

    /**
     * Configures the tolerance of the verification. Without it, all the transitions have to be valid.
     */
    fun shouldSatisfy(block: EvolutionExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>
}

/**
 * Implementation of [EvolutionVerificationStepSpecification].
 *
 * @property direction expected direction of the evolution of the values
 * @property keyExtractor extracts the key identifying the series of values, records with a null key are ignored
 * @property valueExtractor extracts the value to verify from the record, null values are ignored
 * @property expectedPercentile minimal percentage of valid transitions
 */
@Spec
data class EvolutionVerificationStepSpecificationImpl<INPUT, VALUE : Comparable<VALUE>>(
    val direction: EvolutionDirection,
    val keyExtractor: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Any?,
    val valueExtractor: (input: INPUT) -> VALUE?,
) : EvolutionVerificationStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, EvolutionVerificationStepSpecification<INPUT>>() {

    var expectedPercentile: Double = ALL_TRANSITIONS_PERCENTILE

    override fun shouldSatisfy(
        block: EvolutionExpectationSpec.() -> Unit
    ): ConfigurableStepSpecification<INPUT, INPUT, *> {
        expectedPercentile = EvolutionExpectationSpecImpl().apply(block).percentile
        return this
    }

    private class EvolutionExpectationSpecImpl : EvolutionExpectationSpec {

        var percentile = ALL_TRANSITIONS_PERCENTILE

        override fun percentile(percentile: Double) {
            require(percentile > 0 && percentile <= ALL_TRANSITIONS_PERCENTILE) {
                "The percentile should be greater than 0 and less than or equal to 100, but was $percentile"
            }
            this.percentile = percentile
        }
    }

    private companion object {

        /**
         * Percentile requiring all the transitions to be valid.
         */
        const val ALL_TRANSITIONS_PERCENTILE = 100.0
    }
}

/**
 * Verifies that, for each key, the values of the received records never decrease, with the tolerance set by
 * [EvolutionVerificationStepSpecification.shouldSatisfy]. Consecutive equal values are valid.
 *
 * The step forwards its input unchanged and does not fail the minions on an invalid transition: the verification is
 * reported on the step, combined over all the factories of the campaign.
 *
 * Example:
 * ```
 * .verifyIncreasing(
 *     name = "entity-version",
 *     keyExtractor = { _, input -> input.entityId },
 *     valueExtractor = { input -> input.version },
 * ).shouldSatisfy {
 *     percentile(99.0) // At least 99 % of the transitions have to be increasing.
 * }
 * ```
 *
 * @param name name of the step
 * @param keyExtractor extracts the key identifying the series of values from the context and the record, the records
 * with a null key are ignored
 * @param valueExtractor extracts the value to verify from the record, the records with a null value are ignored
 */
fun <INPUT, VALUE : Comparable<VALUE>> StepSpecification<*, INPUT, *>.verifyIncreasing(
    name: String,
    keyExtractor: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Any?,
    valueExtractor: (input: INPUT) -> VALUE?,
): EvolutionVerificationStepSpecification<INPUT> =
    verifyEvolution(name, EvolutionDirection.INCREASING, keyExtractor, valueExtractor)

/**
 * Verifies that, for each key, the values of the received records never increase, with the tolerance set by
 * [EvolutionVerificationStepSpecification.shouldSatisfy]. Consecutive equal values are valid.
 *
 * The step forwards its input unchanged and does not fail the minions on an invalid transition: the verification is
 * reported on the step, combined over all the factories of the campaign.
 *
 * Example:
 * ```
 * .verifyDecreasing(
 *     name = "remaining-quota",
 *     keyExtractor = { _, input -> input.accountId },
 *     valueExtractor = { input -> input.remaining },
 * ).shouldSatisfy {
 *     percentile(99.5) // At least 99.5 % of the transitions have to be decreasing.
 * }
 * ```
 *
 * @param name name of the step
 * @param keyExtractor extracts the key identifying the series of values from the context and the record, the records
 * with a null key are ignored
 * @param valueExtractor extracts the value to verify from the record, the records with a null value are ignored
 */
fun <INPUT, VALUE : Comparable<VALUE>> StepSpecification<*, INPUT, *>.verifyDecreasing(
    name: String,
    keyExtractor: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Any?,
    valueExtractor: (input: INPUT) -> VALUE?,
): EvolutionVerificationStepSpecification<INPUT> =
    verifyEvolution(name, EvolutionDirection.DECREASING, keyExtractor, valueExtractor)

private fun <INPUT, VALUE : Comparable<VALUE>> StepSpecification<*, INPUT, *>.verifyEvolution(
    name: String,
    direction: EvolutionDirection,
    keyExtractor: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Any?,
    valueExtractor: (input: INPUT) -> VALUE?,
): EvolutionVerificationStepSpecification<INPUT> =
    EvolutionVerificationStepSpecificationImpl(direction, keyExtractor, valueExtractor).also { step ->
        step.name = name
        add(step)
    }
