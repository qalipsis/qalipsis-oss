package io.evolue.plugins.netty.tcp

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.evolue.plugins.netty.tcp.spec.TcpClientStepSpecification

/**
 * [StepSpecificationConverter] from [TcpClientStepSpecification] to [TcpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class TcpClientStepSpecificationConverter(
    private val metricsRecorder: MetricsRecorder,
    private val eventsRecorder: EventsRecorder
) : StepSpecificationConverter<TcpClientStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return (stepSpecification is TcpClientStepSpecification).also {
            log.trace("Support of {} is {}", stepSpecification, it)
        }
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<TcpClientStepSpecification<*>>) {
        val spec = creationContext.stepSpecification as TcpClientStepSpecification<*>
        val step =
            TcpClientStep(spec.name ?: Cuid.createCuid(), spec.retryPolicy, spec.requestBlock,
                spec.connectionConfiguration, spec.metricsConfiguration, spec.eventsConfiguration, metricsRecorder,
                eventsRecorder)
        creationContext.createdStep(step)
        log.trace("Step {} created from {}", step, spec)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
