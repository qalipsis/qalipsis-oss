package io.qalipsis.core.directives

import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveReference
import io.qalipsis.api.orchestration.directives.ListDirective
import io.qalipsis.api.orchestration.directives.QueueDirective
import io.qalipsis.api.orchestration.directives.SingleUseDirective
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

object JsonTestSerializers {
    fun getJson() = Json {
        serializersModule = SerializersModule {
            polymorphic(QueueDirective::class) {
                subclass(TestQueueDirective::class, TestQueueDirective.serializer())
            }

            polymorphic(ListDirective::class) {
                subclass(TestListDirective::class, TestListDirective.serializer())
            }

            polymorphic(DirectiveReference::class){
                subclass(TestQueueDirectiveReference::class, TestQueueDirectiveReference.serializer() )
                subclass(TestListDirectiveReference::class, TestListDirectiveReference.serializer())
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