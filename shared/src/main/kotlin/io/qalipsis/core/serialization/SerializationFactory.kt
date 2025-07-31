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

package io.qalipsis.core.serialization

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Secondary
import io.qalipsis.api.serialization.JsonSerializers
import io.qalipsis.api.serialization.ProtobufSerializers
import io.qalipsis.api.serialization.SerializersProvider
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.DescriptiveDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.directives.FactoryShutdownDirective
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
import io.qalipsis.core.executionprofile.AcceleratingExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ImmediateExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.PercentageStageExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ProgressiveVolumeExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.TimeFrameExecutionProfileConfiguration
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.CampaignScenarioShutdownFeedback
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.feedbacks.CampaignTimeoutFeedback
import io.qalipsis.core.feedbacks.CompleteMinionFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.FailedCampaignFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsShutdownFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.feedbacks.NodeExecutionFeedback
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
import jakarta.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.reflect.KClass

/**
 * Kotlin Protobuf Serialization configuration.
 *
 * Creates a serializer instances properly configured with modules for subclasses of [Feedback] and [Directive].
 *
 * This way Kotlin serialization can serialize and deserialize data using Interfaces or Parent classes and still keep reference to the original class.
 * It is needed to explicitly declare polymorphic relations due to Kotlin serialization limitations related to polymorphic objects.
 * See more [here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 *
 * @author Gabriel Moraes
 */
@Factory
@Requires(notEnv = [ExecutionEnvironments.STANDALONE])
@ExperimentalSerializationApi
class SerializationFactory {

    protected open val directiveClassesAndSerializers = mapOf<KClass<out Directive>, KSerializer<out Directive>>(
        FactoryAssignmentDirective::class to FactoryAssignmentDirective.serializer(),
        ScenarioWarmUpDirective::class to ScenarioWarmUpDirective.serializer(),
        CampaignShutdownDirective::class to CampaignShutdownDirective.serializer(),
        CampaignScenarioShutdownDirective::class to CampaignScenarioShutdownDirective.serializer(),
        CompleteCampaignDirective::class to CompleteCampaignDirective.serializer(),
        FactoryShutdownDirective::class to FactoryShutdownDirective.serializer(),
        CampaignAbortDirective::class to CampaignAbortDirective.serializer(),

        MinionsAssignmentDirective::class to MinionsAssignmentDirective.serializer(),
        MinionsDeclarationDirective::class to MinionsDeclarationDirective.serializer(),
        MinionsDeclarationDirectiveReference::class to MinionsDeclarationDirectiveReference.serializer(),
        MinionsStartDirective::class to MinionsStartDirective.serializer(),
        MinionsShutdownDirective::class to MinionsShutdownDirective.serializer(),

        MinionsRampUpPreparationDirective::class to MinionsRampUpPreparationDirective.serializer(),
        MinionsRampUpPreparationDirectiveReference::class to MinionsRampUpPreparationDirectiveReference.serializer(),
    )

    protected open val descriptiveDirectiveClassesAndSerializers =
        mapOf<KClass<out DescriptiveDirective>, KSerializer<out DescriptiveDirective>>(
            FactoryAssignmentDirective::class to FactoryAssignmentDirective.serializer(),
            ScenarioWarmUpDirective::class to ScenarioWarmUpDirective.serializer(),
            CampaignShutdownDirective::class to CampaignShutdownDirective.serializer(),
            CampaignScenarioShutdownDirective::class to CampaignScenarioShutdownDirective.serializer(),
            CompleteCampaignDirective::class to CompleteCampaignDirective.serializer(),
            FactoryShutdownDirective::class to FactoryShutdownDirective.serializer(),
            CampaignAbortDirective::class to CampaignAbortDirective.serializer(),

            MinionsAssignmentDirective::class to MinionsAssignmentDirective.serializer(),
            MinionsStartDirective::class to MinionsStartDirective.serializer(),
            MinionsShutdownDirective::class to MinionsShutdownDirective.serializer(),
        )

    protected open val singleUseDirectiveClassesAndSerializers =
        mapOf<KClass<out SingleUseDirective<*>>, KSerializer<out SingleUseDirective<*>>>(
            MinionsDeclarationDirective::class to MinionsDeclarationDirective.serializer(),
            MinionsRampUpPreparationDirective::class to MinionsRampUpPreparationDirective.serializer(),
        )

    protected open val singleUseDirectiveReferenceClassesAndSerializers =
        mapOf<KClass<out SingleUseDirectiveReference>, KSerializer<out SingleUseDirectiveReference>>(
            MinionsDeclarationDirectiveReference::class to MinionsDeclarationDirectiveReference.serializer(),
            MinionsRampUpPreparationDirectiveReference::class to MinionsRampUpPreparationDirectiveReference.serializer(),
        )

    protected open val serializedRecordClassesAndSerializers =
        mapOf<KClass<out SerializedRecord>, KSerializer<out SerializedRecord>>(
            BinarySerializedRecord::class to BinarySerializedRecord.serializer(),
        )

