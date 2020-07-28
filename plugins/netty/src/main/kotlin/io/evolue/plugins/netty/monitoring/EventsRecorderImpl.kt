package io.evolue.plugins.netty.monitoring

import io.evolue.api.annotations.PluginComponent
import io.evolue.api.context.StepContext
import io.evolue.api.events.EventsLogger

@PluginComponent
class EventsRecorderImpl(
    private val eventsLogger: EventsLogger
) : EventsRecorder {

    override fun recordConnecting(stepContext: StepContext<*, *>) {
        eventsLogger.info("netty.connecting", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordSuccessfulConnection(stepContext: StepContext<*, *>) {
        eventsLogger.info("netty.connection-succeeded", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordFailedConnection(stepContext: StepContext<*, *>) {
        eventsLogger.info("netty.connection-failed", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordSending(stepContext: StepContext<*, *>) {
        eventsLogger.info("netty.sending-data", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordSent(stepContext: StepContext<*, *>) {
        eventsLogger.info("netty.data-sent", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordReceiving(stepContext: StepContext<*, *>) {
        eventsLogger.info("netty.receiving-data", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordReceived(stepContext: StepContext<*, *>) {
        eventsLogger.info("netty.data-received", tagsSupplier = { stepContext.toEventTags() })
    }
}