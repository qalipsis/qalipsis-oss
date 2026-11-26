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

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import io.lettuce.core.cluster.SlotHash
import org.junit.jupiter.api.Test

internal class RedisMeterKeysTest {

    @Test
    fun `given several shards when building the keys then each shard has its own fraction of the slots`() {
        val keys = RedisMeterKeys("tenant", "campaign", SHARDS)

        (0 until SHARDS).forEach { shard ->
            val slots = listOf(
                keys.cumulative(shard), keys.previous(shard), keys.acknowledgements(shard, 10), keys.completions(shard),
                keys.message(shard, 10), keys.channel(shard)
            ).map(SlotHash::getSlot).distinct()
            assertThat(slots).hasSize(1)
            assertThat(slots.single()).isBetween(shard * SlotHash.SLOT_COUNT / SHARDS, (shard + 1) * SlotHash.SLOT_COUNT / SHARDS - 1)
        }
    }

    @Test
    fun `given the documented campaign when building the keys then they match the documentation`() {
        val keys = RedisMeterKeys("acme", "my-campaign", 16)

        assertThat(keys.sequence).isEqualTo("qmeters:{acme:my-campaign}:seq")
        assertThat(SlotHash.getSlot(keys.sequence)).isEqualTo(12150)
        assertThat(keys.cumulative(0)).isEqualTo("qmeters:{acme:my-campaign:s0-11}:cum")
        assertThat(SlotHash.getSlot(keys.cumulative(0))).isEqualTo(634)
        assertThat(keys.cumulative(1)).isEqualTo("qmeters:{acme:my-campaign:s1-12}:cum")
        assertThat(SlotHash.getSlot(keys.cumulative(1))).isEqualTo(1197)
    }

    @Test
    fun `given a campaign when building the keys then the keys of the registry share a slot and the shards are stable`() {
        val keys = RedisMeterKeys("tenant", "campaign", SHARDS)

        assertThat(keys.registryKeys.map(SlotHash::getSlot).distinct()).hasSize(1)
        assertThat(RedisMeterKeys("tenant", "campaign", SHARDS).channel(3)).isEqualTo(keys.channel(3))
    }

    private companion object {

        const val SHARDS = 16
    }
}
