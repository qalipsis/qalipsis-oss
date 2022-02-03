package io.qalipsis.core.factory.orchestration

/**
 * Snapshot of the campaign execution state.
 *
 * @property campaignComplete [true] when the campaign execution is complete, [false] otherwise
 * @property scenarioComplete [true] when the scenario execution is complete, [false] otherwise
 * @property minionComplete [true] when the minion execution is complete, [false] otherwise
 *
 * @author Eric Jessé
 */
internal data class CampaignCompletionState(
    var minionComplete: Boolean = false,
    var scenarioComplete: Boolean = false,
    var campaignComplete: Boolean = false
)