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

package io.qalipsis.core.factory.steps.meter.converters

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.steps.TimerMeterStepSpecificationImpl
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factory.steps.meter.TimerMeterStep
import io.qalipsis.core.factory.steps.meter.checkers.ValueCheckerConverter
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope

/**
 * [StepSpecificationConverter] from [TimerMeterStepSpecificationImpl] to [TimerMeterStep].
 *
 * @author Francisca Eze
 */
@StepConverter
class TimerMeterStepSpecificationConverter(
    val meterRegistry: CampaignMeterRegistry,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry,
) : StepSpecificationConverter<TimerMeterStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is TimerMeterStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<TimerMeterStepSpecificationImpl<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as TimerMeterStepSpecificationImpl<I>
        val checkerConverter = ValueCheckerConverter()
        val checkers = spec.checks
            .filter { it.checkSpec != null }
            .map { it.valueExtractor to checkerConverter.convert(it.checkSpec!!) }

        val step = TimerMeterStep(
            id = spec.name,
            retryPolicy = spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
            coroutineScope = coroutineScope,
            campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
            meterName = spec.meterName,
            percentiles = spec.percentiles,
            block = spec.block,
            checkers = checkers,
            campaignMeterRegistry = meterRegistry
        )
        creationContext.createdStep(step)
    }

}
