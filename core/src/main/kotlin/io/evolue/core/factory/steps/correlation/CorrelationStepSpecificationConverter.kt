package io.evolue.core.factory.steps.correlation

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.context.CorrelationRecord
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.messaging.Topic
import io.evolue.api.messaging.broadcastTopic
import io.evolue.api.steps.CorrelationStepSpecification
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter

/**
 * [StepSpecificationConverter] from [CorrelationStepSpecification] to [CorrelationStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CorrelationStepSpecificationConverter : StepSpecificationConverter<CorrelationStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CorrelationStepSpecification<*, *>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<CorrelationStepSpecification<*, *>>) {
        val spec = creationContext.stepSpecification as CorrelationStepSpecification<I, O>
        if (!creationContext.scenarioSpecification.exists(spec.secondaryStepName)) {
            throw InvalidSpecificationException(
                    "The dependency step ${spec.secondaryStepName} of ${spec.name ?: "Unknown"} of type CorrelationStep does not exist in the scenario")
        }

        // The secondary step to correlate is decorated in order to transfer its output to the newly created step.
        val secondaryStep = creationContext.directedAcyclicGraph.scenario.findStep(spec.secondaryStepName)

        val topic = broadcastTopic<CorrelationRecord<O>>()

        val decoratedSecondaryStep = CorrelationOutputDecorator(secondaryStep as Step<Any, O>, topic)
        creationContext.directedAcyclicGraph.scenario.addStep(decoratedSecondaryStep)

        val step = CorrelationStep(spec.name ?: Cuid.createCuid(), spec.primaryKeyExtractor,
                listOf(SecondaryCorrelation(secondaryStep.id, topic as Topic<CorrelationRecord<Any>>,
                        spec.secondaryKeyExtractor)),
                spec.cacheTimeout)
        creationContext.createdStep(step)
    }

}
