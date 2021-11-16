package io.qalipsis.api.steps

import io.qalipsis.api.annotations.Spec

/**
 * Configuration of the metrics and events to record for a step.
 *
 * @property events when set to true, records the events, defaults to false.
 * @property meters when set to true, records meters, defaults to false.
 *
 * @author Eric Jessé
 */
@Spec
data class StepMonitoringConfiguration(
    var events: Boolean = false,
    var meters: Boolean = false
) {
    /**
     * Enables the record of both events and meters.
     */
    fun all() {
        events = true
        meters = true
    }
}
