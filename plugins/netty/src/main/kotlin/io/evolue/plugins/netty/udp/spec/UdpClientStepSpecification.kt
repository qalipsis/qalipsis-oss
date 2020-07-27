package io.evolue.plugins.netty.udp.spec

import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.steps.AbstractStepSpecification
import io.evolue.plugins.netty.NettyPluginSpecification
import io.evolue.plugins.netty.NettyScenarioSpecification
import io.evolue.plugins.netty.configuration.ConnectionConfiguration
import io.evolue.plugins.netty.configuration.EventsConfiguration
import io.evolue.plugins.netty.configuration.MetricsConfiguration

/**
 * Specification for a [UdpClientStep].
 *
 * @author Eric Jessé
 */
data class UdpClientStepSpecification<INPUT> internal constructor(
    val configurationBlock: UdpClientStepSpecification<INPUT>.() -> Unit
) : AbstractStepSpecification<INPUT, Pair<INPUT, ByteArray>, UdpClientStepSpecification<INPUT>>() {

    internal var requestBlock: suspend (input: INPUT) -> ByteArray = { ByteArray(0) }

    internal val connectionConfiguration = ConnectionConfiguration()

    internal val metricsConfiguration = MetricsConfiguration()

    internal val eventsConfiguration = EventsConfiguration()

    init {
        configurationBlock()
    }

    fun request(requestBlock: suspend (input: INPUT) -> ByteArray) {
        this.requestBlock = requestBlock
    }

    fun connect(configurationBlock: ConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    fun metrics(configurationBlock: MetricsConfiguration.() -> Unit) {
        metricsConfiguration.configurationBlock()
    }

    fun events(configurationBlock: EventsConfiguration.() -> Unit) {
        eventsConfiguration.configurationBlock()
    }
}


fun <INPUT> NettyPluginSpecification<*, INPUT>.udp(
    configurationBlock: UdpClientStepSpecification<INPUT>.() -> Unit): UdpClientStepSpecification<INPUT> {
    val step = UdpClientStepSpecification(configurationBlock)
    this.add(step)
    return step
}

fun NettyScenarioSpecification.udp(
    configurationBlock: UdpClientStepSpecification<Unit>.() -> Unit): UdpClientStepSpecification<Unit> {
    val step = UdpClientStepSpecification(configurationBlock)
    (this as MutableScenarioSpecification).add(step)
    return step
}
