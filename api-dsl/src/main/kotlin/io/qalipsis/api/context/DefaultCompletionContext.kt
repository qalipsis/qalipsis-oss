package io.qalipsis.api.context

import io.micrometer.core.instrument.Tags

/**
 * Default implementation of [CompletionContext].
 *
 * @author Eric Jessé
 */
data class DefaultCompletionContext(
    override val campaignId: CampaignId,
    override val scenarioId: ScenarioId,
    override val minionId: MinionId,
    override val lastExecutedStepId: StepId,
    override val errors: List<StepError>
) : CompletionContext {

    private var eventTags = mapOf(
        "campaign" to campaignId,
        "minion" to minionId,
        "scenario" to scenarioId,
        "last-executed-step" to lastExecutedStepId,
    )

    private var metersTag = Tags.of(
        "campaign", campaignId,
        "scenario", scenarioId
    )

    override fun toEventTags() = eventTags

    override fun toMetersTags() = metersTag
}