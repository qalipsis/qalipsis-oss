package io.evolue.plugins.netty.monitoring

import io.evolue.api.annotations.PluginComponent
import io.evolue.api.context.StepContext
import io.evolue.api.events.EventsLogger

@PluginComponent
class EventsRecorderImpl(
    private val eventsLogger: EventsLogger
) : EventsRecorder {

    override fun recordConnecting(stepContext: StepContext<*, *>) {
        eventsLogger.info("connecting", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordSuccessfulConnection(stepContext: StepContext<*, *>) {
        eventsLogger.info("connection-succeeded", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordFailedConnection(stepContext: StepContext<*, *>) {
        eventsLogger.info("connection-failed", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordSending(stepContext: StepContext<*, *>) {
        eventsLogger.info("sending-data", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordSent(stepContext: StepContext<*, *>) {
        eventsLogger.info("data-sent", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordReceiving(stepContext: StepContext<*, *>) {
        eventsLogger.info("receiving-data", tagsSupplier = { stepContext.toEventTags() })
    }

    override fun recordReceived(stepContext: StepContext<*, *>) {
        eventsLogger.info("data-received", tagsSupplier = { stepContext.toEventTags() })
    }
}