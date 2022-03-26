package io.qalipsis.core.factory.configuration

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.JsonSerializationModuleConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic

/**
 * Kotlin Json Serialization configuration.
 *
 * Creates a [Json] instance properly configured with serializer modules for subclasses of [io.qalipsis.core.factory.orchestration.TransportableContext].
 * This way Kotlin serialization can serialize and deserialize data using Interfaces or Parent classes and still keep reference to the original class.
 * It is needed to explicitly declare polymorphic relations due to Kotlin serialization limitations related to polymorphic objects.
 * See more [here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 *
 * @author Gabriel Moraes
 */
@Context
@Requires(env = [ExecutionEnvironments.FACTORY])
@ExperimentalSerializationApi
internal class FactoryJsonSerializationModuleConfiguration : JsonSerializationModuleConfiguration() {

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
