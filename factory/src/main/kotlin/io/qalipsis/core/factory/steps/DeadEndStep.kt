/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import java.time.Instant

/**
 * Steps at the end of a DAG with no next step.
 *
 * @author Eric Jessé
 */
class DeadEndStep<I>(
    id: StepName,
    private val dagId: DirectedAcyclicGraphName,
    private val factoryCampaignManager: FactoryCampaignManager
) : BlackHoleStep<I>(id) {

    override suspend fun complete(completionContext: CompletionContext) {
        log.trace { "DAG $dagId completed for the context $completionContext" }
        factoryCampaignManager.notifyCompleteMinion(
            completionContext.minionId,
            Instant.ofEpochMilli(completionContext.minionStart),
            completionContext.campaignKey,
            completionContext.scenarioName,
            dagId
        )
    }

    private companion object {
        val log = logger()
    }
}
