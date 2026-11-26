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

import io.lettuce.core.cluster.SlotHash
import io.qalipsis.api.context.CampaignKey

/**
 * Keys and channels used by the [RedisMeterRegistry] for a campaign.
 *
 * The keys of the campaign registry share the hash tag of the campaign. The meter levels are distributed over
 * [shardsCount] shards, whose keys share the hash tag of the shard, so that each Lua call only accesses a single slot
 * in a Redis cluster, while the shards spread the load over the nodes of the cluster.
 *
 * The hash tag of each shard is chosen so that its slot lies in its own fraction of the slots: the shards are evenly
 * distributed over the nodes of a cluster whose slots are evenly assigned, which hashing the shard index alone does
 * not guarantee.
 *
 * For the tenant `acme`, the campaign `my-campaign` and 16 shards:
 * - the keys of the campaign registry are like `qmeters:{acme:my-campaign}:seq` (slot 12150),
 * - the keys of the shard 0 are like `qmeters:{acme:my-campaign:s0-11}:cum` (slot 634, in the slots 0 to 1023),
 * - the keys of the shard 1 are like `qmeters:{acme:my-campaign:s1-12}:cum` (slot 1197, in the slots 1024 to 2047).
 *
 * @param tenant tenant of the campaign, to isolate the campaigns of different tenants having the same key
 * @param campaignKey key of the campaign
 * @param shardsCount count of shards distributing the meter levels, `meters.redis.shards`
 */
internal class RedisMeterKeys(tenant: String, campaignKey: CampaignKey, shardsCount: Int = 1) {

    /**
     * Hash tag shared by the keys of the campaign registry, `{<tenant>:<campaign>}`.
     */
    private val campaignTag = "{${tenant}:${campaignKey}}"

    /**
     * Prefix of the keys of each shard, by shard index, including its slot-aligned hash tag:
     * `qmeters:{<tenant>:<campaign>:s<shard>-<n>}`.
     */
    private val shardPrefixes = Array(shardsCount) { shard ->
        "${PREFIX}{${slotAlignedTag("${tenant}:${campaignKey}:s${shard}", shard, shardsCount)}}"
    }

    /**
     * Counter generating the short ids of the meter levels of the campaign, which also determine their shard.
     */
    val sequence = "${PREFIX}${campaignTag}:seq"

    /**
     * Hash of the declared meter levels of the campaign: key of the meter level to `<short id>|<owner factory>`.
     */
    val declarations = "${PREFIX}${campaignTag}:ids"

    /**
     * Set of the shards containing at least one meter level of the campaign, to which the periods are acknowledged.
     */
    val shards = "${PREFIX}${campaignTag}:shards"

    /**
     * Keys of the campaign registry, accessed together by the declaration script and deleted at the end of the campaign.
     */
    val registryKeys = listOf(sequence, declarations, shards)

    /**
     * Hash of the cumulative values of the meter levels of [shard] over all the factories, by short id, each one as a
     * JSON object of its fields: for example `"12" -> {"cnt":20,"tot":1520,"max":87}`.
     */
    fun cumulative(shard: Int) = "${shardPrefixes[shard]}:cum"

    /**
     * Hash of the cumulative values of the meter levels of [shard] at the end of the previous period, by short id, to
     * compute the values of the current period as the difference with [cumulative].
     */
    fun previous(shard: Int) = "${shardPrefixes[shard]}:prev"

    /**
     * Set of the factories having acknowledged the [period] (end in epoch seconds) of [shard]: once all the factories
     * acknowledged it, the values of the period are published and the set is deleted.
     */
    fun acknowledgements(shard: Int, period: Long) = "${shardPrefixes[shard]}:ack:${period}"

    /**
     * Set of the factories having completed the campaign for [shard]: once it contains all the factories, the final
     * message with the cumulative values is published.
     */
    fun completions(shard: Int) = "${shardPrefixes[shard]}:fin"

    /**
     * Fallback copy of the message published for the [period] (end in epoch seconds) of [shard], for the owners that
     * missed it on the channel. It expires with the keys TTL.
     */
    fun message(shard: Int, period: Long) = "${shardPrefixes[shard]}:msg:${period}"

    /**
     * Pub/Sub channel of [shard], on which the values of each period and the final values are published to the owners
     * of its meter levels. It shares the hash tag of the shard, as required by the sharded Pub/Sub.
     */
    fun channel(shard: Int) = "${shardPrefixes[shard]}:meters"

    internal companion object {

        /**
         * Prefix of all the keys of the meters, to identify them in Redis.
         */
        private const val PREFIX = "qmeters:"

        /**
         * Returns the first tag "<base>-<n>" whose slot lies in the fraction [shard] of [shards] of all the slots.
         * A fraction contains about 16384 / [shards] slots, so about [shards] candidates are hashed on average.
         *
         * For example, with 16 shards, the shard 0 needs a slot from 0 to 1023: the candidates `acme:my-campaign:s0-0`
         * to `acme:my-campaign:s0-10` hash outside of it, `acme:my-campaign:s0-11` hashes to the slot 634 and is
         * returned. The result only depends on the arguments, so all the factories compute the same tags.
         */
        fun slotAlignedTag(base: String, shard: Int, shards: Int): String {
            val slots = shard * SlotHash.SLOT_COUNT / shards until (shard + 1) * SlotHash.SLOT_COUNT / shards
            return generateSequence(0) { it + 1 }.map { "$base-$it" }.first { SlotHash.getSlot(it) in slots }
        }
    }
}
