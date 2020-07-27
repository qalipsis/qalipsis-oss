package io.evolue.plugins.netty.handlers

import io.evolue.api.context.StepContext
import io.evolue.plugins.netty.ClientExecutionContext
import io.evolue.plugins.netty.Pipeline
import io.evolue.plugins.netty.configuration.EventsConfiguration
import io.evolue.plugins.netty.configuration.MetricsConfiguration
import io.evolue.plugins.netty.handlers.monitoring.ChannelMonitoringHandler
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.netty.channel.ChannelInitializer
import java.util.concurrent.atomic.AtomicReference

/**
 * Channel initializer for any kind of Netty client.
 *
 * @param C channel type.
 *
 * @author Eric Jessé
 */
internal abstract class AbstractChannelInitializer<C : io.netty.channel.Channel>(
    private val metricsConfiguration: MetricsConfiguration,
    private val eventsConfiguration: EventsConfiguration,
    private val executionContext: AtomicReference<ClientExecutionContext>,
    private val metricsRecorder: MetricsRecorder,
    private val eventsRecorder: EventsRecorder
) : ChannelInitializer<C>() {

    override fun initChannel(channel: C) {
        val pipeline = channel.pipeline()
        configureRequestHandlers(channel)

        configureMonitoringHandlers(channel, executionContext.get().stepContext)

        // The handler is no longer required once everything was initialized.
        pipeline.remove(this)
    }

    protected open fun configureMonitoringHandlers(channel: C, stepContext: StepContext<*, *>) {
        if (requiresChannelMonitoring()) {
            channel.pipeline().addBefore(Pipeline.REQUEST_HANDLER, Pipeline.MONITORING_CHANNEL_HANDLER,
                ChannelMonitoringHandler(metricsRecorder, eventsRecorder, executionContext))
        }
    }

    protected abstract fun requiresChannelMonitoring(): Boolean

    protected abstract fun configureRequestHandlers(channel: C)
}
