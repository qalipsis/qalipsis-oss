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

import io.micronaut.context.annotation.Property
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.factory.steps.ReportingStepDecorator
import java.time.Duration

/**
 * [StepSpecificationDecoratorConverter] from any [AbstractStepSpecification] to [ReportingStepDecorator].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class ReportingStepDecoratorSpecificationConverter(
    private val eventsLogger: EventsLogger,
    private val meterRegistry: CampaignMeterRegistry,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry,
    @PositiveDuration
    @Property(name = "campaign.step.start-timeout", defaultValue = "30s")
    private val stepStartTimeout: Duration,
) : StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 100

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        creationContext.createdStep(
            ReportingStepDecorator(
                creationContext.createdStep!!,
                creationContext.stepSpecification.reporting.reportErrors,
                eventsLogger,
                meterRegistry,
                reportLiveStateRegistry,
                stepStartTimeout
            )
        )
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}
