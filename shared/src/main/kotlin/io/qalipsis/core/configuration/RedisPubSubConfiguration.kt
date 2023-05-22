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

import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.RedisClient
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import jakarta.inject.Named
import jakarta.inject.Singleton

@Factory
@Requirements(
    Requires(env = [ExecutionEnvironments.REDIS]),
    Requires(
        property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY,
        value = ExecutionEnvironments.REDIS
    )
)
class RedisPubSubConfiguration {

    @Singleton
    @Bean(preDestroy = "close")
    @Named(SUBSCRIBER_BEAN_NAME)
    internal fun subscriptionConnection(redisClient: AbstractRedisClient): StatefulRedisPubSubConnection<String, ByteArray> {
        return when (redisClient) {
            is RedisClusterClient -> redisClient.connectPubSub(
                ComposedRedisCodec(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
            )
            is RedisClient -> redisClient.connectPubSub(ComposedRedisCodec(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
            else -> throw IllegalArgumentException("Redis client of type ${redisClient::class} is not supported")
        }
    }

    @Singleton
    @Bean
    @Named(SUBSCRIBER_BEAN_NAME)
    internal fun reactiveSubscriptionCommands(@Named(SUBSCRIBER_BEAN_NAME) connection: StatefulRedisPubSubConnection<String, ByteArray>): RedisPubSubReactiveCommands<String, ByteArray> {
        return connection.reactive()
    }

    @Singleton
    @Bean(preDestroy = "close")
    @Named(PUBLISHER_BEAN_NAME)
    internal fun publicationConnection(redisClient: AbstractRedisClient): StatefulRedisPubSubConnection<String, ByteArray> {
        return when (redisClient) {
            is RedisClusterClient -> redisClient.connectPubSub(
                ComposedRedisCodec(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
            )
            is RedisClient -> redisClient.connectPubSub(ComposedRedisCodec(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
            else -> throw IllegalArgumentException("Redis client of type ${redisClient::class} is not supported")
        }
    }

    @Singleton
    @Bean
    @Named(PUBLISHER_BEAN_NAME)
    internal fun reactivePublicationCommands(@Named(PUBLISHER_BEAN_NAME) connection: StatefulRedisPubSubConnection<String, ByteArray>): RedisPubSubReactiveCommands<String, ByteArray> {
        return connection.reactive()
    }

    companion object {

        const val SUBSCRIBER_BEAN_NAME = "subscriber"

        const val PUBLISHER_BEAN_NAME = "publisher"

    }
}