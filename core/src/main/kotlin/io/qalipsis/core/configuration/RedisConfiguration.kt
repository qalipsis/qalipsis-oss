package io.qalipsis.core.configuration

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import jakarta.inject.Named
import jakarta.inject.Singleton

@ExperimentalLettuceCoroutinesApi
@Factory
@Requirements(
    Requires(env = [ExecutionEnvironments.REDIS]),
    Requires(beans = [StatefulRedisConnection::class])
)
class RedisConfiguration {

    /**
     * For each kind of connection created by the Micronaut framework, we create the equivalent set of commands.
     *
     * See the implementations of [StatefulRedisConnection] for more details.
     */
    @Singleton
    @Primary
    @Named(DEFAULT)
    fun coroutineRedisCommands(connection: StatefulRedisConnection<*, *>): RedisCoroutinesCommands<*, *> {
        return connection.coroutines()
    }


    /**
     * For each kind of connection created by the Micronaut framework, we create the equivalent set of commands.
     *
     * See the implementations of [StatefulRedisConnection] for more details.
     */
    @Singleton
    @Named(PUB_SUB)
    fun coroutineRedisPubSubCommands(connection: StatefulRedisPubSubConnection<*, *>): RedisCoroutinesCommands<*, *> {
        return connection.coroutines()
    }

    companion object {

        /**
         * Name of the default [RedisCoroutinesCommands].
         */
        const val DEFAULT = "default"

        /**
         * Name of the [RedisCoroutinesCommands] for pub/sub.
         */
        const val PUB_SUB = "pubsub"

    }

}