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

package io.qalipsis.core.factory.orchestration

/**
 * Snapshot of the campaign execution state.
 *
 * @property campaignComplete [true] when the campaign execution is complete, [false] otherwise
 * @property scenarioComplete [true] when the scenario execution is complete, [false] otherwise
 * @property minionComplete [true] when the minion execution is complete, [false] otherwise
 *
 * @author Eric Jessé
 */
data class CampaignCompletionState(
    var minionComplete: Boolean = false,
    var scenarioComplete: Boolean = false,
    var campaignComplete: Boolean = false
)