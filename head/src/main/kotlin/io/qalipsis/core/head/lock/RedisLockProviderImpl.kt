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

import io.aerisconsulting.catadioptre.KTestable
import io.lettuce.core.RedisClient
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton
import java.time.Duration
import ru.sokomishalov.lokk.provider.RedisLettuceLokkProvider
import ru.sokomishalov.lokk.provider.withLokk

/**
 * Custom redis implementation of the lockProvider.
 *
 * @author Francisca Eze
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD]),
    Requires(notEnv = [ExecutionEnvironments.SINGLE_HEAD])
)
internal class RedisLockProviderImpl(redisClient: RedisClient) : LockProvider {

    @KTestable
    private val redisLettuceLockProvider = RedisLettuceLokkProvider(client = redisClient)

    /**
     * Executes the given action within a distributed lock.
     *
     * Testing different args of the RedisLettuceLockProvider withLokk function, while running the
     * ClusterDeploymentIntegrationTest(CDIT) produces different result.
     * Not providing a value for the atMostFor arg throws an error.
     * Using a very high atMostFor the application seems to be stuck almost forever and eventually times out.
     * Using a low value atMostFor like 1 or 2seconds and without an atLeastFor or a small atLeastFor
     * seem to work perfectly.
     *
     * Summary: The best option from the tests seem to be not specifying any value for the atLeastFor so
     * that the operation isn't forced to still hold the lock even when it's not necessary then utilising
     * an atMostFor that isn't so high(using 1 second in this scenario) since it appears to be stuck for high values atMostFor.
     * Also, we should observe the application for a while to see if these changes comes at a performance cost.
     */
    override suspend fun withLock(campaignKey: CampaignKey, block: suspend () -> Unit) {
        redisLettuceLockProvider.withLokk(
            name = campaignKey,
            atMostFor = Duration.ofSeconds(2),
        ) {
            block()
        }
    }
}