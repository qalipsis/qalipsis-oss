package io.qalipsis.core.directives

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

object JsonTestSerializers {
    fun getJson() = Json {
        serializersModule = SerializersModule {
            polymorphic(DirectiveReference::class) {
                subclass(TestSingleUseDirectiveReference::class, TestSingleUseDirectiveReference.serializer())
            }

            polymorphic(SingleUseDirective::class) {
                subclass(TestSingleUseDirective::class, TestSingleUseDirective.serializer())
            }

            polymorphic(Directive::class) {
                subclass(TestDescriptiveDirective::class, TestDescriptiveDirective.serializer())
            }

        }
    }

}