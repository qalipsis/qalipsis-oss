package io.qalipsis.core.configuration

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.orchestration.directives.DescriptiveDirective
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveReference
import io.qalipsis.api.orchestration.directives.ListDirective
import io.qalipsis.api.orchestration.directives.ListDirectiveReference
import io.qalipsis.api.orchestration.directives.QueueDirective
import io.qalipsis.api.orchestration.directives.QueueDirectiveReference
import io.qalipsis.api.orchestration.directives.SingleUseDirective
import io.qalipsis.api.orchestration.directives.SingleUseDirectiveReference
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackJsonModule
import io.qalipsis.api.serialization.Serializers
import io.qalipsis.core.directives.CampaignStartDirective
import io.qalipsis.core.directives.MinionsCreationDirective
import io.qalipsis.core.directives.MinionsCreationDirectiveReference
import io.qalipsis.core.directives.MinionsCreationPreparationDirective
import io.qalipsis.core.directives.MinionsCreationPreparationDirectiveReference
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.directives.MinionsStartDirectiveReference
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import jakarta.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic

/**
 * Kotlin Json Serialization configuration.
 *
 * Creates a [Json] instance properly configured with serializer modules for subclasses of [Feedback] and [Directive].
 * This way Kotlin serialization can serialize and deserialize data using Interfaces or Parent classes and still keep reference to the original class.
 * It is needed to explicitly declare polymorphic relations due to Kotlin serialization limitations related to polymorphic objects.
 * See more [here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 *
 * @author Gabriel Moraes
 */
@Factory
@Requires(notEnv = [ExecutionEnvironments.STANDALONE])
@ExperimentalSerializationApi
class JsonSerializationModuleConfiguration {

    @Singleton
    @Primary
    fun json() = Json(from = Serializers.json) {
        serializersModule = serializersModuleConfiguration + FeedbackJsonModule.serializersModule
    }

    private val serializersModuleConfiguration = SerializersModule {

        feedbacksSerializer(this)
        minionApiDirectives(this)
        minionHeadDelegationApiDirectives(this)

    }

    private fun feedbacksSerializer(builderAction: SerializersModuleBuilder): SerializersModuleBuilder {
        return builderAction.apply {
            polymorphic(Feedback::class) {
                subclass(CampaignStartedForDagFeedback::class, CampaignStartedForDagFeedback.serializer())
                subclass(EndOfCampaignFeedback::class, EndOfCampaignFeedback.serializer())
            }
        }
    }

    private fun minionApiDirectives(builderAction: SerializersModuleBuilder): SerializersModuleBuilder {
        return builderAction.apply {
            polymorphic(Directive::class) {
                subclass(MinionsCreationDirective::class, MinionsCreationDirective.serializer())
                subclass(MinionsCreationDirectiveReference::class, MinionsCreationDirectiveReference.serializer())
                subclass(MinionsStartDirective::class, MinionsStartDirective.serializer())
                subclass(MinionsStartDirectiveReference::class, MinionsStartDirectiveReference.serializer())
                subclass(CampaignStartDirective::class, CampaignStartDirective.serializer())
            }

            polymorphic(DescriptiveDirective::class) {
                subclass(CampaignStartDirective::class, CampaignStartDirective.serializer())
            }

            polymorphic(DirectiveReference::class) {
                subclass(MinionsStartDirectiveReference::class, MinionsStartDirectiveReference.serializer())
                subclass(MinionsCreationDirectiveReference::class, MinionsCreationDirectiveReference.serializer())
            }

            polymorphic(QueueDirective::class) {
                subclass(MinionsCreationDirective::class, MinionsCreationDirective.serializer())
            }

            polymorphic(QueueDirectiveReference::class) {
                subclass(MinionsCreationDirectiveReference::class, MinionsCreationDirectiveReference.serializer())
            }

            polymorphic(ListDirective::class) {
                subclass(MinionsStartDirective::class, MinionsStartDirective.serializer())
            }

            polymorphic(ListDirectiveReference::class) {
                subclass(MinionsStartDirectiveReference::class, MinionsStartDirectiveReference.serializer())
            }

            polymorphic(DescriptiveDirective::class) {
                subclass(CampaignStartDirective::class, CampaignStartDirective.serializer())
            }
        }
    }

    private fun minionHeadDelegationApiDirectives(builderAction: SerializersModuleBuilder): SerializersModuleBuilder {
        return builderAction.apply {

            polymorphic(Directive::class) {
                subclass(MinionsCreationPreparationDirective::class, MinionsCreationPreparationDirective.serializer())
                subclass(MinionsCreationPreparationDirectiveReference::class, MinionsCreationPreparationDirectiveReference.serializer())
                subclass(MinionsRampUpPreparationDirective::class, MinionsRampUpPreparationDirective.serializer())
            }

            polymorphic(DirectiveReference::class) {
                subclass(MinionsCreationPreparationDirectiveReference::class, MinionsCreationPreparationDirectiveReference.serializer())
            }

            polymorphic(SingleUseDirective::class) {
                subclass(MinionsCreationPreparationDirective::class, MinionsCreationPreparationDirective.serializer())
            }

            polymorphic(SingleUseDirectiveReference::class) {
                subclass(MinionsCreationPreparationDirectiveReference::class, MinionsCreationPreparationDirectiveReference.serializer())
            }

            polymorphic(DescriptiveDirective::class) {
                subclass(MinionsRampUpPreparationDirective::class, MinionsRampUpPreparationDirective.serializer())
            }
        }

    }
}
