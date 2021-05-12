package io.qalipsis.api.context

import io.micrometer.core.instrument.Tags

interface MonitoringTags {

    /**
     * Converts the context to a map that can be used as tags for logged events.
     */
    fun toEventTags(): Map<String, String>

    /**
     * Converts the context to a map that can be used as tags for meters. The tags should not contain
     * any detail about the minion, but remains at the level of step, scenario and campaign.
     */
    fun toMetersTags(): Tags
}