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

package io.qalipsis.core.configuration

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.BaseRedisAsyncCommands
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.async.RedisHashAsyncCommands
import io.lettuce.core.api.async.RedisKeyAsyncCommands
import io.lettuce.core.api.async.RedisListAsyncCommands
import io.lettuce.core.api.async.RedisScriptingAsyncCommands
import io.lettuce.core.api.async.RedisSetAsyncCommands
import io.lettuce.core.api.async.RedisSortedSetAsyncCommands
import io.lettuce.core.api.async.RedisStreamAsyncCommands
import io.lettuce.core.api.async.RedisStringAsyncCommands
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.BaseRedisCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisKeyCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisListCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisScriptingCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSetCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSortedSetCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisStreamCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisStringCoroutinesCommands
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.lettuce.core.cluster.api.coroutines
import io.lettuce.core.cluster.api.coroutines.RedisClusterCoroutinesCommands
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires

@Factory
@Requirements(Requires(env = [ExecutionEnvironments.REDIS]))
@ExperimentalLettuceCoroutinesApi
class RedisConfiguration {

    /**
     * Coroutine commands for a Redis standalone.
     */
    @Bean(
        typed = [
            BaseRedisCoroutinesCommands::class,
            RedisHashCoroutinesCommands::class,
            RedisKeyCoroutinesCommands::class,
            RedisListCoroutinesCommands::class,
            RedisScriptingCoroutinesCommands::class,
            RedisSetCoroutinesCommands::class,
            RedisSortedSetCoroutinesCommands::class,
            RedisStreamCoroutinesCommands::class,
            RedisStringCoroutinesCommands::class
        ]
    )
    @Requires(beans = [StatefulRedisConnection::class])
    @Requirements(
        Requires(beans = [StatefulRedisConnection::class]),
        Requires(missingBeans = [StatefulRedisClusterConnection::class])
    )
    fun coroutineRedisCommands(connection: StatefulRedisConnection<String, String>): RedisCoroutinesCommands<String, String> {
        return connection.coroutines()
    }

    /**
     * Async commands for a Redis standalone.
     */
    @Bean(
        typed = [
            BaseRedisAsyncCommands::class,
            RedisHashAsyncCommands::class,
            RedisKeyAsyncCommands::class,
            RedisListAsyncCommands::class,
            RedisScriptingAsyncCommands::class,
            RedisSetAsyncCommands::class,
            RedisSortedSetAsyncCommands::class,
            RedisStreamAsyncCommands::class,
            RedisStringAsyncCommands::class
        ]
    )
    @Requirements(
        Requires(beans = [StatefulRedisConnection::class]),
        Requires(missingBeans = [StatefulRedisClusterConnection::class])
    )
    fun asyncRedisCommands(connection: StatefulRedisConnection<String, String>): RedisAsyncCommands<String, String> {
        return connection.async()
    }

    /**
     * Coroutine commands for a Redis cluster.
     */
    @Bean(
        typed = [
            BaseRedisCoroutinesCommands::class,
            RedisHashCoroutinesCommands::class,
            RedisKeyCoroutinesCommands::class,
            RedisListCoroutinesCommands::class,
            RedisScriptingCoroutinesCommands::class,
            RedisSetCoroutinesCommands::class,
            RedisSortedSetCoroutinesCommands::class,
            RedisStreamCoroutinesCommands::class,
            RedisStringCoroutinesCommands::class]
    )
    @Requires(beans = [StatefulRedisClusterConnection::class])
    fun coroutineRedisClusterCommands(connection: StatefulRedisClusterConnection<String, String>): RedisClusterCoroutinesCommands<String, String> {
        return connection.coroutines()
    }

    /**
     * Async commands for a Redis cluster.
     */
    @Bean(
        typed = [
            BaseRedisAsyncCommands::class,
            RedisHashAsyncCommands::class,
            RedisKeyAsyncCommands::class,
            RedisListAsyncCommands::class,
            RedisScriptingAsyncCommands::class,
            RedisSetAsyncCommands::class,
            RedisSortedSetAsyncCommands::class,
            RedisStreamAsyncCommands::class,
            RedisStringAsyncCommands::class]
    )
    @Requires(beans = [StatefulRedisClusterConnection::class])
    fun asyncRedisClusterCommands(connection: StatefulRedisClusterConnection<String, String>): RedisAdvancedClusterAsyncCommands<String, String> {
        return connection.async()
    }
}