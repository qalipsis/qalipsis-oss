/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.factory.steps.evolution

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.verification.EvolutionVerificationStepSpecificationImpl
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope

/**
 * [StepSpecificationConverter] from [EvolutionVerificationStepSpecificationImpl] to [EvolutionVerificationStep].
 *
 * @author Eric Jessé
 */
@StepConverter
class EvolutionVerificationStepSpecificationConverter(
    private val countsRepository: EvolutionCountsRepository,
    private val campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val configuration: EvolutionVerificationConfiguration,
) : StepSpecificationConverter<EvolutionVerificationStepSpecificationImpl<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean =
        stepSpecification is EvolutionVerificationStepSpecificationImpl<*, *>

    override suspend fun <I, O> convert(
        creationContext: StepCreationContext<EvolutionVerificationStepSpecificationImpl<*, *>>
    ) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as EvolutionVerificationStepSpecificationImpl<I, *>
        @Suppress("UNCHECKED_CAST")
        val step = EvolutionVerificationStep(
            id = spec.name,
            retryPolicy = spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
            direction = spec.direction,
            keyExtractor = spec.keyExtractor as (StepContext<I, I>, I) -> Any?,
            valueExtractor = spec.valueExtractor as (I) -> Comparable<Any>?,
            expectedPercentile = spec.expectedPercentile,
            countsRepository = countsRepository,
            campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
            coroutineScope = coroutineScope,
            linger = configuration.linger
        )
        creationContext.createdStep(step)
    }
}
