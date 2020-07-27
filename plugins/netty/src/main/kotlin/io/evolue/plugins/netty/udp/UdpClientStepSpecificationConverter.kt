package io.evolue.plugins.netty.udp

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.evolue.plugins.netty.udp.spec.UdpClientStepSpecification

/**
 * [StepSpecificationConverter] from [UdpClientStepSpecification] to [UdpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class UdpClientStepSpecificationConverter(
    private val metricsRecorder: MetricsRecorder,
    private val eventsRecorder: EventsRecorder
) : StepSpecificationConverter<UdpClientStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is UdpClientStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<UdpClientStepSpecification<*>>) {
        val spec = creationContext.stepSpecification as UdpClientStepSpecification<*>
        val step = UdpClientStep(spec.name ?: Cuid.createCuid(), spec.retryPolicy, spec.requestBlock,
                spec.connectionConfiguration, spec.metricsConfiguration, spec.eventsConfiguration, metricsRecorder,
                eventsRecorder)
        creationContext.createdStep(step)
    }

}
