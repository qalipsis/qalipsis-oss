package io.evolue.plugins.netty.tcp

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.plugins.netty.configuration.ExecutionEventsConfiguration
import io.evolue.plugins.netty.configuration.ExecutionMetricsConfiguration
import io.evolue.plugins.netty.tcp.spec.KeptAliveTcpClientStepSpecification

/**
 * [StepSpecificationConverter] from [KeptAliveTcpClientStepSpecification] to [KeptAliveTcpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class KeptAliveTcpClientStepSpecificationConverter() :
    StepSpecificationConverter<KeptAliveTcpClientStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return (stepSpecification is KeptAliveTcpClientStepSpecification).also {
            log.trace("Support of {} is {}", stepSpecification, it)
        }
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<KeptAliveTcpClientStepSpecification<*>>) {
        val spec = creationContext.stepSpecification as KeptAliveTcpClientStepSpecification<I>
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
        val usages = spec.iterations.coerceAtLeast(1).toInt()
        connectionOwner.addUsage(requiresChannelActivityMonitoring(spec.metricsConfiguration, spec.eventsConfiguration),
            usages)
        log.trace("Adding {} usages to {}", usages, connectionOwner)

        val step =
            KeptAliveTcpClientStep(spec.name ?: Cuid.createCuid(), spec.retryPolicy, connectionOwner, spec.requestBlock,
                spec.optionsConfiguration, spec.metricsConfiguration, spec.eventsConfiguration)
        creationContext.createdStep(step)
        log.trace("Step {} created from {}", step, spec)
    }

    // Visible for test only.
    fun requiresChannelActivityMonitoring(metricsConfiguration: ExecutionMetricsConfiguration,
                                          eventsConfiguration: ExecutionEventsConfiguration): Boolean {
        return metricsConfiguration.dataReceived
                || metricsConfiguration.dataSent
                || metricsConfiguration.timeToLastByte
                || eventsConfiguration.receiving
                || eventsConfiguration.received
                || eventsConfiguration.sending
                || eventsConfiguration.sent
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
