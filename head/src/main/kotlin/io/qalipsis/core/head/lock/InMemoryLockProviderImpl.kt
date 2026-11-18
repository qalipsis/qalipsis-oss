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
package io.qalipsis.core.head.lock

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.head.hook.CampaignHook
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/**
 * Custom inmemory implementation of the lockProvider.
 *
 * @author Francisca Eze
 */
@Singleton
class InMemoryLockProviderImpl : LockProvider, CampaignHook {

    @KTestable
    private val lockRegistry: Cache<String, Mutex> = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(1))
        .build { Mutex() }

    override suspend fun withLock(campaignKey: CampaignKey, block: suspend () -> Unit) {
        lockRegistry.get(campaignKey) { Mutex() }.withLock {
            block()
        }
    }

    override suspend fun afterStop(campaignKey: CampaignKey) {
        lockRegistry.invalidate(campaignKey)
    }
}