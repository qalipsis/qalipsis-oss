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
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.factory.steps.WorkflowStepDecorator
import java.time.Duration

/**
 * [StepSpecificationDecoratorConverter] from any [AbstractStepSpecification] to [WorkflowStepDecorator].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class WorkflowStepDecoratorConverter(
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry,
    @PositiveDuration
    @Property(name = "campaign.step.start-timeout", defaultValue = "30s")
    private val stepStartTimeout: Duration,
) : StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 0

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        creationContext.createdStep(
            WorkflowStepDecorator(
                creationContext.createdStep!!,
                reportLiveStateRegistry,
                stepStartTimeout
            )
        )
    }

}
