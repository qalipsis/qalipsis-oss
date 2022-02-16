package io.qalipsis.core.configuration

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.serialization.Serializers
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.DescriptiveDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveReference
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.directives.MinionsDeclarationDirectiveReference
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.directives.MinionsRampUpPreparationDirectiveReference
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import io.qalipsis.core.directives.SingleUseDirective
import io.qalipsis.core.directives.SingleUseDirectiveReference
import io.qalipsis.core.feedbacks.CampaignScenarioShutdownFeedback
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.feedbacks.CompleteMinionFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.FailedCampaignFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackJsonModule
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsShutdownFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
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
@Context
@Requirements(
    Requires(notEnv = [ExecutionEnvironments.STANDALONE]),
    Requires(missingBeans = [JsonSerializationModuleConfiguration::class])
)
@ExperimentalSerializationApi
open class JsonSerializationModuleConfiguration {

    @Singleton
    @Primary
    fun json() = Json(from = Serializers.json) {
        serializersModule = serializersModuleConfiguration + FeedbackJsonModule.serializersModule
    }

    private val serializersModuleConfiguration = SerializersModule {
        feedbacksSerializer(this)
        factoryApiDirectives(this)
        minionApiDirectives(this)
        minionHeadDelegationApiDirectives(this)
        configure(this)
    }

    /**
     * Method to overwrite to add local types to the initialization.
     */
    protected open fun configure(builderAction: SerializersModuleBuilder): SerializersModuleBuilder =
        builderAction

    private fun feedbacksSerializer(builderAction: SerializersModuleBuilder): SerializersModuleBuilder {
        return builderAction.apply {
            polymorphic(Feedback::class) {
                subclass(CampaignStartedForDagFeedback::class, CampaignStartedForDagFeedback.serializer())
                subclass(FactoryAssignmentFeedback::class, FactoryAssignmentFeedback.serializer())
                subclass(ScenarioWarmUpFeedback::class, ScenarioWarmUpFeedback.serializer())
                subclass(EndOfCampaignScenarioFeedback::class, EndOfCampaignScenarioFeedback.serializer())
                subclass(EndOfCampaignFeedback::class, EndOfCampaignFeedback.serializer())
                subclass(CampaignScenarioShutdownFeedback::class, CampaignScenarioShutdownFeedback.serializer())
                subclass(CampaignShutdownFeedback::class, CampaignShutdownFeedback.serializer())
                subclass(MinionsAssignmentFeedback::class, MinionsAssignmentFeedback.serializer())
                subclass(MinionsStartFeedback::class, MinionsStartFeedback.serializer())
                subclass(CompleteMinionFeedback::class, CompleteMinionFeedback.serializer())
                subclass(MinionsShutdownFeedback::class, MinionsShutdownFeedback.serializer())
                subclass(MinionsDeclarationFeedback::class, MinionsDeclarationFeedback.serializer())
                subclass(MinionsRampUpPreparationFeedback::class, MinionsRampUpPreparationFeedback.serializer())
                subclass(FailedCampaignFeedback::class, FailedCampaignFeedback.serializer())
            }
        }
    }

    private fun factoryApiDirectives(builderAction: SerializersModuleBuilder): SerializersModuleBuilder {
        return builderAction.apply {
            polymorphic(Directive::class) {
                subclass(FactoryAssignmentDirective::class, FactoryAssignmentDirective.serializer())
                subclass(ScenarioWarmUpDirective::class, ScenarioWarmUpDirective.serializer())
                subclass(CampaignShutdownDirective::class, CampaignShutdownDirective.serializer())
                subclass(CampaignScenarioShutdownDirective::class, CampaignScenarioShutdownDirective.serializer())
            }

            polymorphic(DescriptiveDirective::class) {
                subclass(FactoryAssignmentDirective::class, FactoryAssignmentDirective.serializer())
                subclass(ScenarioWarmUpDirective::class, ScenarioWarmUpDirective.serializer())
                subclass(CampaignShutdownDirective::class, CampaignShutdownDirective.serializer())
                subclass(CampaignScenarioShutdownDirective::class, CampaignScenarioShutdownDirective.serializer())
                subclass(CompleteCampaignDirective::class, CompleteCampaignDirective.serializer())
            }
        }
    }

    private fun minionApiDirectives(builderAction: SerializersModuleBuilder): SerializersModuleBuilder {
        return builderAction.apply {
            polymorphic(Directive::class) {
                subclass(MinionsAssignmentDirective::class, MinionsAssignmentDirective.serializer())
                subclass(MinionsDeclarationDirective::class, MinionsDeclarationDirective.serializer())
                subclass(MinionsStartDirective::class, MinionsStartDirective.serializer())
                subclass(MinionsShutdownDirective::class, MinionsShutdownDirective.serializer())
            }

            polymorphic(DescriptiveDirective::class) {
                subclass(MinionsAssignmentDirective::class, MinionsAssignmentDirective.serializer())
                subclass(MinionsStartDirective::class, MinionsStartDirective.serializer())
                subclass(MinionsShutdownDirective::class, MinionsShutdownDirective.serializer())
            }

            polymorphic(SingleUseDirective::class) {
                subclass(MinionsDeclarationDirective::class, MinionsDeclarationDirective.serializer())
            }
        }
    }

    private fun minionHeadDelegationApiDirectives(builderAction: SerializersModuleBuilder): SerializersModuleBuilder {
        return builderAction.apply {

            polymorphic(Directive::class) {
                subclass(MinionsDeclarationDirective::class, MinionsDeclarationDirective.serializer())
                subclass(MinionsDeclarationDirectiveReference::class, MinionsDeclarationDirectiveReference.serializer())
                subclass(MinionsRampUpPreparationDirective::class, MinionsRampUpPreparationDirective.serializer())
                subclass(
                    MinionsRampUpPreparationDirectiveReference::class,
                    MinionsRampUpPreparationDirectiveReference.serializer()
                )
            }

            polymorphic(DirectiveReference::class) {
                subclass(MinionsDeclarationDirectiveReference::class, MinionsDeclarationDirectiveReference.serializer())
                subclass(
                    MinionsRampUpPreparationDirectiveReference::class,
                    MinionsRampUpPreparationDirectiveReference.serializer()
                )
            }

            polymorphic(SingleUseDirective::class) {
                subclass(MinionsDeclarationDirective::class, MinionsDeclarationDirective.serializer())
                subclass(MinionsRampUpPreparationDirective::class, MinionsRampUpPreparationDirective.serializer())
            }

            polymorphic(SingleUseDirectiveReference::class) {
                subclass(MinionsDeclarationDirectiveReference::class, MinionsDeclarationDirectiveReference.serializer())
                subclass(
                    MinionsRampUpPreparationDirectiveReference::class,
                    MinionsRampUpPreparationDirectiveReference.serializer()
                )
            }
        }

    }
}
