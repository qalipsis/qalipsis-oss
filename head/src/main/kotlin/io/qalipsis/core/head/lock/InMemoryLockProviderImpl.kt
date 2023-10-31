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
package io.qalipsis.core.head.lock

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.head.hook.CampaignHook
import jakarta.inject.Singleton
import java.time.Duration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Custom inmemory implementation of the lockProvider.
 *
 * @author Francisca Eze
 */
@Singleton
internal class InMemoryLockProviderImpl : LockProvider, CampaignHook {

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