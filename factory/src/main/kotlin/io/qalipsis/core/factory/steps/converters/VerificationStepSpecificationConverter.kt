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

package io.qalipsis.core.factory.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.VerificationStepSpecification
import io.qalipsis.core.factory.steps.VerificationStep

/**
 * [StepSpecificationConverter] from [VerificationStepSpecification] to [VerificationStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class VerificationStepSpecificationConverter(
    private val eventsLogger: EventsLogger,
    private val meterRegistry: CampaignMeterRegistry,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry
) : StepSpecificationConverter<VerificationStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is VerificationStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<VerificationStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as VerificationStepSpecification<I, O>
        // The reporting is done by the step itself and should not added by the decorator.
        spec.reporting.reportErrors = false
        val step =
            VerificationStep(spec.name, eventsLogger, meterRegistry, reportLiveStateRegistry, spec.verificationBlock)
        creationContext.createdStep(step)
    }

}