    protected open val feedbackClassesAndSerializers =
        mapOf<KClass<out Feedback>, KSerializer<out Feedback>>(
            CampaignStartedForDagFeedback::class to CampaignStartedForDagFeedback.serializer(),
            FactoryAssignmentFeedback::class to FactoryAssignmentFeedback.serializer(),
            ScenarioWarmUpFeedback::class to ScenarioWarmUpFeedback.serializer(),
            EndOfCampaignScenarioFeedback::class to EndOfCampaignScenarioFeedback.serializer(),
            EndOfCampaignFeedback::class to EndOfCampaignFeedback.serializer(),
            CampaignScenarioShutdownFeedback::class to CampaignScenarioShutdownFeedback.serializer(),
            CampaignShutdownFeedback::class to CampaignShutdownFeedback.serializer(),
            MinionsAssignmentFeedback::class to MinionsAssignmentFeedback.serializer(),
            MinionsStartFeedback::class to MinionsStartFeedback.serializer(),
            CompleteMinionFeedback::class to CompleteMinionFeedback.serializer(),
            MinionsShutdownFeedback::class to MinionsShutdownFeedback.serializer(),
            MinionsDeclarationFeedback::class to MinionsDeclarationFeedback.serializer(),
            MinionsRampUpPreparationFeedback::class to MinionsRampUpPreparationFeedback.serializer(),
            FailedCampaignFeedback::class to FailedCampaignFeedback.serializer(),
            NodeExecutionFeedback::class to NodeExecutionFeedback.serializer(),
            CampaignAbortFeedback::class to CampaignAbortFeedback.serializer(),
            CampaignTimeoutFeedback::class to CampaignTimeoutFeedback.serializer(),
        )

    protected open val executionProfileConfigurationClassesAndSerializers =
        mapOf<KClass<out ExecutionProfileConfiguration>, KSerializer<out ExecutionProfileConfiguration>>(
            AcceleratingExecutionProfileConfiguration::class to AcceleratingExecutionProfileConfiguration.serializer(),
            ImmediateExecutionProfileConfiguration::class to ImmediateExecutionProfileConfiguration.serializer(),
            PercentageStageExecutionProfileConfiguration::class to PercentageStageExecutionProfileConfiguration.serializer(),
            ProgressiveVolumeExecutionProfileConfiguration::class to ProgressiveVolumeExecutionProfileConfiguration.serializer(),
            RegularExecutionProfileConfiguration::class to RegularExecutionProfileConfiguration.serializer(),
            StageExecutionProfileConfiguration::class to StageExecutionProfileConfiguration.serializer(),
            TimeFrameExecutionProfileConfiguration::class to TimeFrameExecutionProfileConfiguration.serializer(),
            DefaultExecutionProfileConfiguration::class to DefaultExecutionProfileConfiguration.serializer(),
        )

    @Singleton
    @Secondary
    fun protobuf(configurers: Collection<SerializationConfigurer> = emptyList()): ProtoBuf {
        return ProtoBuf(from = ProtobufSerializers.protobuf) {
            serializersModule = SerializersModule {
                polymorphic(Directive::class) {
                    directiveClassesAndSerializers.forEach { (kClass, serializer) ->
                        subclass(kClass as KClass<Directive>, serializer as KSerializer<Directive>)
                    }
                }
                polymorphic(DescriptiveDirective::class) {
                    descriptiveDirectiveClassesAndSerializers.forEach { (kClass, serializer) ->
                        subclass(
                            kClass as KClass<DescriptiveDirective>,
                            serializer as KSerializer<DescriptiveDirective>
                        )
                    }
                }
                polymorphic(SingleUseDirective::class) {
                    singleUseDirectiveClassesAndSerializers.forEach { (kClass, serializer) ->
                        subclass(
                            kClass as KClass<SingleUseDirective<*>>,
                            serializer as KSerializer<SingleUseDirective<*>>
                        )
                    }
                }
                polymorphic(SingleUseDirectiveReference::class) {
                    singleUseDirectiveReferenceClassesAndSerializers.forEach { (kClass, serializer) ->
                        subclass(
                            kClass as KClass<SingleUseDirectiveReference>,
                            serializer as KSerializer<SingleUseDirectiveReference>
                        )
                    }
                }
                polymorphic(SerializedRecord::class) {
                    serializedRecordClassesAndSerializers.forEach { (kClass, serializer) ->
                        subclass(kClass as KClass<SerializedRecord>, serializer as KSerializer<SerializedRecord>)
                    }
                }
                polymorphic(Feedback::class) {
                    feedbackClassesAndSerializers.forEach { (kClass, serializer) ->
                        subclass(kClass as KClass<Feedback>, serializer as KSerializer<Feedback>)
                    }
                }
                polymorphic(ExecutionProfileConfiguration::class) {
                    executionProfileConfigurationClassesAndSerializers.forEach { (kClass, serializer) ->
                        subclass(
                            kClass as KClass<ExecutionProfileConfiguration>,
                            serializer as KSerializer<ExecutionProfileConfiguration>
                        )
                    }
                }
                configurers.forEach { it.configure(this) }
            }
        }
    }


    @ExperimentalSerializationApi
    @Singleton
    fun serializersProvider(protoBuf: ProtoBuf): SerializersProvider {
        return SerializersProvider(listOf(JsonSerializers.json, protoBuf))
    }

}
