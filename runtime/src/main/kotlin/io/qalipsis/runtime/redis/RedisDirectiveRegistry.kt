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

package io.qalipsis.runtime.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisListCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.directives.SingleUseDirective
import io.qalipsis.core.directives.SingleUseDirectiveReference
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Singleton

/**
 * Implementation of [DirectiveRegistry] hosting the [Directive]s into Redis,
 * used for deployments other than [STANDALONE].
 *
 * @property idGenerator ID generator to create the directives keys.
 * @property serializer serializer for redis messages.
 * @property redisListCommands redis Coroutines commands.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(notEnv = [STANDALONE])
@ExperimentalLettuceCoroutinesApi
internal class RedisDirectiveRegistry(
    private val idGenerator: IdGenerator,
    private val serializer: DistributionSerializer,
    private val redisListCommands: RedisListCoroutinesCommands<String, String>
) : DirectiveRegistry {

    @LogInputAndOutput
    override suspend fun save(
        channel: DispatcherChannel,
        directive: SingleUseDirective<*>
    ): SingleUseDirectiveReference {
        val key = "${channel}:${idGenerator.long()}"
        val reference = directive.toReference(key)
        redisListCommands.lpush(key, serializer.serialize(directive).decodeToString())
        return reference
    }

    @LogInputAndOutput
    override suspend fun <T : SingleUseDirectiveReference> get(reference: T): Directive? {
        log.trace { "Reading single use directive with key ${reference.key}" }
        return redisListCommands.lpop(reference.key)?.let {
            log.trace { "Single use directive with key ${reference.key} was just retrieved (and removed)" }
            serializer.deserialize<SingleUseDirective<T>>(it.encodeToByteArray())
        }
    }

    private companion object {

        val log = logger()

    }

}