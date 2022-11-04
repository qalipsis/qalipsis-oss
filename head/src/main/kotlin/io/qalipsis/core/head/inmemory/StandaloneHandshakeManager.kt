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

package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.handshake.HandshakeManager
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
@Replaces(HandshakeManager::class)
internal class StandaloneHandshakeManager(
    headChannel: HeadChannel,
    idGenerator: IdGenerator,
    factoryService: FactoryService,
    headConfiguration: HeadConfiguration
) : HandshakeManager(
    headChannel,
    idGenerator,
    factoryService,
    headConfiguration
) {

    override fun giveNodeIdToFactory(nodeRegistrationId: String): String {
        return STANDALONE_FACTORY_NAME
    }

    companion object {

        const val STANDALONE_FACTORY_NAME = "_embedded_"
    }
}