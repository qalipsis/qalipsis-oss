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

package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import kotlinx.coroutines.ThreadContextElement
import org.slf4j.MDC
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element that sets the identifiers of a minion and of its job into the [MDC] of the thread
 * executing the coroutine, and restores the previous values when the coroutine is suspended.
 *
 * Unlike [kotlinx.coroutines.slf4j.MDCContext], which copies the complete MDC when it is created and replaces it
 * (with two more copies) at every resume, only the four keys of the minion are written in place: the resumes of the
 * minion's coroutines allocate nothing as long as the thread has no previous value for them.
 */
internal class MinionMdcContext(
    private val campaignKey: CampaignKey,
    private val scenarioName: ScenarioName,
    private val minionId: MinionId,
    private val jobId: String,
) : ThreadContextElement<Array<String?>?>, AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<MinionMdcContext> {

        const val CAMPAIGN_KEY = "campaign"

        const val SCENARIO_KEY = "scenario"

        const val MINION_KEY = "minion"

        const val JOB_KEY = "job"

        private val KEYS = arrayOf(CAMPAIGN_KEY, SCENARIO_KEY, MINION_KEY, JOB_KEY)
    }

    /**
     * Sets the values of the minion and returns the previous ones, or null when none of the keys was set.
     */
    override fun updateThreadContext(context: CoroutineContext): Array<String?>? {
        val previousValues = previousValues()
        MDC.put(CAMPAIGN_KEY, campaignKey)
        MDC.put(SCENARIO_KEY, scenarioName)
        MDC.put(MINION_KEY, minionId)
        MDC.put(JOB_KEY, jobId)
        return previousValues
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Array<String?>?) {
        KEYS.forEachIndexed { index, key ->
            oldState?.get(index)?.let { MDC.put(key, it) } ?: MDC.remove(key)
        }
    }

    private fun previousValues(): Array<String?>? {
        var previous: Array<String?>? = null
        KEYS.forEachIndexed { index, key ->
            MDC.get(key)?.let { value ->
                (previous ?: arrayOfNulls<String>(KEYS.size).also { previous = it })[index] = value
            }
        }
        return previous
    }
}
