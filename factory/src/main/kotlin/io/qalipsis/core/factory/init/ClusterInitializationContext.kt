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

package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.configuration.CommunicationChannelConfiguration
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.handshake.HandshakeResponse
import jakarta.inject.Singleton


@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY])
internal class ClusterInitializationContext(
    factoryConfiguration: FactoryConfiguration,
    communicationChannelConfiguration: CommunicationChannelConfiguration,
    factoryChannel: FactoryChannel,
    private val handshakeBlocker: HandshakeBlocker,
) : InitializationContext(
    factoryConfiguration,
    communicationChannelConfiguration,
    factoryChannel
) {

    @LogInput
    override suspend fun notify(response: HandshakeResponse) {
        super.notify(response)
        handshakeBlocker.notifySuccessfulRegistration()
    }
}