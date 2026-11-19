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

package io.qalipsis.core.head.report.meters

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.configuration.ExecutionEnvironments.TRANSIENT
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Implementation of [CampaignOwnershipChecker] based upon the persisted campaigns.
 *
 * Since the tenant of a campaign never changes, the results are cached to keep the database out of the
 * path of the meters.
 */
@Singleton
@Requires(notEnv = [TRANSIENT])
internal class PersistentCampaignOwnershipChecker(
    private val campaignRepository: CampaignRepository
) : CampaignOwnershipChecker {

    private val ownershipCache: Cache<TenantCampaign, Boolean> = Caffeine.newBuilder()
        .maximumSize(CACHE_MAX_SIZE)
        .expireAfterWrite(CACHE_EXPIRY)
        .build()

    override suspend fun filterOwnedCampaigns(tenant: String, campaignKeys: Set<CampaignKey>): Set<CampaignKey> {
        val cachedOwnerships = campaignKeys.associateWith { ownershipCache.getIfPresent(TenantCampaign(tenant, it)) }
        val unknownCampaigns = cachedOwnerships.filterValues { it == null }.keys
        val ownedUnknownCampaigns = if (unknownCampaigns.isEmpty()) {
            emptySet()
        } else {
            campaignRepository.findKeyByTenantAndKeyIn(tenant, unknownCampaigns).also { owned ->
                unknownCampaigns.forEach { ownershipCache.put(TenantCampaign(tenant, it), it in owned) }
            }
        }
        return cachedOwnerships.filterValues { it == true }.keys + ownedUnknownCampaigns
    }

    private data class TenantCampaign(val tenant: String, val campaign: CampaignKey)

    private companion object {

        const val CACHE_MAX_SIZE = 10_000L

        val CACHE_EXPIRY: Duration = Duration.ofMinutes(10)
    }
}
