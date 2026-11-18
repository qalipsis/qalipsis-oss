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

package io.qalipsis.core.factory.configuration

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import io.qalipsis.core.serialization.SerializationConfigurer
import jakarta.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic

/**
 * Kotlin Protobuf serialization configuration for the factory.
 *
 * @author Gabriel Moraes
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY])
@ExperimentalSerializationApi
class FactorySerializationModuleConfiguration : SerializationConfigurer {

    override fun configure(serializersModuleBuilder: SerializersModuleBuilder) {
        serializersModuleBuilder.apply {
            polymorphic(Directive::class) {
                subclass(TransportableContext::class, TransportableContext.serializer())
                subclass(TransportableCompletionContext::class, TransportableCompletionContext.serializer())
                subclass(TransportableStepContext::class, TransportableStepContext.serializer())
            }
            polymorphic(TransportableContext::class) {
                subclass(TransportableCompletionContext::class, TransportableCompletionContext.serializer())
                subclass(TransportableStepContext::class, TransportableStepContext.serializer())
            }
        }
    }
}
