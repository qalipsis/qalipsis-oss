package io.evolue.plugins.netty.tcp

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.plugins.netty.tcp.spec.CloseTcpClientStepSpecification

/**
 * [StepSpecificationConverter] from [CloseTcpClientStepSpecification] to [CloseTcpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CloseTcpClientStepSpecificationConverter() :
    StepSpecificationConverter<CloseTcpClientStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return (stepSpecification is CloseTcpClientStepSpecification).also {
            log.trace("Support of {} is {}", stepSpecification, it)
        }
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<CloseTcpClientStepSpecification<*>>) {
        val spec = creationContext.stepSpecification as CloseTcpClientStepSpecification<I>
        // Validate that the referenced step exists and is a TCP step.
        val connectionOwner = when (val referencedStep = creationContext.directedAcyclicGraph.findStep(spec.stepName)) {
            is KeptAliveTcpClientStep -> {
                // We can chain the names from step to step.
                referencedStep.connectionOwner
            }
            is TcpClientStep -> {
                referencedStep
            }
            else -> {
                throw InvalidSpecificationException(
                    "Step with specified name ${spec.stepName} does not exist or is not a TCP step")
            }
        }

        log.trace("Found connection owner for spec {}: {}", spec, connectionOwner)
        connectionOwner.keepOpen()

        val step = CloseTcpClientStep<I>(spec.name ?: Cuid.createCuid(), connectionOwner)
        creationContext.createdStep(step)
        log.trace("Step {} created from {}", step, spec)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
