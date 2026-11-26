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

package io.qalipsis.core.factory.redis.meters

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Message published by Redis to the owner of meter levels, when all the factories acknowledged a period.
 *
 * @property version version of the format
 * @property period end of the period, in epoch seconds
 * @property final true when all the factories completed the campaign
 * @property values values of the period, by short id of the meter level then field; the histograms are
 * separate entries, with the suffix [RedisMeterMeasurements.BUCKETS_SUFFIX]
 * @property cumulative cumulative values over the campaign, by short id of the meter level then field, only
 * present in the final message
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class RedisMeterMessage(
    @JsonProperty("v") val version: Int = 2,
    @JsonProperty("per") val period: Long = 0,
    @JsonProperty("fin") val final: Boolean = false,
    @JsonProperty("m") val values: Map<String, Map<String, Any?>> = emptyMap(),
    @JsonProperty("cum") val cumulative: Map<String, Map<String, Any?>> = emptyMap(),
)
