package io.qalipsis.core.configuration

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
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
import io.lettuce.core.cluster.api.coroutines
import io.lettuce.core.cluster.api.coroutines.RedisClusterCoroutinesCommands
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires

@Factory
@Requirements(Requires(env = [ExecutionEnvironments.REDIS]))
@ExperimentalLettuceCoroutinesApi
internal class RedisConfiguration {

    /**
     * Coroutine commands for a Redis standalone.
     */
    @Bean(
        typed = [BaseRedisCoroutinesCommands::class,
            RedisHashCoroutinesCommands::class,
            RedisKeyCoroutinesCommands::class,
            RedisListCoroutinesCommands::class,
            RedisScriptingCoroutinesCommands::class,
            RedisSetCoroutinesCommands::class,
            RedisSortedSetCoroutinesCommands::class,
            RedisStreamCoroutinesCommands::class,
            RedisStringCoroutinesCommands::class]
    )
    @Requires(beans = [StatefulRedisConnection::class])
    fun coroutineRedisCommands(connection: StatefulRedisConnection<String, String>): RedisCoroutinesCommands<String, String> {
        return connection.coroutines()
    }

    /**
     * Coroutine commands for a Redis cluster.
     */
    @Bean(
        typed = [BaseRedisCoroutinesCommands::class,
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

}