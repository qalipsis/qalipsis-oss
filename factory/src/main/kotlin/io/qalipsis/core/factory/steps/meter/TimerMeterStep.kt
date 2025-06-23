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

package io.qalipsis.core.factory.steps.meter

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.meters.meterConditions.*
import io.qalipsis.api.meters.meterConditions.ComparableValueFailureSpecification
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import java.time.Duration

/**
 * @TODO
 *
 * @author Francisca Eze
 */
internal class TimerMeterStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    val meterName: String,
    val percentiles: Map<Double, Duration>,
    val block: (stepContext: StepContext<I, I>, input: I) -> Duration,
    //@TODO the checks are need here so we can know what we are up against. But How do we get them here
    val specifications: List<FailureSpecification<Duration>>,
    val campaignMeterRegistry: CampaignMeterRegistry,
    //@TODO it exists in a different package
//    val campaignReportStateKeeper: CampaignReportStateKeeper
//    private val specification: (suspend (context: StepContext<I, O>) -> Unit)
) : AbstractStep<I, I>(id, retryPolicy) {
    init{
        println("CHECKS53 $specifications")
    }

    private lateinit var meter: Timer
    private val converter = ValueCheckConverter()

    override suspend fun start(context: StepStartStopContext) {
        meter = campaignMeterRegistry.timer(
            scenarioName = context.scenarioName,
            stepName = context.stepName,
            name = meterName,
            tags = context.toMetersTags(),
            //@TODO to be fixed
//            percentiles = percentiles
        )

        //Add a scheduler to run the checks function.
    }

    override suspend fun execute(context: StepContext<I, I>) {
        try {
            //if i have 1000 minions and they run they should return a duration(basically call timer.record())
            //run the code block of the input gotten from the context
            //Record this value as the current value of the timer(I guess update the timer with this new value)
            val input = context.receive()
            val valueToRecord = block(context, input)
            meter.record(valueToRecord)

            //MAke ASYNC
//            schedule.....{
            if(specifications.isNotEmpty()) {
                    val exceptions = specifications.map { spec ->
                        converter.convert(spec, meter)
//                        ValueCheckerExecutor(spec.)
                    }
                    if (exceptions.isNotEmpty()) {
                        // TODO Find a way to report the failure state.
//                        campaignStateKeeper.reportException()
                    }else {
                        // TODO Remove failure states.
                    }
                }
//            }

            context.send(input)
        } catch (e: Exception) {
            log.error(e) { e.message }
            throw e
        }
    }

    override suspend fun stop(context: StepStartStopContext) {
        //Meter values reported into the console.
        super.stop(context)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}

class ValueCheckConverter {

    fun <T : Comparable<T>, M: Meter<M>> convert(spec: ComparableValueFailureSpecification<M, T>, meter: Meter<M>): ValueChecker<T> {
//        val value = meter.valueExtractor()
//        val specification = spec.
        val checkSpec = spec.valueExtractor
        val value = meter.spec.valueExtractor()
        when(spec.type) {
            SpecificationType.LESS_THAN -> LessThanChecker(meter)
            SpecificationType.MORE_THAN -> LessThanChecker(meter)
            SpecificationType.IS_BETWEEN ->LessThanChecker(meter)
        }

        return checker.check()
    }
}

class ValueCheckConverter {
    fun <M : Meter<M>, T : Comparable<T>> convert(
        spec: ComparableValueFailureSpecification<M, T>,
        meter: M
    ): Boolean {
        val checkSpec = spec.checkSpec
            ?: throw IllegalStateException("No check specified in failure spec")

        val value = meter.spec.valueExtractor() // call extractor

        val checker: ValueChecker<T> = when (checkSpec.type) {
            "greaterThan" -> GreaterThanChecker(checkSpec.threshold)
            "lessThan" -> LessThanChecker(checkSpec.threshold)
            else -> throw IllegalArgumentException("Unknown type: ${checkSpec.type}")
        }

        return checker.check(value)
    }
}
