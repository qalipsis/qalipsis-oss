package io.evolue.plugins.netty.tcp.spec

import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.steps.AbstractStepSpecification
import io.evolue.plugins.netty.NettyPluginSpecification
import io.evolue.plugins.netty.NettyScenarioSpecification
import io.evolue.plugins.netty.configuration.ExecutionEventsConfiguration
import io.evolue.plugins.netty.configuration.ExecutionMetricsConfiguration

/**
 * Specification for a [TcpClientStep].
 *
 * @author Eric Jessé
 */
data class TcpClientStepSpecification<INPUT> internal constructor(
    val configurationBlock: TcpClientStepSpecification<INPUT>.() -> Unit
) : AbstractStepSpecification<INPUT, Pair<INPUT, ByteArray>, TcpClientStepSpecification<INPUT>>(),
    NettyPluginSpecification<INPUT, Pair<INPUT, ByteArray>, TcpClientStepSpecification<INPUT>> {

    internal var requestBlock: suspend (input: INPUT) -> ByteArray = { ByteArray(0) }

    internal val connectionConfiguration =
        TcpConnectionConfiguration()

    internal val metricsConfiguration = TcpMetricsConfiguration()

    internal val eventsConfiguration = TcpEventsConfiguration()

    init {
        configurationBlock()
    }

    fun request(requestBlock: suspend (input: INPUT) -> ByteArray) {
        this.requestBlock = requestBlock
    }

    fun connect(configurationBlock: TcpConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    fun metrics(configurationBlock: TcpMetricsConfiguration.() -> Unit) {
        metricsConfiguration.configurationBlock()
    }

    fun events(configurationBlock: TcpEventsConfiguration.() -> Unit) {
        eventsConfiguration.configurationBlock()
    }
}

/**
 * Create a new TCP connection to send requests to a remote address.
 * It is not necessary to explicitly close the TCP connection after use if the workflow is straightforward.
 *
 * @see reuseTcp
 * @see closeTcp
 *
 * @author Eric Jessé
 */
fun <INPUT> NettyPluginSpecification<*, INPUT, *>.tcp(
    configurationBlock: TcpClientStepSpecification<INPUT>.() -> Unit): TcpClientStepSpecification<INPUT> {
    val step = TcpClientStepSpecification(configurationBlock)
    this.add(step)
    return step
}

/**
 * Create a new TCP connection to send requests to a remote address.
 * It is not necessary to explicitly close the TCP connection after use if the workflow is straightforward.
 *
 * @see reuseTcp
 * @see closeTcp
 *
 * @author Eric Jessé
 */
fun NettyScenarioSpecification.tcp(
    configurationBlock: TcpClientStepSpecification<Unit>.() -> Unit): TcpClientStepSpecification<Unit> {
    val step = TcpClientStepSpecification(configurationBlock)
    (this as MutableScenarioSpecification).add(step)
    return step
}

/**
 * Specification for a [io.evolue.plugins.netty.tcp.KeptAliveTcpClientStep].
 *
 * @author Eric Jessé
 */
data class KeptAliveTcpClientStepSpecification<INPUT>(
    val stepName: String,
    val configurationBlock: KeptAliveTcpClientStepSpecification<INPUT>.() -> Unit
) : AbstractStepSpecification<INPUT, Pair<INPUT, ByteArray>, KeptAliveTcpClientStepSpecification<INPUT>>(),
    NettyPluginSpecification<INPUT, Pair<INPUT, ByteArray>, KeptAliveTcpClientStepSpecification<INPUT>> {

    internal var requestBlock: suspend (input: INPUT) -> ByteArray = { ByteArray(0) }

    internal val optionsConfiguration =
        OptionsConfiguration()

    internal val metricsConfiguration =
        ExecutionMetricsConfiguration()

    internal val eventsConfiguration =
        ExecutionEventsConfiguration()

    init {
        configurationBlock()
    }

    fun request(requestBlock: suspend (input: INPUT) -> ByteArray) {
        this.requestBlock = requestBlock
    }

    fun options(configurationBlock: OptionsConfiguration.() -> Unit) {
        optionsConfiguration.configurationBlock()
    }

    fun metrics(configurationBlock: ExecutionMetricsConfiguration.() -> Unit) {
        metricsConfiguration.configurationBlock()
    }

    fun events(configurationBlock: ExecutionEventsConfiguration.() -> Unit) {
        eventsConfiguration.configurationBlock()
    }

    data class OptionsConfiguration(
        var closeOnFailure: Boolean = false,
        var closeAfterUse: Boolean = false
    )
}

/**
 * Keep a previously created TCP connection open and reuse it to perform new requests to the remote address.
 * It is not necessary to explicitly close the TCP connection after use if the workflow is straightforward.
 *
 * @param stepName name of the step where the TCP connection was open.
 *
 * @see tcp
 * @see closeTcp
 *
 * @author Eric Jessé
 */
fun <INPUT> NettyPluginSpecification<*, INPUT, *>.reuseTcp(
    stepName: String,
    configurationBlock: KeptAliveTcpClientStepSpecification<INPUT>.() -> Unit
): KeptAliveTcpClientStepSpecification<INPUT> {
    val step =
        KeptAliveTcpClientStepSpecification(stepName,
            configurationBlock)
    this.add(step)
    return step
}

data class CloseTcpClientStepSpecification<INPUT>(val stepName: String) :
    AbstractStepSpecification<INPUT, INPUT, CloseTcpClientStepSpecification<INPUT>>(),
    NettyPluginSpecification<INPUT, INPUT, CloseTcpClientStepSpecification<INPUT>>

/**
 * Keep a previously created TCP connection open until that point.
 *
 *@param stepName name of the step where the TCP connection was open.
 *
 * @author Eric Jessé
 */
fun <INPUT> NettyPluginSpecification<*, INPUT, *>.closeTcp(stepName: String): CloseTcpClientStepSpecification<INPUT> {
    val step = CloseTcpClientStepSpecification<INPUT>(stepName)
    this.add(step)
    return step
}