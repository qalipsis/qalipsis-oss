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

package io.qalipsis.core.factory.configuration

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.ProtobufSerializationModuleConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic

/**
 * Kotlin Json Serialization configuration.
 *
 * Creates a [Protobuf] instance properly configured with serializer modules for subclasses of [io.qalipsis.core.factory.orchestration.TransportableContext].
 * This way Kotlin serialization can serialize and deserialize data using Interfaces or Parent classes and still keep reference to the original class.
 * It is needed to explicitly declare polymorphic relations due to Kotlin serialization limitations related to polymorphic objects.
 * See more [here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 *
 * @author Gabriel Moraes
 */
@Context
@Requires(env = [ExecutionEnvironments.FACTORY])
@ExperimentalSerializationApi
internal class FactoryProtobufSerializationModuleConfiguration : ProtobufSerializationModuleConfiguration() {

    override fun configure(builderAction: SerializersModuleBuilder): SerializersModuleBuilder {
        return builderAction.apply {
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
